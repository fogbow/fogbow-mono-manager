package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.fogbowcloud.manager.xmpp.util.TestHelperData;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetRemoteInstance {

	private static final String WRONG_TOKEN = "wrong";
	private static final String INSTANCE_OTHER_USER = "otherUser";

	private ManagerTestHelper managerTestHelper;

	@Before
	public void setUp() throws XMPPException {
		this.managerTestHelper = new ManagerTestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.managerTestHelper.shutdown();
	}

	private Instance createInstance() {
		Category category = new Category("term", "schema", "class");

		List<String> supportedAtt = new ArrayList<String>();
		supportedAtt.add("att1");

		List<String> actions = new ArrayList<String>();
		actions.add("actions1");

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(new Resource(category, supportedAtt, actions, "location", "title", "rel"));

		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("key", "value");

		Link link = new Link("linkname", attributes);

		return new Instance(TestHelperData.INSTANCE_ID, resources, attributes, link);
	}

	@Test
	public void testGetRemoteInstance() throws Exception {
		managerTestHelper.initializeXMPPManagerComponent(false);
		Instance instance = createInstance();

		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(
						Mockito.eq(TestHelperData.ACCESS_TOKEN_ID),
						Mockito.eq(TestHelperData.INSTANCE_ID))).thenReturn(instance);

		Request request = new Request("anyvalue", new Token("anyvalue", OCCITestHelper.USER_MOCK,
				TestHelperData.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()),
				TestHelperData.USER_NAME, null, null);
		request.setInstanceId(TestHelperData.INSTANCE_ID);
		request.setMemberId(TestHelperData.MANAGER_COMPONENT_URL);

		Instance remoteInstance = null;
		try {
			remoteInstance = ManagerPacketHelper.getRemoteInstance(request,
					managerTestHelper.createPacketSender());
		} catch (Exception e) {
			e.printStackTrace();
		}

		Assert.assertEquals(instance.getId(), remoteInstance.getId());
		Assert.assertEquals(instance.getAttributes(), remoteInstance.getAttributes());
		Assert.assertEquals(instance.getResources().get(0).toHeader(), remoteInstance
				.getResources().get(0).toHeader());
		Assert.assertEquals(instance.getLink().getName(), remoteInstance.getLink().getName());
	}

	@Test
	public void testGetRemoteInstaceNotFound() throws Exception {
		Request request = new Request("anyvalue", new Token(WRONG_TOKEN, OCCITestHelper.USER_MOCK,
				TestHelperData.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()),
				TestHelperData.USER_NAME, null, null);
		request.setInstanceId(TestHelperData.INSTANCE_ID);
		request.setMemberId(TestHelperData.MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(this.managerTestHelper.getComputePlugin())
				.getInstance(Mockito.anyString(), Mockito.anyString());

		Assert.assertNull(ManagerPacketHelper.getRemoteInstance(request,
				managerTestHelper.createPacketSender()));
	}

	@Test(expected = OCCIException.class)
	public void testGetRemoteInstanceUnauthorized() throws Exception {
		Request request = new Request("anyvalue", new Token(WRONG_TOKEN, OCCITestHelper.USER_MOCK,
				TestHelperData.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()),
				TestHelperData.USER_NAME, null, null);
		request.setInstanceId(INSTANCE_OTHER_USER);
		request.setMemberId(TestHelperData.MANAGER_COMPONENT_URL);

		managerTestHelper.initializeXMPPManagerComponent(false);

		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED))
				.when(this.managerTestHelper.getComputePlugin())
				.getInstance(Mockito.eq(TestHelperData.ACCESS_TOKEN_ID),
						Mockito.eq(INSTANCE_OTHER_USER));

		ManagerPacketHelper.getRemoteInstance(request, managerTestHelper.createPacketSender());
	}
}