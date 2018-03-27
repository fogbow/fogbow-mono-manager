package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;

import org.fogbowcloud.manager.core.ManagerTestHelper;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
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
	public void setUp() {
		this.managerTestHelper = new ManagerTestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.managerTestHelper.shutdown();
	}

	@Test
	public void testDeleteRemoteInstance() throws Exception {
		Order order = new Order("anyvalue", new Token("anyvalue", new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null, true, "");
		order.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);
		ManagerPacketHelper.deleteRemoteInstace(order, managerTestHelper.createPacketSender());
	}

	@Test(expected = OCCIException.class)
	public void testDeleteRemoteInstaceNotFound() throws Exception {
		Order order = new Order("anyvalue", new Token(WRONG_TOKEN, new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null, true, "");
		order.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(this.managerTestHelper.getComputePlugin())
				.removeInstance(Mockito.any(Token.class), Mockito.anyString());

		ManagerPacketHelper.deleteRemoteInstace(order, managerTestHelper.createPacketSender());
	}

	@Test(expected = OCCIException.class)
	public void testDeleteRemoteInstanceUnauthorized() throws Exception {
		Order order = new Order("anyvalue", new Token(WRONG_TOKEN, new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null, true, "");
		order.setInstanceId(INSTANCE_OTHER_USER);
		order.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED))
				.when(this.managerTestHelper.getComputePlugin())
				.removeInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_OTHER_USER));

		ManagerPacketHelper.deleteRemoteInstace(order, managerTestHelper.createPacketSender());
	}
}
