package org.fogbowcloud.manager.core.plugins.imagestorage.vmcatcher;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.imagestorage.fixed.StaticImageStoragePlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @see https://github.com/hepix-virtualisation/vmcatcher
 * @see https://github.com/EGI-FCTF/glancepush
 * @see https://github.com/grid-admin/vmcatcher_eventHndlExpl_ON
 */
public class VMCatcherStoragePlugin extends StaticImageStoragePlugin {

	private static final Logger LOGGER = Logger.getLogger(ImageStoragePlugin.class);
	
	protected static final String PROP_VMC_MAPPING_FILE = "image_storage_vmcatcher_glancepush_vmcmapping_file";
	protected static final String PROP_VMC_PUSH_METHOD = "image_storage_vmcatcher_push_method";
	protected static final String PROP_VMC_USE_SUDO = "image_storage_vmcatcher_use_sudo";
	protected static final String PROP_VMC_RUN_AS = "image_storage_vmcatcher_run_as";
	protected static final String PROP_VMC_ENV_PREFIX = "image_storage_vmcatcher_env_";
	
	private Properties props;
	private ComputePlugin computePlugin;
	private ExecutorService imageDownloader;
	private ShellWrapper shellWrapper;
	
	protected VMCatcherStoragePlugin(Properties properties, ComputePlugin computePlugin, 
			ExecutorService imageDownloader, ShellWrapper shellWrapper) {
		super(properties, computePlugin);
		this.props = properties;
		this.computePlugin = computePlugin;
		this.imageDownloader = imageDownloader;
		this.shellWrapper = shellWrapper;
	}
	
	public VMCatcherStoragePlugin(Properties properties, ComputePlugin computePlugin) {
		this(properties, computePlugin, Executors.newFixedThreadPool(5), 
				new ShellWrapper());
	}
	
	@Override
	public String getLocalId(Token token, String globalId) {
		String localId = super.getLocalId(token, globalId);
		if (localId != null) {
			return localId;
		}
		JSONObject imageInfo = null;
		try {
			imageInfo = retrieveImageListInfo(globalId);
		} catch (Exception e) {
			LOGGER.warn("Couldn't retrieve info for: " + globalId, e);
			return null;
		}
		
		localId = computePlugin.getImageId(token, globalId);
		if (localId != null) {
			return localId;
		}
		
		String imageTitleTranslated = null;
		final String pushMethod = this.props.getProperty(PROP_VMC_PUSH_METHOD);
		if (pushMethod.equals("glancepush")) {
			imageTitleTranslated = getImageNameWithGlancePush(imageInfo);
		} else if (pushMethod.equals("cesga")) {
			imageTitleTranslated = getImageNameWithCESGAPush(imageInfo);
		}
		if (imageTitleTranslated == null) {
			return null;
		}
		
		localId = computePlugin.getImageId(token, imageTitleTranslated);
		if (localId != null) {
			return localId;
		}
		
		try {
			executeShellCommand(sudo("vmcatcher_subscribe", 
					"--imagelist-newimage-subscribe", "--auto-endorse", "-s", globalId));
		} catch (Exception e) {
			LOGGER.warn("Couldn't add image.list to VMCatcher subscription list", e);
			return null;
		}
		
		imageDownloader.execute(new Runnable() {
			@Override
			public void run() {
				try {
					executeShellCommand(sudo("vmcatcher_subscribe", "-U"));
					executeShellCommand(sudo("vmcatcher_cache"));
					if (pushMethod.equals("glancepush")) {
						executeShellCommand(sudo("gpupdate"));
					} else if (pushMethod.equals("cesga")) {
						//TODO
					}
				} catch (Exception e) {
					LOGGER.warn("Couldn't cache image via VMCatcher", e);
				}
			}
		});
		
		return null;
	}

	private String[] sudo(String... cmds) {
		LinkedList<String> cmdList = new LinkedList<String>();
		String useSudoStr = props.getProperty(PROP_VMC_USE_SUDO);
		boolean useSudo = useSudoStr != null && Boolean.parseBoolean(useSudoStr);
		String runAs = props.getProperty(PROP_VMC_RUN_AS);		
		
		if (useSudo) {
			cmdList.add("sudo");
			cmdList.add("-E");
		}
		if (runAs != null) {
			cmdList.add("su");
			cmdList.add(runAs);
			cmdList.add("-c");
			cmdList.add(join(cmds));
		} else {
			for (String cmd : cmds) {
				cmdList.add(cmd);
			}			
		}
		return cmdList.toArray(new String[]{});
	}
	
	private static String join(String... cmds) {
		StringBuilder joinedStr = new StringBuilder();
		for (String cmd : cmds) {
			joinedStr.append(cmd);
			joinedStr.append(" ");
		}	
		return joinedStr.toString().trim();
	}
	
	private void executeShellCommand(String... command) throws IOException,
			InterruptedException {
		Map<String, String> envVars = new HashMap<String, String>();
		for (Object propKey : props.keySet()) {
			String propKeyStr = propKey.toString();
			if (propKeyStr.startsWith(PROP_VMC_ENV_PREFIX)) {
				envVars.put(propKeyStr.substring(PROP_VMC_ENV_PREFIX.length()), 
						props.getProperty(propKeyStr));
			}
		}
		shellWrapper.execute(envVars, command);
	}

	private String getImageNameWithCESGAPush(JSONObject imageInfo) {
		JSONObject imageListJson = imageInfo.optJSONObject("hv:imagelist");
		if (imageListJson == null) {
			return null;
		}
		JSONArray imagesArray = imageListJson.optJSONArray("hv:images");
		JSONObject imageJson = imagesArray.optJSONObject(0).optJSONObject("hv:image");
		return imageJson.optString("dc:identifier", null);
	}

	private String getImageNameWithGlancePush(JSONObject imageInfo) {
		String imageTitleTranslated = null;
		JSONObject imageListJson = imageInfo.optJSONObject("hv:imagelist");
		if (imageListJson == null) {
			return null;
		}
		JSONArray imagesArray = imageListJson.optJSONArray("hv:images");
		JSONObject imageJson = imagesArray.optJSONObject(0).optJSONObject("hv:image");
		String imageTitle = imageJson.optString("dc:title", null);
		if (imageTitle == null) {
			return null;
		}
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
		ContentType type = new ContentType(message.getHeader("Content-Type")[0]);
		String boundary = type.getParameter("boundary");
		String messageContent = IOUtils.toString(message.getInputStream());
		String[] splitMessageContent = messageContent.split("--" + boundary);
		JSONObject imageInfo = new JSONObject(splitMessageContent[1]);
		imageListStream.close();
		
		return imageInfo;
	}
	
}
