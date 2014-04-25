package org.fogbowcloud.manager.xmpp;

import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDeleteRemoteInstance {

	private static final String TOKEN = "token";
	private static final String WRONG_TOKEN = "wrong";

	public static final String MANAGER_COMPONENT_URL = "manager.test.com";

	public static final String USER_DEFAULT = "user";
	public static final String INSTANCE_DEFAULT = "instance";
	public static final String INSTANCE_OTHER_USER = "otherUser";

	private ManagerTestHelper managerTestHelper;

	@Before
	public void setUp() throws XMPPException {
		this.managerTestHelper = new ManagerTestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.managerTestHelper.shutdown();
	}

	@Test
	public void testDeleteRemoteInstance() throws Exception {
		Request request = new Request("anyvalue", "anyvalue", USER_DEFAULT, null, null);
		request.setInstanceId(INSTANCE_DEFAULT);

		managerTestHelper.initializeXMPPManagerComponent(false);
		ManagerPacketHelper.deleteRemoteInstace(request, MANAGER_COMPONENT_URL,
				managerTestHelper.createPacketSender());
	}

	@Test(expected = OCCIException.class)
	public void testDeleteRemoteInstaceNotFound() throws Exception {
		Request request = new Request("anyvalue", WRONG_TOKEN, USER_DEFAULT, null, null);
		request.setInstanceId(INSTANCE_DEFAULT);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(this.managerTestHelper.getComputePlugin())
				.removeInstance(Mockito.anyString(), Mockito.anyString());

		ManagerPacketHelper.deleteRemoteInstace(request, MANAGER_COMPONENT_URL,
				managerTestHelper.createPacketSender());
	}

	@Test(expected = OCCIException.class)
	public void testDeleteRemoteInstanceUnauthorized() throws Exception {
		Request request = new Request("anyvalue", WRONG_TOKEN, USER_DEFAULT, null, null);
		request.setInstanceId(INSTANCE_OTHER_USER);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED))
				.when(this.managerTestHelper.getComputePlugin())
				.removeInstance(Mockito.eq(TOKEN), Mockito.eq(INSTANCE_OTHER_USER));

		ManagerPacketHelper.deleteRemoteInstace(request, MANAGER_COMPONENT_URL,
				managerTestHelper.createPacketSender());
	}
}
