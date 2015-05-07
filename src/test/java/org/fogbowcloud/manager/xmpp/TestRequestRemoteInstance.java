package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.fogbowcloud.manager.core.AsynchronousRequestCallback;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRequestRemoteInstance {

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
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRequestRemoteInstanceAsynchronously() throws Exception {
		managerTestHelper.initializeXMPPManagerComponent(false);
		Request request = createRequest();

		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenReturn(
				INSTANCE_DEFAULT);

		final BlockingQueue<String> bq = new LinkedBlockingQueue<String>();

		ManagerPacketHelper.asynchronousRemoteRequest(request.getId(), request.getCategories(), 
				request.getxOCCIAtt(), DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
				request.getFederationToken(), managerTestHelper.createPacketSender(), 
				new AsynchronousRequestCallback() {
					
					@Override
					public void success(String instanceId) {
						bq.add(instanceId);
					}
					
					@Override
					public void error(Throwable t) {
						// TODO Auto-generated method stub
						
					}
				});
		String instanceId = bq.poll(5, TimeUnit.SECONDS);
		Assert.assertEquals(INSTANCE_DEFAULT, instanceId);
	}

	private Request createRequest() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category("term1", "scheme1", "class1"));
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("key1", "value1");
		attributes.put("key2", "value2");
		Request request = new Request("id", new Token("anyvalue",
				OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), new Token("anyvalue",
				OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), categories, attributes, true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		request.setInstanceId(INSTANCE_DEFAULT);
		return request;
	}
}
