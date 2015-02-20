package org.fogbowcloud.manager.core.plugins.appliance;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.Token;

public class EgiApplianceImageStoragePlugin implements ImageStoragePlugin {

	private static final String PROP_STATIC_IMAGE_PREFIX = "image_storage_static_";
	
	private static final Logger LOGGER = Logger.getLogger(EgiApplianceImageStoragePlugin.class);
	private static final Executor IMAGE_DOWNLOADER = Executors.newFixedThreadPool(5);
	
	private ComputePlugin computePlugin;
	private Set<String> pendingImageUploads = Collections.newSetFromMap(
			new ConcurrentHashMap<String, Boolean>());
	private Map<String, String> globalToLocalIds = new HashMap<String, String>();
	
	private final String marketPlaceBaseURL;
	private final String keystorePath;
	private final String tmpStorage;
	private final String keystorePassword;
	
	public EgiApplianceImageStoragePlugin(Properties properties, ComputePlugin computePlugin) {
		this.computePlugin = computePlugin;
		this.marketPlaceBaseURL = properties.getProperty("image_storage_appliance_base_url");
		this.keystorePath = properties.getProperty("image_storage_applicance_keystore_path");
		this.keystorePassword = properties.getProperty("image_storage_appliance_keystore_password");
		this.tmpStorage = properties.getProperty("image_storage_appliance_tmp_storage");
		fillStaticStorage(properties);
	}
	
	private void fillStaticStorage(Properties properties) {
		LOGGER.info("Filling static storage...");
		for (Object propName : properties.keySet()) {
			String propNameStr = (String) propName;
			if (propNameStr.startsWith(PROP_STATIC_IMAGE_PREFIX)) {
				String globalImageId = propNameStr.substring(PROP_STATIC_IMAGE_PREFIX.length());
				LOGGER.debug("Global image id = " + globalImageId);
				globalToLocalIds.put(globalImageId, properties.getProperty(propNameStr));
				ResourceRepository.getInstance().addImageResource(globalImageId);
			}
		}
	}
	
	@Override
	public String getLocalId(Token token, String globalId) {
		LOGGER.debug("Getting local id for globalImageId = " + globalId);
		//TODO what to do with marketplace?		
		String localId = globalToLocalIds.get(globalId);
		if (localId != null) {
			return localId;
		}
		
		if (marketPlaceBaseURL != null) {
			try {
				localId = computePlugin.getImageId(token, normalizeImageName(createURL(globalId)));
			} catch (Throwable e) {
				LOGGER.error("Couldn't get local image id from Compute plugin.", e);
			}
			if (localId != null) {
				return localId;
			}
			scheduleImageDownload(token, createURL(globalId));
		} else {
			try {
				localId = computePlugin.getImageId(token, normalizeImageName(globalId));
			} catch (Throwable e) {
				LOGGER.error("Couldn't get local image id from Compute plugin.", e);
			}
			if (localId != null) {
				return localId;
			}
			scheduleImageDownload(token, globalId);
		}
		return null;
	}

	private void scheduleImageDownload(final Token token, final String imageURL) {
		if (pendingImageUploads.contains(imageURL)) {
			LOGGER.debug("Download of image " + imageURL + " is been done.");
			return;
		}
		pendingImageUploads.add(imageURL);
		IMAGE_DOWNLOADER.execute(new Runnable() {
			@Override
			public void run() {
				File downloadTempFile = downloadTempFile(imageURL);
				LOGGER.debug("Download of image " + imageURL + " was done.");
				if (downloadTempFile != null) {
					try {
						computePlugin.uploadImage(token, 
								downloadTempFile.getAbsolutePath(), 
								normalizeImageName(imageURL));
					} catch (Throwable e) {
						LOGGER.error("Couldn't upload image.", e);
					}
				}
				pendingImageUploads.remove(imageURL);
			}
		});
	}
	
	private String normalizeImageName(final String imageURL) {
		return imageURL.replaceAll("/", ".").replaceAll(":", ".");
	}
	
	private File downloadTempFile(final String imageURL) {
		LOGGER.debug("Downloading image " + imageURL);
		
		HttpClient httpclient = null;
		HttpEntity entity = null;
		try {
			httpclient = new DefaultHttpClient();
			injectKeystore(httpclient);
			HttpGet httpget = new HttpGet(imageURL);
			HttpResponse response = httpclient.execute(httpget);
			entity = response.getEntity();
			if (entity != null) {				
				String extension = imageURL.substring(imageURL.lastIndexOf("."));
				String imageName = imageURL.substring(0, imageURL.lastIndexOf(".")).replaceAll("/", ".");
				File tempFile = File.createTempFile(imageName, 
						extension, new File(tmpStorage));

				InputStream instream = entity.getContent();
				FileUtils.copyInputStreamToFile(instream, tempFile);;
				instream.close();
				return tempFile;
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't download image file", e);
		} finally {
			if (entity != null) {
				try {
					entity.consumeContent();
				} catch (IOException e) {
					// Ignore this
				}
			}
			if (httpclient != null) {
				httpclient.getConnectionManager().shutdown();
			}
		}
		return null;
	}

	private void injectKeystore(HttpClient httpclient)
			throws Exception {
		if (keystorePath != null) {
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(new FileInputStream(keystorePath), keystorePassword.toCharArray());
			SSLSocketFactory socketFactory = new SSLSocketFactory(ks);
			Scheme sch = new Scheme("https", socketFactory, 443);
			httpclient.getConnectionManager().getSchemeRegistry().register(sch);
		}
	}

	protected String createURL(String globalId) {
		return marketPlaceBaseURL + "/" + globalId;
	}
}
