package org.fogbowcloud.manager.occi.network;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
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

public class TestGetNetwork {

	private static final String BASIC_TOKEN = "Basic token";
	private static final String ACCESS_TOKEN = "access_token";
	private static final String INSTANCE_DB_FILE = "./src/test/resources/fedNetwork.db";

	private NetworkPlugin networkPlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private MapperPlugin mapperPlugin;
	private OCCITestHelper helper;
	private Map<String, List<Order>> ordersToAdd;
	private Token tokenA;
	private Token tokenB;
	private ManagerController facade;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		
		this.helper = new OCCITestHelper();
		
		NetworkDataStore networkDB = new NetworkDataStore("jdbc:h2:file:" + INSTANCE_DB_FILE);
		networkDB.deleteAll();
		networkDB = null;
		
		networkPlugin = Mockito.mock(NetworkPlugin.class);
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		mapperPlugin = Mockito.mock(MapperPlugin.class);
		
		ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(BASIC_TOKEN, new ArrayList<Order>());
		
		tokenA = new Token("id_one", new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		tokenB = new Token("id_two", new Token.User(BASIC_TOKEN, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		// Mocking
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN))
				.thenReturn(tokenA);
		Mockito.when(identityPlugin.createToken(Mockito.anyMap()))
				.thenReturn(tokenA);
		
		String basicAuthToken = new String(Base64.decodeBase64(BASIC_TOKEN.replace("Basic ", "")));
		
		Mockito.when(identityPlugin.getToken(basicAuthToken)).thenReturn(tokenB);
		
		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(new HashMap<String, String>());
		
		facade = this.helper.initializeComponentNetwork(networkPlugin, identityPlugin, authorizationPlugin, ordersToAdd, mapperPlugin, null);
		
	}

	@After
	public void tearDown() throws Exception {
		TestDataStorageHelper.clearManagerDataStore(
				facade.getManagerDataStoreController().getManagerDatabase());
		File dbFile = new File(INSTANCE_DB_FILE + ".mv.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}		
		this.helper.stopComponent();
	}

	@Test
	public void testGetNetworkOk() throws Exception {
		
		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		
		List<Order> userOrders = new ArrayList<Order>();
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		userOrders.add(order1);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(1, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetNetworkOkTextPlain() throws Exception {
		
		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		List<Order> userOrders = new ArrayList<Order>();
		Order orderOne = new Order("1", tokenA, null, xOCCIAtt, true, "");
		orderOne.setInstanceId(INSTANCE_1_ID);
		orderOne.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderOne.setResourceKind(OrderConstants.NETWORK_TERM);
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		
		userOrders.add(orderOne);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(1, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetNetworkOkTokenBasic() throws Exception {
		
		String INSTANCE_1_ID = "network01";	
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		List<Order> userOrders = new ArrayList<Order>();
		Order orderOne = new Order("1", tokenB, null, xOCCIAtt, true, "");
		orderOne.setInstanceId(INSTANCE_1_ID);
		orderOne.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderOne.setResourceKind(OrderConstants.NETWORK_TERM);
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		
		userOrders.add(orderOne);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, BASIC_TOKEN);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(1, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetMultiNetworkOk() throws Exception {
		
		String INSTANCE_1_ID = "network01";
		String INSTANCE_2_ID = "network02";
		String INSTANCE_3_ID = "network03";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		List<Order> userOrders = new ArrayList<Order>();
		
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		Order order2 = new Order("2", tokenA, null, xOCCIAtt, true, "");
		order2.setInstanceId(INSTANCE_2_ID);
		order2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		Order order3 = new Order("3", tokenB, null, xOCCIAtt, true, "");
		order3.setInstanceId(INSTANCE_3_ID);
		order3.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		
		userOrders.add(order1);
		userOrders.add(order2);
		userOrders.add(order3);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetNetworkAcceptUriOk() throws Exception {
		
		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		
		List<Order> userOrders = new ArrayList<Order>();
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		order1.setResourceKind(OrderConstants.NETWORK_TERM);
		userOrders.add(order1);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(1, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}
	
	@Test
	public void testGetNetworkNotOkNoToken() throws Exception {
		

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN))
				.thenReturn(null);
		
		facade.setFederationIdentityPlugin(identityPlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		
		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		
		List<Order> userOrders = new ArrayList<Order>();
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		order1.setResourceKind(OrderConstants.NETWORK_TERM);
		userOrders.add(order1);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetNetworkNotOkNotAcceptable() throws Exception {
		

		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		
		List<Order> userOrders = new ArrayList<Order>();
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		userOrders.add(order1);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.ACCEPT, "Wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetNetworkOkWithId() throws Exception {
		
		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		
		List<Order> userOrders = new ArrayList<Order>();
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		userOrders.add(order1);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
	
		Instance networkInstance = new Instance(INSTANCE_1_ID);
		
		String adress = "10.50.10.0/16";
		String allocation = "dynamic";
		String gateway = "10.50.10.1";
		String label = "PrivateNetwork";
		String state = "active";
		String vlan = "20.30.40.100";

		networkInstance.addAttribute(OCCIConstants.NETWORK_ADDRESS, adress);
		networkInstance.addAttribute(OCCIConstants.NETWORK_ALLOCATION, allocation);
		networkInstance.addAttribute(OCCIConstants.NETWORK_GATEWAY, gateway);
		networkInstance.addAttribute(OCCIConstants.NETWORK_LABEL, label);
		networkInstance.addAttribute(OCCIConstants.NETWORK_STATE, state);
		networkInstance.addAttribute(OCCIConstants.NETWORK_VLAN, vlan);

		Mockito.when(networkPlugin.getInstance(tokenA, INSTANCE_1_ID))
				.thenReturn(networkInstance);
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK + INSTANCE_1_ID + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		String responseStr = null;
		responseStr = EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		
		Assert.assertEquals("\""+adress+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals("\""+allocation+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_ALLOCATION));
		Assert.assertEquals("\""+gateway+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_GATEWAY));
		Assert.assertEquals("\""+label+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_LABEL));
		Assert.assertEquals("\""+state+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_STATE));
		Assert.assertEquals("\""+vlan+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_VLAN));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetNetworkNotOkInvalidId() throws Exception {
		
		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		
		List<Order> userOrders = new ArrayList<Order>();
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		order1.setResourceKind(OrderConstants.NETWORK_TERM);
		userOrders.add(order1);
				
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
	
		Instance networkInstance = new Instance(INSTANCE_1_ID);
		
		String adress = "10.50.10.0/16";
		String allocation = "dynamic";
		String gateway = "10.50.10.1";
		String label = "PrivateNetwork";
		String state = "active";
		String vlan = "20.30.40.100";

		networkInstance.addAttribute(OCCIConstants.NETWORK_ADDRESS, adress);
		networkInstance.addAttribute(OCCIConstants.NETWORK_ALLOCATION, allocation);
		networkInstance.addAttribute(OCCIConstants.NETWORK_GATEWAY, gateway);
		networkInstance.addAttribute(OCCIConstants.NETWORK_LABEL, label);
		networkInstance.addAttribute(OCCIConstants.NETWORK_STATE, state);
		networkInstance.addAttribute(OCCIConstants.NETWORK_VLAN, vlan);

		Mockito.when(networkPlugin.getInstance(tokenA, INSTANCE_1_ID))
				.thenReturn(networkInstance);
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK + INSTANCE_1_ID+"invalid"+ Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetNetworkAcceptUriWithIDNotOK() throws Exception {
		
		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		
		List<Order> userOrders = new ArrayList<Order>();
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		order1.setResourceKind(OrderConstants.NETWORK_TERM);
		userOrders.add(order1);
		
		for (Order order : userOrders){
			facade.getManagerDataStoreController().addOrder(order);
		}
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK + INSTANCE_1_ID + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}
	

}