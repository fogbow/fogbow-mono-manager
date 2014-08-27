package org.fogbowcloud.manager.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

public class UserdataUtils {

	protected static final String TOKEN_ID_STR = "#TOKEN_ID#";
	protected static final String REMOTE_HOST_STR = "#REMOTE_HOST#";
	protected static final String REMOTE_HOST_PORT_STR = "#REMOTE_HOST_PORT#";
	private static final String DEFAULT_SSH_HOST_PORT = "22";
	
	public static String createCommand(String tokenId, String sshPrivateHostIP,
			String sshRemoteHostPort) throws FileNotFoundException, IOException {
		
		String sshTunnelCmd = IOUtils.toString(new FileInputStream(
				"bin/fogbow-create-reverse-tunnel"));

		if (sshRemoteHostPort == null || sshRemoteHostPort.isEmpty()) {
			sshRemoteHostPort = DEFAULT_SSH_HOST_PORT;
		}
		sshTunnelCmd = sshTunnelCmd.replace(TOKEN_ID_STR, tokenId);
		sshTunnelCmd = sshTunnelCmd.replace(REMOTE_HOST_STR, sshPrivateHostIP);
		sshTunnelCmd = sshTunnelCmd.replace(REMOTE_HOST_PORT_STR, sshRemoteHostPort);

		return sshTunnelCmd;
	}

}
