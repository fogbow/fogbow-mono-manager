package org.fogbowcloud.manager.occi.util;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
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

//	public static final Date EXPIRATION_DATE = new Date(System.currentTimeMillis()
//			+ TestHelperData.LONG_TIME);
	public static final String USERNAME_FOGBOW = "admin";
	public static final String PASSWORD_FOGBOW = "reverse";

	public static final int PORT_ENDPOINT = 8182;

	public PluginHelper() {
		this.component = new Component();
	}

	public void initializeKeystoneComponent() throws Exception {
		this.component = new Component();
		this.component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);

		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_ID, TENANT_ID);
//		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, USERNAME_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, TENANT_NAME);
		Token token = new Token(ACCESS_ID, USERNAME_FOGBOW, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, tokenAttributes);

		// TODO Refactor! We really need all these args to KeytoneApplication?
		KeystoneApplication keystoneApplication = new KeystoneApplication(USERNAME_FOGBOW,
				PASSWORD_FOGBOW, TENANT_NAME, ACCESS_ID, token);

		keystoneApplication.putTokenAndUser(ACCESS_ID, USERNAME_FOGBOW);

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
		computeApplication.putTokenAndUser(ACCESS_ID, USERNAME_FOGBOW);

		this.component.getDefaultHost().attach(computeApplication);
		this.component.start();
	}

	public void disconnectComponent() throws Exception {
		this.component.stop();
	}
}