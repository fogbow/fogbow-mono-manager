package org.fogbowcloud.manager.occi.network;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.ManagerDataStoreController;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
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
import org.mockito.internal.verification.VerificationModeFactory;

public class TestDeleteNetwork {

	private static final String USER_WITHOUT_ORDERS = "withoutInstances";
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
		ordersToAdd.put(USER_WITHOUT_ORDERS, new ArrayList<Order>());
		
		tokenA = new Token("id_one", new Token.User(OCCITestHelper.USER_MOCK, OCCITestHelper.USER_MOCK), 
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		tokenB = new Token("id_two", new Token.User(USER_WITHOUT_ORDERS, USER_WITHOUT_ORDERS), 
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		//Moking
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN))
				.thenReturn(tokenA);
		Mockito.when(identityPlugin.createToken(Mockito.anyMap()))
				.thenReturn(tokenA);		
		Mockito.when(identityPlugin.getToken(USER_WITHOUT_ORDERS))
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
		TestDataStorageHelper.clearManagerDataStore(facade.getManagerDataStoreController().getManagerDatabase());
		File dbFile = new File(INSTANCE_DB_FILE + ".mv.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}				
		this.helper.stopComponent();
	}

	
	@Test
	public void testDeleteNetworkOkWithId() throws Exception {
		
		String INSTANCE_1_ID = "network01";
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		
		List<Order> userOrders = new ArrayList<Order>();
		Order order1 = new Order("1", tokenA, null, xOCCIAtt, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		userOrders.add(order1);
		
		ManagerController managerController = new ManagerController(new Properties());
		ManagerDataStoreController orders = managerController.getManagerDataStoreController();
		for (Order order : userOrders){
			orders.addOrder(order);
		}
	
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_NETWORK + INSTANCE_1_ID + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Mockito.verify(networkPlugin, VerificationModeFactory.times(1)).removeInstance(tokenA, INSTANCE_1_ID);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

	}
	
	@Test
	public void testDeleteNetworkOkWithouId() throws Exception {
		
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
		HttpResponse responseGet = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(responseGet).size());
		Assert.assertEquals(HttpStatus.SC_OK, responseGet.getStatusLine().getStatusCode());
	
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_NETWORK);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpResponse responseDelete = client.execute(httpDelete);

		Mockito.verify(networkPlugin, VerificationModeFactory.times(1)).removeInstance(tokenA, INSTANCE_1_ID);
		Mockito.verify(networkPlugin, VerificationModeFactory.times(1)).removeInstance(tokenA, INSTANCE_2_ID);
		Mockito.verify(networkPlugin, VerificationModeFactory.times(0)).removeInstance(tokenB, INSTANCE_3_ID);
		Assert.assertEquals(HttpStatus.SC_OK, responseDelete.getStatusLine().getStatusCode());
		
		responseGet = client.execute(httpGet);
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(responseGet).size());
	}
	
	@Test
	public void testDeleteNetworkNotOkInvalidId() throws Exception {
		
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
	
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_NETWORK + INSTANCE_1_ID+"Invalid" + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());

	}
	
	@Test
	public void testDeleteNetworkNotOkUnathorized() throws Exception {
		
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
	
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_NETWORK + INSTANCE_1_ID+"Invalid" + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN+"Unauthorized");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());

	}
	
}
