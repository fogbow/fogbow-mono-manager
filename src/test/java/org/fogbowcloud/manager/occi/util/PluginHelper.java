package org.fogbowcloud.manager.occi.util;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.util.ComputeApplication.InstanceIdGenerator;
import org.mockito.Mockito;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class PluginHelper {

	private Component component;

	public static final String ACCESS_ID = "HgfugGJHgJgHJGjGJgJg-857GHGYHjhHjH";
	public static final String TENANT_ID = "fc394f2ab2df4114bde39905f800dc57";
	public static final String TENANT_NAME = "admin";

	public static final String USERNAME = "admin";
	public static final String USER_PASS = "reverse";

	public static final int PORT_ENDPOINT = getAvailablePort();

	public PluginHelper() {
		this.component = new Component();
	}

	/**
	 * Getting a available port on range 60000:61000
	 * @return
	 */
	private static int getAvailablePort() {		
		for (int port = 60000; port < 61000; port++) {
			ServerSocket ss = null;
			DatagramSocket ds = null;
			try {
				ss = new ServerSocket(port);
				ss.setReuseAddress(true);
				ds = new DatagramSocket(port);
				ds.setReuseAddress(true);
				return port;
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
		}		
		return -1;
	}

	public void initializeKeystoneComponent() throws Exception {
		this.component = new Component();
		this.component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);

		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OpenStackIdentityPlugin.TENANT_ID_KEY, TENANT_ID);
		tokenAttributes.put(OpenStackIdentityPlugin.TENANT_NAME_KEY, TENANT_NAME);
		Token token = new Token(ACCESS_ID, USERNAME,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, tokenAttributes);

		KeystoneApplication keystoneApplication = new KeystoneApplication(token, USER_PASS);

		keystoneApplication.putTokenAndUser(ACCESS_ID, USERNAME);

		this.component.getDefaultHost().attach(keystoneApplication);
		this.component.start();
	}

	public void initializeComputeComponent(List<String> expectedInstanceIds) throws Exception {
		this.component = new Component();
		this.component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);

		ComputeApplication computeApplication = new ComputeApplication();

		// mocking 5 first instance id generation
		InstanceIdGenerator idGenerator = Mockito.mock(InstanceIdGenerator.class);
		Mockito.when(idGenerator.generateId()).thenReturn(expectedInstanceIds.get(0),
				expectedInstanceIds.get(1), expectedInstanceIds.get(2), expectedInstanceIds.get(3),
				expectedInstanceIds.get(4));
		computeApplication.setIdGenerator(idGenerator);
		computeApplication.putTokenAndUser(ACCESS_ID, USERNAME);

		this.component.getDefaultHost().attach(computeApplication);
		this.component.start();
	}

	public void disconnectComponent() throws Exception {
		this.component.stop();
	}
}