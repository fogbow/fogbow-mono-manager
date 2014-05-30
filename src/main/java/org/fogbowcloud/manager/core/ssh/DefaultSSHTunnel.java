package org.fogbowcloud.manager.core.ssh;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class DefaultSSHTunnel implements SSHTunnel {

	public static final String USER_DATA_ATT = "org.fogbowcloud.request.user-data";
	public static final String SSH_ADDRESS_ATT = "org.fogbowcloud.request.ssh-address";
	public static final String SSH_PUBLIC_ADDRESS_ATT = "org.fogbowcloud.request.ssh-public-address";
	private Set<Integer> takenPorts = new HashSet<Integer>();
	
	public void create(Properties properties, Request request) throws FileNotFoundException, IOException {
		
		request.addCategory(new Category(RequestConstants.USER_DATA_TERM, 
				RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS));
		
		String sshTunnelCmd = IOUtils.toString(new FileInputStream("bin/fogbow-inject-tunnel"));
		String sshPrivateHostIP = properties.getProperty("ssh_tunnel_private_host");
		String sshPublicHostIP = properties.getProperty("ssh_tunnel_public_host");
		
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_USER#", properties.getProperty("ssh_tunnel_user"));
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_HOST#", sshPrivateHostIP);
		String[] portRange = properties.getProperty("ssh_tunnel_port_range").split(":");
		
		Integer portFloor = Integer.parseInt(portRange[0]);
		Integer portCeiling = Integer.parseInt(portRange[1]);
		
		Integer sshPort = null;
		
		for (Integer i = portFloor; i <= portCeiling; i++) {
			if (!takenPorts.contains(i) && available(i)) {
				sshPort = i;
				takenPorts.add(i);
				break;
			}
		}
		if (sshPort == null) {
			throw new IllegalStateException("No SSH port available for reverse tunnelling");
		}
		
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_PORT#", sshPort.toString());
		request.putAttValue(USER_DATA_ATT, Base64.encodeBase64URLSafeString(
				sshTunnelCmd.getBytes(Charsets.UTF_8)));
		request.putAttValue(SSH_ADDRESS_ATT, sshPrivateHostIP + ":" + sshPort);
		request.putAttValue(SSH_PUBLIC_ADDRESS_ATT, sshPublicHostIP + ":" + sshPort);
	}
	
	public void release(Request request) {
		String sshAddress = request.getAttValue(SSH_ADDRESS_ATT);
		if (sshAddress == null) {
			return;
		}
		String[] sshAddressSplit = sshAddress.split(":");
		int sshPort = Integer.parseInt(sshAddressSplit[1]);
		takenPorts.remove(sshPort);
	}
	
	private static boolean available(int port) {
		Socket socket = null;
		try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(port), 200);
            socket.close();
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
        	if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					/* should not be thrown */
				}
			}
        }
	}
	
	public Set<Integer> getTakenPorts() {
		return takenPorts;
	}
	
	public void setTakenPorts(Set<Integer> takenPorts) {
		this.takenPorts = takenPorts;
	}
}
