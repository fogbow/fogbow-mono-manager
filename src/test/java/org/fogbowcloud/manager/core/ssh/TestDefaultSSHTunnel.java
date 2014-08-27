package org.fogbowcloud.manager.core.ssh;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDefaultSSHTunnel {

	private DefaultSSHTunnel defaultSSHTunnel;

	@Before
	public void setUp() {
		defaultSSHTunnel = new DefaultSSHTunnel();
	}

	@Test
	public void create() throws FileNotFoundException, IOException {
		final String privateHostIP = "10.0.0.1";
		final String publicHostIP = "150.165.80.1";
		final String user = "fogbow";
		final String portRanger = "50000:59999";
		final String port = "50000";
		final String sshHostPort = "2222";

		String sshTunnelCmd = getScriptFogbowInjectTunnel();
		sshTunnelCmd = sshTunnelCmd.replace(DefaultSSHTunnel.REMOTE_USER_STR, user);
		sshTunnelCmd = sshTunnelCmd.replace(DefaultSSHTunnel.REMOTE_HOST_STR, privateHostIP);
		sshTunnelCmd = sshTunnelCmd.replace(DefaultSSHTunnel.REMOTE_PORT_STR, port);
		sshTunnelCmd = sshTunnelCmd.replace(DefaultSSHTunnel.REMOTE_HOST_PORT_STR, sshHostPort);

		Properties properties = new Properties();
		properties.put("ssh_tunnel_public_host", publicHostIP);
		properties.put("ssh_tunnel_private_host", privateHostIP);
		properties.put("ssh_tunnel_user", user);
		properties.put("ssh_tunnel_port_range", portRanger);
		properties.put("ssh_tunnel_host_port", sshHostPort);
		Request request = new Request("is", new Token("accessId", "user", new Date(),
				new HashMap<String, String>()), new ArrayList<Category>(),
				new HashMap<String, String>());

		defaultSSHTunnel.create(properties, request);

		Assert.assertTrue(request.getCategories().get(0).getTerm()
				.equals(RequestConstants.USER_DATA_TERM));
		Assert.assertEquals(Base64.encodeBase64URLSafeString(sshTunnelCmd.getBytes(Charsets.UTF_8))
				.length(), request.getAttValue(DefaultSSHTunnel.USER_DATA_ATT).length());
		Assert.assertEquals(privateHostIP + ":" + port,
				request.getAttValue(DefaultSSHTunnel.SSH_ADDRESS_ATT));
		Assert.assertEquals(publicHostIP + ":" + port,
				request.getAttValue(DefaultSSHTunnel.SSH_PUBLIC_ADDRESS_ATT));
	}

	@Test
	public void releasePort() {
		final int port = 50000;
		final int port2 = 50001;
		Set<Integer> takenPorts = new HashSet<Integer>();
		takenPorts.add(port);
		takenPorts.add(port2);
		defaultSSHTunnel.setTakenPorts(takenPorts);

		Assert.assertEquals(2, defaultSSHTunnel.getTakenPorts().size());

		defaultSSHTunnel.release(port);

		Assert.assertEquals(1, defaultSSHTunnel.getTakenPorts().size());
	}
	
	@Test
	public void releaseInstanceId() throws FileNotFoundException, IOException {
		final String privateHostIP = "10.0.0.1";
		final String publicHostIP = "150.165.80.1";
		final String user = "fogbow";
		final String portRanger = "50000:59999";
		final String port = "50000";

		String sshTunnelCmd = getScriptFogbowInjectTunnel();
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_USER#", user);
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_HOST#", privateHostIP);
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_PORT#", port);

		Properties properties = new Properties();
		properties.put("ssh_tunnel_public_host", publicHostIP);
		properties.put("ssh_tunnel_private_host", privateHostIP);
		properties.put("ssh_tunnel_user", user);
		properties.put("ssh_tunnel_port_range", portRanger);
		Request request = new Request("is", new Token("accessId", "user", new Date(),
				new HashMap<String, String>()), new ArrayList<Category>(),
				new HashMap<String, String>());

		defaultSSHTunnel.update("instanceId", 
				defaultSSHTunnel.create(properties, request));
		
		defaultSSHTunnel.release("instanceId");
		
		Assert.assertTrue(defaultSSHTunnel.getTakenPorts().isEmpty());
	}

	@SuppressWarnings("resource")
	private String getScriptFogbowInjectTunnel() {
		BufferedReader bufferedReader = null;
		String fullText = null;
		try {
			bufferedReader = new BufferedReader(new FileReader("bin/fogbow-inject-tunnel"));
			StringBuilder sb = new StringBuilder();
			String line = bufferedReader.readLine();
			while (line != null) {
				sb.append(line + "\n");
				line = bufferedReader.readLine();
			}
			fullText = sb.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return fullText;
	}
}
