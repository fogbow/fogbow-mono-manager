package org.fogbowcloud.manager.core.plugins.vmcatcher;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

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

public class VMCatcherStoragePlugin implements ImageStoragePlugin {

	private static final Logger LOGGER = Logger.getLogger(ImageStoragePlugin.class); 
	
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
			ProcessBuilder builder = new ProcessBuilder("sudo", "vmcatcher_subscribe", 
					"--imagelist-newimage-subscribe", "--auto-endorse", "-s", globalId);
			Process process = builder.start();
			
			int exitValue = process.waitFor();
			String stdout = IOUtils.toString(process.getInputStream());
			String stderr = IOUtils.toString(process.getErrorStream());
			
			LOGGER.debug("Command vmcatcher_subscribe has finished. "
					+ "Exit: " + exitValue + "; Stdout: " + stdout + "; Stderr: " + stderr);
		} catch (Exception e) {
			LOGGER.warn("Couldn't add image.list to VMCatcher subscription list", e);
		}
		
		// TODO Should it force vmcatcher_cache? 
		// vmcatcher_subscribe -U
		// vmcatcher_cache
		
		return null;
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
