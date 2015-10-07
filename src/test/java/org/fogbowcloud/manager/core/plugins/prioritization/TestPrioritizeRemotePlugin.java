package org.fogbowcloud.manager.core.plugins.prioritization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPrioritizeRemotePlugin {

	private Properties properties;
	private AccountingPlugin accountingPlugin;

	@Before
	public void setUp(){
		properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		accountingPlugin = Mockito.mock(AccountingPlugin.class);	
	}
	
	@Test
	public void testTakeNullForLocalRequest() {		
		PriotizeRemoteRequestPlugin plugin = new PriotizeRemoteRequestPlugin(properties, accountingPlugin);
			
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Request servedRequest1 = new Request("id1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedRequest1.setInstanceId("instanceId1");
		servedRequest1.setState(RequestState.FULFILLED);
		servedRequest1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Request servedRequest2 = new Request("id2", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedRequest2.setInstanceId("instanceId2");
		servedRequest2.setState(RequestState.FULFILLED);
		servedRequest2.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(servedRequest1);
		requests.add(servedRequest2);
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		
		Assert.assertNull(plugin.takeFrom(newRequest, requests));
	}
	
	@Test
	public void testTakeMostRecentServedRequest() {		
		PriotizeRemoteRequestPlugin plugin = new PriotizeRemoteRequestPlugin(properties, accountingPlugin);
			
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Request servedRequest1 = new Request("id1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedRequest1.setInstanceId("instanceId1");
		servedRequest1.setState(RequestState.FULFILLED);
		servedRequest1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Request servedRequest2 = new Request("id2", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedRequest2.setInstanceId("instanceId2");
		servedRequest2.setState(RequestState.FULFILLED);
		servedRequest2.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(servedRequest1);
		requests.add(servedRequest2);
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member2", dateUtils);
		
		// checking if take from most recent request
		Assert.assertEquals(servedRequest2, plugin.takeFrom(newRequest, requests));
	}

	@Test
	public void testTakeMostRecentLocalRequest() {		
		PriotizeRemoteRequestPlugin plugin = new PriotizeRemoteRequestPlugin(properties, accountingPlugin);
			
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Request request1 = new Request("id1", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		request1.setInstanceId("instanceId1");
		request1.setState(RequestState.FULFILLED);
		request1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Request request2 = new Request("id2", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		request2.setInstanceId("instanceId2");
		request2.setState(RequestState.FULFILLED);
		request2.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);
		requests.add(request2);
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member2", dateUtils);
		
		// checking if take from most recent request
		Assert.assertEquals(request2, plugin.takeFrom(newRequest, requests));
	}
	
	@Test
	public void testTakeMostRecentAccrossLocalAndServedRequest() {		
		PriotizeRemoteRequestPlugin plugin = new PriotizeRemoteRequestPlugin(properties, accountingPlugin);
			
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Request servedRequest1 = new Request("id1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedRequest1.setInstanceId("instanceId1");
		servedRequest1.setState(RequestState.FULFILLED);
		servedRequest1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		now += 30;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Request request2 = new Request("id2", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		request2.setInstanceId("instanceId2");
		request2.setState(RequestState.FULFILLED);
		request2.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		now += 30;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		Request request3 = new Request("id3", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		request3.setInstanceId("instanceId3");
		request3.setState(RequestState.FULFILLED);
		request3.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(servedRequest1);
		requests.add(request2);
		requests.add(request3);
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member2", dateUtils);
		
		// checking if take from most recent request
		Assert.assertEquals(request3, plugin.takeFrom(newRequest, requests));
	}
}
