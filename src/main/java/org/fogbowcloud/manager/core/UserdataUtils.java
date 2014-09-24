package org.fogbowcloud.manager.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

public class UserdataUtils {

	protected static final String TOKEN_ID_STR = "#TOKEN_ID#";
	protected static final String TOKEN_HOST_STR = "#TOKEN_HOST#";
	protected static final String TOKEN_HOST_HTTP_PORT_STR = "#TOKEN_HOST_HTTP_PORT#";
	protected static final String TOKEN_HOST_SSH_PORT_STR = "#TOKEN_HOST_SSH_PORT#";
	private static final String DEFAULT_SSH_HOST_PORT = "22";
	
	public static String createBase64Command(String tokenId, String sshPrivateHostIP,
			String sshRemoteHostPort, String sshRemoteHostHttpPort) throws FileNotFoundException, IOException {
		
		String sshTunnelCmd = IOUtils.toString(new FileInputStream(
				"bin/fogbow-create-reverse-tunnel"));

		if (sshRemoteHostPort == null || sshRemoteHostPort.isEmpty()) {
			sshRemoteHostPort = DEFAULT_SSH_HOST_PORT;
		}
		sshTunnelCmd = sshTunnelCmd.replace(TOKEN_ID_STR, tokenId);
		sshTunnelCmd = sshTunnelCmd.replace(TOKEN_HOST_STR, sshPrivateHostIP);
		sshTunnelCmd = sshTunnelCmd.replace(TOKEN_HOST_SSH_PORT_STR, sshRemoteHostPort);
		sshTunnelCmd = sshTunnelCmd.replace(TOKEN_HOST_HTTP_PORT_STR, sshRemoteHostHttpPort);

		return new String(Base64.encodeBase64(sshTunnelCmd.getBytes(Charsets.UTF_8), false, false),
				Charsets.UTF_8);
	}

}
