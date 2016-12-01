package org.fogbowcloud.manager.occi.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import org.fogbowcloud.manager.core.plugins.identity.openstackv2.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.OCCIComputeApplication.InstanceIdGenerator;
import org.mockito.Mockito;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Protocol;

public class PluginHelper {

	private static final int DEFAULT_REQUEST_HEADER_SIZE = 1024*1024;

	private Component component;

	public static final int PORT_ENDPOINT = getAvailablePort();
	public static final String CIRROS_IMAGE_TERM = "cadf2e29-7216-4a5e-9364-cf6513d5f1fd";
	public static final String LINUX_X86_TERM = "linuxx86";
	public static final String COMPUTE_OCCI_URL = "http://localhost:" + PORT_ENDPOINT;
	public static final String COMPUTE_NOVAV2_URL = "http://localhost:" + PORT_ENDPOINT;
	public static final String NETWORK_NOVAV2_URL = "http://localhost:" + PORT_ENDPOINT;

	public static final String ACCESS_ID = "HgfugGJHgJgHJGjGJgJg-857GHGYHjhHjH";
	public static final String TENANT_ID = "fc394f2ab2df4114bde39905f800dc57";
	public static final String TENANT_NAME = "admin";

	public static final String USER_ID = "user_id";
	public static final String USERNAME = "admin";
	public static final String USER_PASS = "reverse";

	public static final String FED_USERNAME = "federation_user";
	public static final String FED_USER_PASS = "federation_user_pass";
	
	public PluginHelper() {
		this.component = new Component();
	}

	/**
	 * Getting a available port on range 60000:61000
	 * @return
	 */
	public static int getAvailablePort() {		
		int initialP = 60000;
		int finalP = 61000;
		for (int i = initialP; i < finalP; i++) {
			int port = new Random().nextInt(finalP - initialP) + initialP;
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
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_ID, TENANT_ID);
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_NAME, TENANT_NAME);
		Token token = new Token(ACCESS_ID, new Token.User(USER_ID, USERNAME),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, tokenAttributes);

		KeystoneApplication keystoneApplication = new KeystoneApplication(token, USER_PASS);

		keystoneApplication.putTokenAndUserId(ACCESS_ID, USER_ID);

		this.component.getDefaultHost().attach(keystoneApplication);
		this.component.start();
	}

	public void initializeOCCIComputeComponent(List<String> expectedInstanceIds) throws Exception {
		this.component = new Component();
		Server server = this.component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);
		server.getContext().getParameters().add("http.requestHeaderSize", String.valueOf(DEFAULT_REQUEST_HEADER_SIZE));

		OCCIComputeApplication occiComputeApplication = new OCCIComputeApplication();

		// mocking 5 first instance id generation
		InstanceIdGenerator idGenerator = Mockito.mock(InstanceIdGenerator.class);
		Mockito.when(idGenerator.generateId()).thenReturn(expectedInstanceIds.get(0),
				expectedInstanceIds.get(1), expectedInstanceIds.get(2), expectedInstanceIds.get(3),
				expectedInstanceIds.get(4));
		occiComputeApplication.setIdGenerator(idGenerator);
		occiComputeApplication.putTokenAndUser(ACCESS_ID, USERNAME);
		this.component.getDefaultHost().attach(occiComputeApplication);
		this.component.start();
	}
	
	public NovaV2ComputeApplication initializeNovaV2ComputeComponent(String testDirPath) throws Exception {
		this.component = new Component();
		this.component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);

		NovaV2ComputeApplication novaV2ComputeApplication = new NovaV2ComputeApplication();
		novaV2ComputeApplication.putTokenAndUser(ACCESS_ID, USERNAME);
		novaV2ComputeApplication.setTestDirPath(testDirPath);
		this.component.getDefaultHost().attach(novaV2ComputeApplication);
		this.component.start();
		return novaV2ComputeApplication;
	}

	public void disconnectComponent() throws Exception {
		this.component.stop();
	}

	public String getAttValueFromInstanceDetails(String instanceDetails, String attName) {
		StringTokenizer st = new StringTokenizer(instanceDetails, "\n");
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
			if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE) && line.contains(attName)) {
				StringTokenizer st2 = new StringTokenizer(line, "=");
				st2.nextToken(); // attName
				return st2.nextToken().replaceAll("\"", "");
			}
		}
		return null;
	}

	public static String getContentFile(String filePath) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(filePath));
		try {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append("\n");
				line = br.readLine();
			}
			return sb.toString();
		} finally {
			br.close();
		}
	}
	
	public static Date formatExpirationDate(String format, Token token) throws ParseException {
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.parse(sdf.format(token.getExpirationDate()));
	}
}