package org.fogbowcloud.manager.core.ssh;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class DefaultSSHTunnel implements SSHTunnel {

	protected static final String REMOTE_PORT_STR = "#REMOTE_PORT#";
	protected static final String REMOTE_USER_STR = "#REMOTE_USER#";
	protected static final String REMOTE_HOST_STR = "#REMOTE_HOST#";
	protected static final String REMOTE_HOST_PORT_STR = "#REMOTE_HOST_PORT#";

	public static final String USER_DATA_ATT = "org.fogbowcloud.request.user-data";
	public static final String SSH_ADDRESS_ATT = "org.fogbowcloud.request.ssh-address";
	public static final String SSH_PUBLIC_ADDRESS_ATT = "org.fogbowcloud.request.ssh-public-address";
	private static final String DEFAULT_SSH_HOST_PORT = "22";

	private Set<Integer> takenPorts = new HashSet<Integer>();
	private Map<String, Integer> instanceToPort = new HashMap<String, Integer>();

	public Integer create(Properties properties, Request request) throws FileNotFoundException,
			IOException {

		boolean hasCategory = false;
		for (Category category : request.getCategories()) {
			if (category.getTerm().equals(RequestConstants.USER_DATA_TERM)) {
				hasCategory = true;
				break;
			}
		}
		if (!hasCategory) {
			request.addCategory(new Category(RequestConstants.USER_DATA_TERM,
					RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS));
		}

		String sshTunnelCmd = IOUtils.toString(new FileInputStream("bin/fogbow-inject-tunnel"));
		String sshPrivateHostIP = properties
				.getProperty(ConfigurationConstants.SSH_PRIVATE_HOST_KEY);
		String sshPublicHostIP = properties.getProperty(ConfigurationConstants.SSH_PUBLIC_HOST_KEY);
		String sshRemoteHostPort = properties.getProperty(ConfigurationConstants.SSH_HOST_PORT_KEY);

		if (sshRemoteHostPort == null) {
			sshRemoteHostPort = DEFAULT_SSH_HOST_PORT;
		}

		sshTunnelCmd = sshTunnelCmd.replace(REMOTE_USER_STR,
				properties.getProperty(ConfigurationConstants.SSH_USER_KEY));
		sshTunnelCmd = sshTunnelCmd.replace(REMOTE_HOST_STR, sshPrivateHostIP);
		sshTunnelCmd = sshTunnelCmd.replace(REMOTE_HOST_PORT_STR, sshRemoteHostPort);
		String[] portRange = properties.getProperty(ConfigurationConstants.SSH_PORT_RANGE_KEY)
				.split(":");

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

		sshTunnelCmd = sshTunnelCmd.replace(REMOTE_PORT_STR, sshPort.toString());
		request.putAttValue(USER_DATA_ATT,
				Base64.encodeBase64URLSafeString(sshTunnelCmd.getBytes(Charsets.UTF_8)));
		request.putAttValue(SSH_ADDRESS_ATT, sshPrivateHostIP + ":" + sshPort);
		request.putAttValue(SSH_PUBLIC_ADDRESS_ATT, sshPublicHostIP + ":" + sshPort);

		return sshPort;
	}

	public void update(String instanceId, Integer port) {
		instanceToPort.put(instanceId, port);
	}

	public void release(String instanceId) {
		if (instanceId == null) {
			return;
		}
		Integer port = instanceToPort.remove(instanceId);
		release(port);
	}

	public void release(Integer port) {
		if (port == null) {
			return;
		}
		takenPorts.remove(port);
	}

	@Override
	public String getPublicAddress(Properties properties, String instanceId) {
		String sshPublicHostIP = properties.getProperty(ConfigurationConstants.SSH_PUBLIC_HOST_KEY);
		Integer port = instanceToPort.get(instanceId);
		return sshPublicHostIP + ":" + port;
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

	public Set<Integer> getTakenPorts() {
		return takenPorts;
	}

	public void setTakenPorts(Set<Integer> takenPorts) {
		this.takenPorts = takenPorts;
	}
}
