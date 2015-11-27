package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.fogbowcloud.manager.core.AsynchronousRequestCallback;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;

public class TestRemoceServeredRequest {

	private static final String INSTANCE_ID = "instanceId";
	private static final String ACCESS_ID = "accessId";
	private static final String REQUEST_ID = "requestId";

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
	public void test() throws Exception {
		ManagerXmppComponent initializeXMPPManagerComponent = managerTestHelper.initializeXMPPManagerComponent(false);
		Request request = createRequest();
		RequestRepository requests = new RequestRepository();
		requests.addRequest(request.getFederationToken().getUser(), request);
		ManagerController managerFacade = initializeXMPPManagerComponent.getManagerFacade();
		managerFacade.setRequests(requests);

		Token token = new Token(ACCESS_ID, OCCITestHelper.USER_MOCK, null, new HashMap<String, String>());
		
		Mockito.when(managerTestHelper.getFederationIdentityPlugin().getToken(ACCESS_ID))
				.thenReturn(token);
		Mockito.when(managerTestHelper.getIdentityPlugin().getToken(ACCESS_ID))
		.thenReturn(token);
		Mockito.when(managerTestHelper.getAuthorizationPlugin().isAuthorized(token)).thenReturn(true);			

		Assert.assertEquals(1, initializeXMPPManagerComponent.getManagerFacade()
				.getRequestsFromUser(token.getAccessId()).size());
		
		final BlockingQueue<String> bq = new LinkedBlockingQueue<String>();

		ManagerPacketHelper.deleteRemoteRequest(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				request, managerTestHelper.createPacketSender(), new AsynchronousRequestCallback() {

					@Override
					public void success(String instanceId) {
						bq.add(new String());
					}

					@Override
					public void error(Throwable t) {
					}
				});
		
		String instanceId = bq.poll(5, TimeUnit.SECONDS);
		
		Assert.assertEquals(0, initializeXMPPManagerComponent.getManagerFacade()
				.getRequestsFromUser(token.getAccessId()).size());

		Mockito.verify(managerTestHelper.getComputePlugin(), VerificationModeFactory.times(1))
				.removeInstance(token, INSTANCE_ID);
		
		Assert.assertEquals("", instanceId);
	}

	private Request createRequest() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category("term1", "scheme1", "class1"));
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("key1", "value1");
		attributes.put("key2", "value2");
		Request request = new Request(REQUEST_ID, new Token(ACCESS_ID,
				OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), categories, attributes, true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		request.setInstanceId(INSTANCE_ID);
		return request;
	}	
	
}