package org.fogbowcloud.manager.core;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class SSHTunnel {

	public static final String USER_DATA_ATT = "org.fogbowcloud.request.user-data";
	
	public static void create(Properties properties, List<Category> categories,
			Map<String, String> xOCCIAtt) throws FileNotFoundException, IOException {
		
		categories.add(new Category(RequestConstants.USER_DATA_TERM, 
				RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS));
		String sshTunnelCmd = IOUtils.toString(new FileInputStream("bin/fogbow-inject-tunnel"));
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_USER#", properties.getProperty("ssh_tunnel_user"));
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_HOST#", properties.getProperty("ssh_tunnel_host"));
		String[] portRange = properties.getProperty("ssh_tunnel_port_range").split(":");
		
		Integer portFloor = Integer.parseInt(portRange[0]);
		Integer portCeiling = Integer.parseInt(portRange[1]);
		
		Integer sshPort = null;
		
		for (Integer i = portFloor; i <= portCeiling; i++) {
			if (available(i)) {
				sshPort = i;
				break;
			}
		}
		if (sshPort == null) {
			throw new IllegalStateException("No SSH port available for reverse tunnelling");
		}
		
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_PORT#", sshPort.toString());
		xOCCIAtt.put(USER_DATA_ATT, Base64.encodeBase64URLSafeString(
				sshTunnelCmd.getBytes(Charsets.UTF_8)));
	}
	
	private static boolean available(int port) {
		ServerSocket ss = null;
		DatagramSocket ds = null;
		try {
			ss = new ServerSocket(port);
			ss.setReuseAddress(true);
			ds = new DatagramSocket(port);
			ds.setReuseAddress(true);
			return true;
		} catch (IOException e) {
		} finally {
			if (ds != null) {
				ds.close();
			}
			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {
					/* should not be thrown */
				}
			}
		}
		return false;
	}
}
