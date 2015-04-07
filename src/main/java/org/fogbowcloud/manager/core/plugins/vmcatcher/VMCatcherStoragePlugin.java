package org.fogbowcloud.manager.core.plugins.vmcatcher;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.occi.core.Token;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @see https://github.com/hepix-virtualisation/vmcatcher
 * @see https://github.com/EGI-FCTF/glancepush
 * @see https://github.com/grid-admin/vmcatcher_eventHndlExpl_ON
 */
public class VMCatcherStoragePlugin implements ImageStoragePlugin {

	private static final Logger LOGGER = Logger.getLogger(ImageStoragePlugin.class);
	private static final Executor IMAGE_DOWNLOADER = Executors.newFixedThreadPool(5);
	
	private static final String PROP_VMC_MAPPING_FILE = "vmcatcher_glancepush_vmcmapping_file";
	private static final String PROP_VMC_PUSH_METHOD = "vmcatcher_push_method";
	
	private Properties props;
	private ComputePlugin computePlugin;
	
	public VMCatcherStoragePlugin(Properties props, ComputePlugin computePlugin) {
		this.props = props;
		this.computePlugin = computePlugin;
	}
	
	@Override
	public String getLocalId(Token token, String globalId) {
		JSONObject imageInfo = null;
		
		try {
			imageInfo = retrieveImageListInfo(globalId);
		} catch (Exception e) {
			LOGGER.warn("Couldn't retrieve info for: " + globalId, e);
			return null;
		}
		
		String imageTitleTranslated = null;
		String pushMethod = this.props.getProperty(PROP_VMC_PUSH_METHOD);
		if (pushMethod.equals("glancepush")) {
			imageTitleTranslated = getImageNameWithGlancePush(imageInfo);
		} else if (pushMethod.equals("cesga")) {
			imageTitleTranslated = getImageNameWithCESGAPush(imageInfo);
		}
		
		String localId = imageTitleTranslated == null ? null : computePlugin
				.getImageId(token, imageTitleTranslated);
		if (localId != null) {
			return localId;
		}
		
		try {
			executeShellCommand("sudo", "vmcatcher_subscribe", 
					"--imagelist-newimage-subscribe", "--auto-endorse", "-s", globalId);
		} catch (Exception e) {
			LOGGER.warn("Couldn't add image.list to VMCatcher subscription list", e);
		}
		
		IMAGE_DOWNLOADER.execute(new Runnable() {
			
			@Override
			public void run() {
				try {
					executeShellCommand("sudo", "vmcatcher_subscribe", "-U");
					executeShellCommand("sudo", "vmcatcher_cache");
				} catch (Exception e) {
					LOGGER.warn("Couldn't cache image via VMCatcher", e);
				}
			}
		});
		
		return null;
	}

	private void executeShellCommand(String... command) throws IOException,
			InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(command);
		Process process = builder.start();
		
		int exitValue = process.waitFor();
		String stdout = IOUtils.toString(process.getInputStream());
		String stderr = IOUtils.toString(process.getErrorStream());
		
		LOGGER.debug("Command " + Arrays.asList(command) + " has finished. "
				+ "Exit: " + exitValue + "; Stdout: " + stdout + "; Stderr: " + stderr);
	}

	private String getImageNameWithCESGAPush(JSONObject imageInfo) {
		return imageInfo.optJSONObject("hv:imagelist").optString("dc:identifier");
	}

	private String getImageNameWithGlancePush(JSONObject imageInfo) {
		String imageTitleTranslated = null;
		String imageTitle = imageInfo.optJSONObject("hv:imagelist").optString("dc:title");
		if (this.props.containsKey(PROP_VMC_MAPPING_FILE)) {
			try {
				JSONObject vmcMapping = new JSONObject(IOUtils.toString(
						new FileInputStream(this.props.getProperty(PROP_VMC_MAPPING_FILE))));
				imageTitleTranslated = vmcMapping.optString(imageTitle);
			} catch (Exception e) {
				LOGGER.warn("Couldn't parse VMC mapping file", e);
			}
		}
		if (imageTitleTranslated == null) {
			imageTitleTranslated = imageTitle.replace(" ", "_").replace("/", "_");
		}
		return imageTitleTranslated;
	}

	private JSONObject retrieveImageListInfo(String globalId)
			throws MalformedURLException, IOException, MessagingException,
			JSONException {
		
		URL imageListURL = new URL(globalId);
		InputStream imageListStream = imageListURL.openStream();
		MimeMessage message = new MimeMessage(
				Session.getDefaultInstance(new Properties()), imageListStream);
		MimeMultipart mime = (MimeMultipart) message.getContent();
		JSONObject imageInfo = new JSONObject(IOUtils.toString(
				mime.getBodyPart(0).getInputStream()));
		imageListStream.close();
		
		return imageInfo;
	}

}
