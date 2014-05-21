package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;

import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.fogbowcloud.manager.xmpp.util.TestHelperData;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDeleteRemoteInstance {

	private static final String WRONG_TOKEN = "wrong";
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
		Request request = new Request("anyvalue", new Token("anyvalue", OCCITestHelper.USER_MOCK,
				TestHelperData.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), TestHelperData.USER_NAME, null, null);
		request.setInstanceId(TestHelperData.INSTANCE_ID);
		request.setMemberId(TestHelperData.MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);
		ManagerPacketHelper.deleteRemoteInstace(request, managerTestHelper.createPacketSender());
	}

	@Test(expected = OCCIException.class)
	public void testDeleteRemoteInstaceNotFound() throws Exception {
		Request request = new Request("anyvalue", new Token(WRONG_TOKEN, OCCITestHelper.USER_MOCK,
				TestHelperData.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), TestHelperData.USER_NAME, null, null);
		request.setInstanceId(TestHelperData.INSTANCE_ID);
		request.setMemberId(TestHelperData.MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(this.managerTestHelper.getComputePlugin())
				.removeInstance(Mockito.anyString(), Mockito.anyString());

		ManagerPacketHelper.deleteRemoteInstace(request, managerTestHelper.createPacketSender());
	}

	@Test(expected = OCCIException.class)
	public void testDeleteRemoteInstanceUnauthorized() throws Exception {
		Request request = new Request("anyvalue",  new Token(WRONG_TOKEN, OCCITestHelper.USER_MOCK,
				TestHelperData.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), TestHelperData.USER_NAME, null, null);
		request.setInstanceId(INSTANCE_OTHER_USER);
		request.setMemberId(TestHelperData.MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED))
				.when(this.managerTestHelper.getComputePlugin())
				.removeInstance(Mockito.eq(TestHelperData.ACCESS_TOKEN_ID), Mockito.eq(INSTANCE_OTHER_USER));

		ManagerPacketHelper.deleteRemoteInstace(request, managerTestHelper.createPacketSender());
	}
}
