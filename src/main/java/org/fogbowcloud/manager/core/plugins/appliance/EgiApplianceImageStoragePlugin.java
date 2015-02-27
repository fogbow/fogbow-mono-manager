package org.fogbowcloud.manager.core.plugins.appliance;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
		String localId = globalToLocalIds.get(globalId);
		if (localId != null) {
			return localId;
		}
		
		if (marketPlaceBaseURL != null && !isAbsoluteURL(globalId)) {
			try {
				String normalizedImageName = normalizeImageName(removeHTTPPrefix(createURL(globalId)));
				LOGGER.debug("Getting local id for normalized image name = " + normalizedImageName);
				localId = computePlugin.getImageId(token,
						normalizedImageName);
			} catch (Throwable e) {
				LOGGER.error("Couldn't get local image id from Compute plugin.", e);
			}
			if (localId != null) {
				return localId;
			}
			scheduleImageDownload(token, createURL(globalId));
		} else {
			try {
				String normalizedImageName = normalizeImageName(removeHTTPPrefix(globalId));
				LOGGER.debug("Getting local id for normalized image name = " + normalizedImageName);
				localId = computePlugin.getImageId(token,
						normalizedImageName);
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

	private boolean isAbsoluteURL(String uriStr) {
		try {
			return new URI(uriStr).isAbsolute();
		} catch (URISyntaxException e) { }
		return false;
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
					String imageExtension = getExtension(imageURL);
					LOGGER.debug("Image extension = " + imageExtension);
					if (imageExtension.equalsIgnoreCase(OVA)) {
						LOGGER.debug("Image is tar file");	
						File outputDir = new File(tmpStorage + "/" + UUID.randomUUID());
						LOGGER.debug("Creating output directory = " + outputDir.getAbsolutePath());
						outputDir.mkdirs();
						try {
							List<File> files = unTar(downloadTempFile, outputDir);
							for (File file : files) {
								String diskFormat = "disk1." + getExtension(file.getAbsolutePath());
								if (isValidDiskForConversion(diskFormat)) {
									LOGGER.debug("Disk format into tar file = " + diskFormat);
									if (executeCommand("qemu-img", "info", file.getAbsolutePath()) == 0) {
										String convertedDiskFileName = file.getAbsolutePath()
												+ ".qcow2";
										int conversionResultCode = executeCommand("qemu-img",
												"convert", "-O", "qcow2", file.getAbsolutePath(),
												convertedDiskFileName);
										if (conversionResultCode == 0) {
											try {
												computePlugin
														.uploadImage(
																token,
																convertedDiskFileName,
																normalizeImageName(removeHTTPPrefix(imageURL)),
																QCOW2);
											} catch (Throwable e) {
												LOGGER.error("Couldn't upload image.", e);
											}
										} else {
											LOGGER.warn("Couldn't convert image. qemu-img conversion result code: "
													+ conversionResultCode);
										}
									} else {
										LOGGER.warn("Couldn't convert image. qemu-img isn't installed.");
									}
									break;
								}
							}
						} catch (Throwable e) {
							LOGGER.error("Couldn't untar OVA image.", e);
						} 
						return;
					} 
					
					
					String diskFormat = null;
					if (imageExtension.equalsIgnoreCase(QCOW2) || imageExtension.equalsIgnoreCase(IMG)) {
						LOGGER.debug("Image extension is QCOW2 or IMG.");
						diskFormat = QCOW2;
					} else if (imageExtension.equalsIgnoreCase(VMDK) || imageExtension.equalsIgnoreCase(VDI)
							|| imageExtension.equalsIgnoreCase(ISO) || imageExtension.equalsIgnoreCase(RAW)
							|| imageExtension.equalsIgnoreCase(VHD)) {
						diskFormat = imageExtension.toLowerCase();
					} 
					try {
						computePlugin.uploadImage(token, 
								downloadTempFile.getAbsolutePath(), 
								normalizeImageName(removeHTTPPrefix(imageURL)), diskFormat);
					} catch (Throwable e) {
						LOGGER.error("Couldn't upload image.", e);
					}
				}
				pendingImageUploads.remove(imageURL);
			}

			private int executeCommand(String... cmd) {
				ProcessBuilder processBuilder = new ProcessBuilder(cmd);
				try {
					Process process = processBuilder.start();
					int resultCode = process.waitFor();
					if (resultCode != 0) {
						LOGGER.error("Process error stream: "
								+ IOUtils.toString(process.getErrorStream()));
					}
					return resultCode;
				} catch (Exception e) {
					LOGGER.error("Error while executing command.", e);
				}
				return 1;
			}

			private boolean isValidDiskForConversion(String extension) {
				return (extension.equalsIgnoreCase("disk1." + VMDK) || extension.equalsIgnoreCase("disk1." + VDI)
						|| extension.equalsIgnoreCase("disk1." + IMG));
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
				String extension = getExtension(imageURL);
				String imageName = removeHTTPPrefix(imageURL.substring(0, imageURL.lastIndexOf(".")))
						.replaceAll("/", ".");
				File tempFile = File.createTempFile(imageName, 
						"." + extension, new File(tmpStorage));

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

	private String getExtension(final String imageName) {
		LOGGER.debug("Getting extension of name " + imageName);
		return imageName.substring(imageName.lastIndexOf(".") + 1);
	}

	private String removeHTTPPrefix(String imageURL) {
		return imageURL.replaceFirst("http://", "");
	}
	
	private static List<File> unTar(final File inputFile, final File outputDir) throws FileNotFoundException, IOException, ArchiveException {

	    LOGGER.debug(String.format("Untaring %s to dir %s.", inputFile.getAbsolutePath(), outputDir.getAbsolutePath()));

	    final List<File> untaredFiles = new LinkedList<File>();
	    final InputStream is = new FileInputStream(inputFile); 
	    final TarArchiveInputStream debInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
	    TarArchiveEntry entry = null; 
	    while ((entry = (TarArchiveEntry)debInputStream.getNextEntry()) != null) {
	        final File outputFile = new File(outputDir, entry.getName());
	        if (entry.isDirectory()) {
	        	LOGGER.debug(String.format("Attempting to write output directory %s.", outputFile.getAbsolutePath()));
	            if (!outputFile.exists()) {
	            	LOGGER.debug(String.format("Attempting to create output directory %s.", outputFile.getAbsolutePath()));
	                if (!outputFile.mkdirs()) {
	                    throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
	                }
	            }
	        } else {
	        	LOGGER.debug(String.format("Creating output file %s.", outputFile.getAbsolutePath()));
	            final OutputStream outputFileStream = new FileOutputStream(outputFile); 
	            IOUtils.copy(debInputStream, outputFileStream);
	            outputFileStream.close();
	        }
	        untaredFiles.add(outputFile);
	    }
	    debInputStream.close(); 

	    return untaredFiles;
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
