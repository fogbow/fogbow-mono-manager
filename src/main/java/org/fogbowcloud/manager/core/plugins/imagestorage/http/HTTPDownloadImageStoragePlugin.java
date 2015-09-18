package org.fogbowcloud.manager.core.plugins.imagestorage.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.imagestorage.fixed.StaticImageStoragePlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class HTTPDownloadImageStoragePlugin extends StaticImageStoragePlugin {

	private static final int IMAGE_UPLOAD_RETRY_INTERVAL = 15000;

	private static final Logger LOGGER = Logger.getLogger(HTTPDownloadImageStoragePlugin.class);
	private static final Executor IMAGE_DOWNLOADER = Executors.newFixedThreadPool(5);
	
	private ComputePlugin computePlugin;
	private Set<String> pendingImageUploads = Collections.newSetFromMap(
			new ConcurrentHashMap<String, Boolean>());
	
	private final String marketPlaceBaseURL;
	private final String keystorePath;
	private final String tmpStorage;
	private final String keystorePassword;
	private final String[] acceptedFormats;
	private final String conversionOutputFormat;
	
	public HTTPDownloadImageStoragePlugin(Properties properties, ComputePlugin computePlugin) {
		super(properties, computePlugin);
		this.computePlugin = computePlugin;
		this.marketPlaceBaseURL = properties.getProperty("image_storage_http_base_url");
		this.keystorePath = properties.getProperty("image_storage_http_keystore_path");
		this.keystorePassword = properties.getProperty("image_storage_http_keystore_password");
		this.tmpStorage = properties.getProperty("image_storage_http_tmp_storage");
		String acceptedFormatsValue = properties.getProperty("image_storage_http_accepted_formats", null);
		if (acceptedFormatsValue != null) {
			this.acceptedFormats = acceptedFormatsValue.split(",");
		} else {
			this.acceptedFormats = null;
		}
		this.conversionOutputFormat = properties.getProperty("image_storage_http_conversion_output_format", "raw");
	}
	
	@Override
	public String getLocalId(Token token, String globalId) {
		LOGGER.debug("Getting local id for globalImageId = " + globalId);
		// check if image was statically configured
		String localId = super.getLocalId(token, globalId);
		if (localId != null) {
			return localId;
		}
		
		// check if image exists in local cloud with this name 
		localId = computePlugin.getImageId(token, globalId);
		if (localId != null) {
			return localId;
		}
		
		if (marketPlaceBaseURL != null && !isAbsoluteURL(globalId)) {
			try {
				String normalizedImageName = normalizeImageName(removeHTTPPrefix(createURL(globalId)));
				LOGGER.debug("Getting local id for normalized image name = " + normalizedImageName);
				// check if image exists in local cloud with normalized name from marketplace				
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
				// check if image exists in local cloud with normalized name from URL
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
				try {
					doDownloadImage(token, imageURL);
				} finally {
					pendingImageUploads.remove(imageURL);
				}
			}
		});
	}
	
	private void doDownloadImage(final Token token, final String imageURL) {
		
		File downloadTempFile = downloadTempFile(imageURL);
		if (downloadTempFile == null) {
			LOGGER.debug("Download of image " + imageURL + " failed.");
			return;
		}
		LOGGER.debug("Download of image " + imageURL + " was done.");

		String imagePath = downloadTempFile.getAbsolutePath();
		String imageName = normalizeImageName(removeHTTPPrefix(imageURL));
		String imageExtension = getExtension(imageURL);
		
		LOGGER.debug("Image extension = " + imageExtension);
		
		String diskFormat = imageExtension.toLowerCase();
		File outputDir = new File(tmpStorage + "/" + UUID.randomUUID());
		List<File> imagesToDelete = new ArrayList<File>();
		imagesToDelete.add(downloadTempFile);

		if (imageExtension.equalsIgnoreCase(Extensions.ova.name())) {
			LOGGER.debug("Image is tar file");
			LOGGER.debug("Creating output directory = " + outputDir.getAbsolutePath());
			outputDir.mkdirs();
			try {
				List<File> files = unTar(downloadTempFile, outputDir);
				boolean foundValidImage = false;
				for (File file : files) {
					if (isOVAMainDisk(file.getName())) {
						String extension = getExtension(file.getName());
						if (isAnAcceptedFormat(extension)) {
							imagePath = file.getAbsolutePath();
							diskFormat = extension.toLowerCase();
						} else {
							imagePath = convertToValidFormat(token, imageURL, file);
							diskFormat = conversionOutputFormat;
						}
						foundValidImage = true;
						break;
					}
				}

				if (!foundValidImage) {
					LOGGER.error("Couldn't find valid disk image inside OVA.");
					removeImageFiles(imagesToDelete, outputDir);
					pendingImageUploads.remove(imageURL);
					return;
				}
			} catch (Throwable e) {
				LOGGER.error("Couldn't untar OVA image.", e);
				removeImageFiles(imagesToDelete, outputDir);
				pendingImageUploads.remove(imageURL);
				return;
			}
		} else if (!isAnAcceptedFormat(imageExtension)) {
			imagePath = convertToValidFormat(token, imageURL, downloadTempFile);
			imagesToDelete.add(new File(imagePath));
		} else if (imageExtension.equalsIgnoreCase(Extensions.img.name())) {
			LOGGER.debug("Image extension is IMG.");
			diskFormat = Extensions.qcow2.name();
		}
		try {
			computePlugin.uploadImage(token, imagePath, imageName, diskFormat);
			waitUploadAndDeleteFiles(token, imagesToDelete, imageName, outputDir);
		} catch (Throwable e) {
			LOGGER.error("Couldn't upload image.", e);
			removeImageFiles(imagesToDelete, outputDir);
		}
	}
	
	private boolean isAnAcceptedFormat(String extension) {
		if (acceptedFormats == null) {
			return true;
		}
		for (String accepted : acceptedFormats) {
			if (extension.equalsIgnoreCase(accepted)) {
				return true;
			}
		}
		return false;
	}

	private void removeImageFiles(List<File> imagesToDelete, File imageOutputDir) {
		for (File imageFile : imagesToDelete) {
			imageFile.delete();
		}
		try {
			FileUtils.deleteDirectory(imageOutputDir);
		} catch (IOException e) {
			LOGGER.error(
					"Error while removing directory " + imageOutputDir.getAbsolutePath(), e);
		}
	}

	private void waitUploadAndDeleteFiles(final Token token, List<File> imagesToDelete,
			String imageName, File outputDir) throws InterruptedException {
		while (true) {
			ImageState imageState = null;
			
			try {
				imageState = computePlugin.getImageState(token, imageName);
			} catch (Exception e) {
				LOGGER.error("Error while getting image state.", e);
			}
			
			if (imageState != null && imageState.in(ImageState.PENDING)) {
				Thread.sleep(IMAGE_UPLOAD_RETRY_INTERVAL);
			} else {
				removeImageFiles(imagesToDelete, outputDir);
				return;
			}
		}
	}

	private String convertToValidFormat(final Token token, final String imageURL, File file) {
		LOGGER.debug("Converting " + file.getName() + " to a valid format.");
		if (executeCommand("qemu-img", "info", file.getAbsolutePath()) != 0) { 
			LOGGER.warn("Couldn't convert image. qemu-img isn't installed.");
			return null;
		}
		String convertedDiskFileName = file.getAbsolutePath() + ".raw";
		int conversionResultCode = executeCommand("qemu-img", "convert", "-O", 
				HTTPDownloadImageStoragePlugin.this.conversionOutputFormat,
				file.getAbsolutePath(), convertedDiskFileName);
		if (conversionResultCode != 0) {
			LOGGER.warn("Couldn't convert image. qemu-img conversion result code: "
					+ conversionResultCode);
			return null;
		}				
		return convertedDiskFileName;
		
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

	private boolean isOVAMainDisk(String fileName) {
		String extension = getExtension(fileName);
		return (extension.equalsIgnoreCase(Extensions.vmdk.name()) || extension.equalsIgnoreCase(Extensions.vdi.name())
				|| extension.equalsIgnoreCase(Extensions.img.name()));
	}
	
	private String normalizeImageName(final String imageURL) {
		StringBuilder strBuilder = new StringBuilder();
		for (Character c : imageURL.toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				strBuilder.append(c);
			}
		}
		String normalizedName = strBuilder.toString();
		int maxLength = 50;
		if (normalizedName.length() > maxLength) {
			normalizedName = normalizedName
					.substring(normalizedName.length() - maxLength);
		}
		return normalizedName;
	}
	
	private File downloadTempFile(final String imageURL) {
		LOGGER.debug("Downloading image " + imageURL);
		
		HttpClient httpclient = null;
		HttpEntity entity = null;
		InputStream instream = null;
		File tempFile = null;
		try {
			httpclient = keystorePath == null ? HttpClients.createMinimal() : createSSLClient();
			HttpGet httpget = new HttpGet(imageURL);
			HttpResponse response = httpclient.execute(httpget);
			
			if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
				return null;
			}
			
			entity = response.getEntity();
			if (entity != null) {
				tempFile = getTemFile(imageURL);
				instream = entity.getContent();
				FileUtils.copyInputStreamToFile(instream, tempFile);;
				instream.close();
				return tempFile;
			}
		} catch (Exception e) {
			LOGGER.error("Couldn't download image file", e);
			// checking if file exists
			if (tempFile != null && tempFile.exists()) {
				tempFile.delete();
			}			
		} finally {
			if (entity != null && instream != null) {
				try {
					instream.close();
				} catch (IOException e) {
					// Ignore this
				}
			}
		}
		return null;
	}

	private File getTemFile(final String imageURL) throws IOException {
		String extension = getExtension(imageURL);
		String imageName = removeHTTPPrefix(imageURL.substring(0, imageURL.lastIndexOf(".")))
				.replaceAll("/", ".");
		File tempFile = File.createTempFile(imageName, 
				"." + extension, new File(tmpStorage));
		return tempFile;
	}

	private String getExtension(final String imageName) {
		LOGGER.debug("Getting extension of name " + imageName);
		String extension = imageName.substring(imageName.lastIndexOf(".") + 1);
		return extension;
	}

	protected String removeHTTPPrefix(String imageURL) {
		String protocol = "http";
		try {
			URL url = new URL(imageURL);
			protocol = url.getProtocol().toLowerCase();
		} catch (MalformedURLException e) {
			//ignore
		}
		return imageURL.replaceFirst(protocol + "://", "");
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

	private HttpClient createSSLClient() throws Exception {
		KeyStore trustStore  = KeyStore.getInstance(KeyStore.getDefaultType());
        FileInputStream instream = new FileInputStream(new File(keystorePath));
        try {
            trustStore.load(instream, keystorePassword.toCharArray());
        } finally {
            instream.close();
        }
        SSLContext sslcontext = SSLContexts.custom()
                .loadTrustMaterial(trustStore, new TrustSelfSignedStrategy())
                .build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                sslcontext);
        return HttpClients.custom().setSSLSocketFactory(sslsf).build();
	}

	protected String createURL(String globalId) {
		return marketPlaceBaseURL + "/" + globalId;
	}
}
