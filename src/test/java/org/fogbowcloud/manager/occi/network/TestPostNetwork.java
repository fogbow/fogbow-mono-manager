package org.fogbowcloud.manager.occi.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderRepository;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPostNetwork {

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
	private OrderRepository orderRepositoryMock;
	
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
		orderRepositoryMock = Mockito.mock(OrderRepository.class);
		
		ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(BASIC_TOKEN, new ArrayList<Order>());
		
		tokenA = new Token("id_one", new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		tokenB = new Token("id_two", new Token.User(BASIC_TOKEN, ""), 
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		//Moking
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN))
				.thenReturn(tokenA);
		Mockito.when(identityPlugin.createToken(Mockito.anyMap()))
				.thenReturn(tokenA);
		
		String basicAuthToken = new String(Base64.decodeBase64(BASIC_TOKEN.replace("Basic ", "")));
		
		Mockito.when(identityPlugin.getToken(basicAuthToken))
				.thenReturn(tokenB);
		
		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(new HashMap<String, String>());
		
		facade = this.helper.initializeComponentNetwork(networkPlugin, identityPlugin, authorizationPlugin, ordersToAdd, mapperPlugin, null);
		
	}

	@After
	public void tearDown() throws Exception {
		File dbFile = new File(INSTANCE_DB_FILE + ".mv.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}				
		this.helper.stopComponent();
	}

	@Test
	public void testPostNetworkOk() throws Exception {

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.240";
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"network; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.DYNAMIC.getValue() + "\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);

		assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		assertNotNull(instanceId);
	}
	
	@Test
	public void testPostNetworkOkStatic() throws Exception {

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.240";
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"network; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.STATIC.getValue() + "\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);

		assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		assertNotNull(instanceId);
	}
	
	@Test
	public void testPostNetworkFailWrongAddress() throws Exception {

		String address = "10.30.0.1A8";
		String gateway = "10.30.10.240";
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"network; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.STATIC.getValue() + "\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostNetworkFailWrongGetway() throws Exception {

		String address = "10.30.0.1/8";
		String gateway = "10.30.AA.240";
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"network; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.STATIC.getValue() + "\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostNetworkFailWrongAllocation() throws Exception {

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.240";
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"network; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OCCIConstants.NETWORK_ALLOCATION + "=\"wrongAllocation\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostNetworkTestAttPersistence() throws Exception {

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.240";
		String allocation = OCCIConstants.NetworkAllocation.DYNAMIC.getValue();
		String label = "Not defined";
		String state = OCCIConstants.NetworkState.INACTIVE.getValue();
		String vlan = "Not defined";
		
		String orderId = "1";
		
		Order order = new Order(orderId, tokenA, new ArrayList<Category>(), new HashMap<String, String>(), true, "local");
		order.setState(OrderState.OPEN);
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(order);
		
		Mockito.when(orderRepositoryMock.getByUserId(Mockito.anyString()))
		.thenReturn(orders);
		Mockito.when(orderRepositoryMock.get(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean())).thenReturn(order);
		Mockito.when(orderRepositoryMock.get(Mockito.anyString())).thenReturn(order);
		
		facade.setOrders(orderRepositoryMock);
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"network; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OCCIConstants.NETWORK_ALLOCATION + "=\"" +allocation+ "\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);

		String[] split = instanceId.split("network/");
		
		instanceId = split[1];
		
		assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		assertNotNull(instanceId);
		
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_NETWORK + instanceId);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpResponse responseGet = client.execute(httpGet);
		
		String responseStr = null;
		responseStr = EntityUtils.toString(responseGet.getEntity(), String.valueOf(Charsets.UTF_8));
		
		Assert.assertEquals("\""+address+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals("\""+allocation+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_ALLOCATION));
		Assert.assertEquals("\""+gateway+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_GATEWAY));
		Assert.assertEquals("\""+label+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_LABEL));
		Assert.assertEquals("\""+state+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_STATE));
		Assert.assertEquals("\""+vlan+"\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_VLAN));
	}
	
	@Test
	public void testPostNetworkFailInvalidResource() throws Exception {

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.240";
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"network_wrong; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OCCIConstants.NETWORK_ALLOCATION + "=\"wrongAllocation\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostNetworkFailWrongResource() throws Exception {

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.240";
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OCCIConstants.NETWORK_ALLOCATION + "=\"wrongAllocation\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	

}
