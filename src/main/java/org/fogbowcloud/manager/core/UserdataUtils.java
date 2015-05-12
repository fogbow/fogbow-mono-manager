package org.fogbowcloud.manager.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.fogbowcloud.manager.core.plugins.util.CloudInitUserDataBuilder;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;

public class UserdataUtils {

	protected static final String TOKEN_ID_STR = "#TOKEN_ID#";
	protected static final String TOKEN_HOST_STR = "#TOKEN_HOST#";
	protected static final String TOKEN_HOST_HTTP_PORT_STR = "#TOKEN_HOST_HTTP_PORT#";
	protected static final String TOKEN_HOST_SSH_PORT_STR = "#TOKEN_HOST_SSH_PORT#";
	protected static final String TOKEN_MANAGER_SSH_PUBLIC_KEY = "#TOKEN_MANAGER_SSH_PUBLIC_KEY#";
	protected static final String TOKEN_MANAGER_SSH_USER = "#TOKEN_MANAGER_SSH_USER#";
	
	private static final String DEFAULT_SSH_HOST_PORT = "22";
	
	private static String getManagerSSHPublicKeyFilePath(Properties properties) {
		String publicKeyFilePath = properties.getProperty(ConfigurationConstants.SSH_PUBLIC_KEY_PATH);
		if (publicKeyFilePath == null || publicKeyFilePath.isEmpty()) {
			return null;
		}
		return publicKeyFilePath;
	}
	
	private static String getSSHCommonUser(Properties properties) {
		String sshCommonUser = properties.getProperty(ConfigurationConstants.SSH_COMMON_USER);
		return sshCommonUser == null ? ManagerController.DEFAULT_COMMON_SSH_USER : sshCommonUser;
	}
	
	public static String createBase64Command(Request request, Properties properties) throws IOException, MessagingException {
		String tokenId = request.getId();
		String sshPrivateHostIP = properties.getProperty(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY);
		String sshRemoteHostPort = properties.getProperty(ConfigurationConstants.TOKEN_HOST_PORT_KEY);
		String sshRemoteHostHttpPort = properties.getProperty(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY);
		String managerPublicKeyFilePath = getManagerSSHPublicKeyFilePath(properties);
		String userPublicKey = request.getAttValue(RequestAttribute.DATA_PUBLIC_KEY.getValue());
		String sshCommonUser = getSSHCommonUser(properties);
		
		String sshTunnelCmdFilePath = "bin/fogbow-create-reverse-tunnel";
		String cloudConfigFilePath = "bin/fogbow-cloud-config.cfg";

		if (sshRemoteHostPort == null || sshRemoteHostPort.isEmpty()) {
			sshRemoteHostPort = DEFAULT_SSH_HOST_PORT;
		}
		
		CloudInitUserDataBuilder cloudInitUserDataBuilder = CloudInitUserDataBuilder.start();
		cloudInitUserDataBuilder.addShellScript(new FileReader(sshTunnelCmdFilePath));
		
		if (managerPublicKeyFilePath != null || userPublicKey != null) {
			cloudInitUserDataBuilder.addCloudConfig(new FileReader(new File(cloudConfigFilePath)));
		}
		
		String mimeString = cloudInitUserDataBuilder.buildUserData();
		
		Map<String, String> replacements = new HashMap<String, String>();
		replacements.put(TOKEN_ID_STR, tokenId);
		replacements.put(TOKEN_HOST_STR, sshPrivateHostIP);
		replacements.put(TOKEN_HOST_SSH_PORT_STR, sshRemoteHostPort);
		replacements.put(TOKEN_HOST_HTTP_PORT_STR, sshRemoteHostHttpPort);
		
		String publicKeyToBeReplaced = null;
		if (managerPublicKeyFilePath != null) {
			publicKeyToBeReplaced = IOUtils.toString(new FileInputStream(
					new File(managerPublicKeyFilePath)));
		} else if (userPublicKey != null) {
			publicKeyToBeReplaced = userPublicKey;
		}
		if (publicKeyToBeReplaced != null) {
			replacements.put(TOKEN_MANAGER_SSH_PUBLIC_KEY, publicKeyToBeReplaced);
			replacements.put(TOKEN_MANAGER_SSH_USER, sshCommonUser);
		}
		
		for (Entry<String, String> entry : replacements.entrySet()) {
			mimeString = mimeString.replace(entry.getKey(), entry.getValue());
		}
		
		return new String(Base64.encodeBase64(mimeString.getBytes(Charsets.UTF_8), false, false),
				Charsets.UTF_8);
	}
	
	public static void addMimeBodyPart(Multipart mimeMultipart, String filePath, String fileFormatType) 
			throws IOException, MessagingException {
		
		File file = new File(filePath);
		String fileContent = IOUtils.toString(new FileInputStream(file));
		MimeBodyPart subMessage = new MimeBodyPart();
		subMessage.setText(fileContent);
		subMessage.setContent(fileContent, fileFormatType);
		subMessage.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
		mimeMultipart.addBodyPart(subMessage);
	}
	
}
