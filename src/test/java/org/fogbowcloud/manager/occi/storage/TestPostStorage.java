package org.fogbowcloud.manager.occi.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPostStorage {

	private OCCITestHelper helper;
	private MapperPlugin mapperPlugin;

	@Before
	public void setup() throws Exception {
		
		String FAKE_POST_INSTANCE_HOST = "102.102.02.01";
		String FAKE_POST_INSTANCE_PORT = "9001";
		String INSTANCE_1_ID = "testOne";
		String INSTANCE_2_ID = "testTwo";
		String INSTANCE_3_ID_WITHOUT_USER = "testThree";
		String USER_WITHOUT_ORDERS = "withoutInstances";
		String ACCESS_TOKEN = "access_token";
		
		StoragePlugin storagePlugin;
		IdentityPlugin identityPlugin;
		AuthorizationPlugin authorizationPlugin;
		
		this.helper = new OCCITestHelper();
		
		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		Instance instance1 = new Instance(INSTANCE_1_ID, list, map, null, InstanceState.PENDING);

		Map<String, String> postMap = new HashMap<String, String>();
		postMap.put(Instance.SSH_PUBLIC_ADDRESS_ATT, FAKE_POST_INSTANCE_HOST+":"+FAKE_POST_INSTANCE_PORT);
		postMap.put("test", "test");

		
		storagePlugin = Mockito.mock(StoragePlugin.class);
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_1_ID)))
				.thenReturn(instance1);		
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_1_ID)))
				.thenReturn(instance1);
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_2_ID)))
				.thenReturn(new Instance(INSTANCE_2_ID));
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_3_ID_WITHOUT_USER)))
				.thenReturn(instance1);		

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(new Token("id", new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(), new HashMap<String, String>()));
		
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN))
		.thenReturn(new Token("id_two", new Token.User(USER_WITHOUT_ORDERS, ""), new Date(), new HashMap<String, String>()));		
		
		
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");

		List<Order> ordersA = new LinkedList<Order>();
		
		Token token = new Token(OCCITestHelper.ACCESS_TOKEN, new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.STORAGE_TERM);
		Order order1 = new Order("1", token, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(order1);
		Order order2 = new Order("2", token, null, xOCCIAtt, true, "");
		order2.setInstanceId(INSTANCE_2_ID);
		order2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(order2);
		Order order3 = new Order("3", new Token("token", new Token.User("user", ""), DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, xOCCIAtt, true, "");
		order3.setInstanceId(INSTANCE_3_ID_WITHOUT_USER);
		order3.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(order3);

		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(new HashMap<String, String>());

		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, ordersA);
		
		ordersToAdd.put(USER_WITHOUT_ORDERS, new ArrayList<Order>());		
		
		this.helper.initializeComponentCompute(null, storagePlugin, identityPlugin, authorizationPlugin, null,
				Mockito.mock(AccountingPlugin.class), Mockito.mock(BenchmarkingPlugin.class), ordersToAdd,
				mapperPlugin);
		

	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testPostStorageOk() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader("Category", OrderConstants.STORAGE_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");	
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.SIZE.getValue() + "=" + "10");				
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		
		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);
		
		assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		assertNotNull(instanceId);
		assertTrue(OCCITestHelper.getLocationIds(response).isEmpty());

	}
	
	@Test
	public void testPostStorageInvalidCategory() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader("Category", OrderConstants.COMPUTE_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");	
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.SIZE.getValue() + "=" + "10");				
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostStorageNotOkNoSize() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader("Category", OrderConstants.STORAGE_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");		
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostStorageNotOkNoToken() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader("Category", OrderConstants.STORAGE_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");
		httpPost.addHeader("X-OCCI-Attribute", StorageAttribute.SIZE.getValue() + "=" + "10");		
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostStorageNoContentType() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader("Category", OrderConstants.STORAGE_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");
		httpPost.addHeader("X-OCCI-Attribute", StorageAttribute.SIZE.getValue() + "=" + "10");		
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostStorageInvalidContentType() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, "InvalidContent");
		httpPost.addHeader("Category", OrderConstants.STORAGE_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");
		httpPost.addHeader("X-OCCI-Attribute", StorageAttribute.SIZE.getValue() + "=" + "10");		
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	//TODO fazer teste para valores invalidos de tamanho de disco: negativo, zero, etc.
	@Test
	public void testPostStorageNotOkWrongValues() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader("Category", OrderConstants.STORAGE_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.SIZE.getValue() + "=" + "0");		
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
		
		
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.SIZE.getValue() + "=" + "-10");	
		
		client = HttpClients.createMinimal();
		response = client.execute(httpPost);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
		
	}
	
	@Test
	public void testPostStorageNotOkNoNumericValue() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader("Category", OrderConstants.STORAGE_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.SIZE.getValue() + "=" + "10A");		
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	

}
