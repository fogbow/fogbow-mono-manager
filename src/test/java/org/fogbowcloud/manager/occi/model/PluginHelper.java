package org.fogbowcloud.manager.occi.model;

import java.util.List;

import org.fogbowcloud.manager.occi.model.ComputeApplication.InstanceIdGenerator;
import org.mockito.Mockito;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class PluginHelper {

	private Component component;

	public static final String AUTH_TOKEN = "HgfugGJHgJgHJGjGJgJg-857GHGYHjhHjH";
	public static final String USERNAME = "admin";
	public static final String PASSWORD = "reverse";

	public static final int PORT_ENDPOINT = 8182;

	public PluginHelper() {
		this.component = new Component();
	}

	public void initializeKeystoneComponent() throws Exception {
		this.component = new Component();
		this.component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);

		KeystoneApplication keystoneApplication = new KeystoneApplication(USERNAME, PASSWORD);
		keystoneApplication.putTokenAndUser(AUTH_TOKEN, USERNAME);
		
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
		computeApplication.putTokenAndUser(AUTH_TOKEN, USERNAME);

		this.component.getDefaultHost().attach(computeApplication);
		this.component.start();
	}

	public void disconnectComponent() throws Exception {
		this.component.stop();
	}
}
