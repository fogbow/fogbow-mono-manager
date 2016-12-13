package org.fogbowcloud.manager.occi.storage;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class TestDeleteStorage {

	private static final String INSTANCE_ID_THREE = "u4gtffgcp1dijvbh9";
	public static String INSTANCE_ID = "1234567ujhgf45hdb4w";
	public static String INSTANCE_ID_TWO = "çljuokebvpdhqodkjh";
	public static String FED_STORAGE_PREFIX = "federated_storage_";

	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;
	
	private ManagerController facade;
	private StoragePlugin storagePlugin;
	private IdentityPlugin federationIdenityPlugin;
	
	@Before
	public void setup() throws Exception {
		TestDataStorageHelper.removeDefaultFolderDataStore();
		
		this.helper = new OCCITestHelper();
		Token token = new Token(OCCITestHelper.ACCESS_TOKEN,
				new Token.User(OCCITestHelper.USER_MOCK, ""), DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
		
		storagePlugin = Mockito.mock(StoragePlugin.class);
		Mockito.doNothing().when(storagePlugin).removeInstances(token);
		Mockito.doNothing().when(storagePlugin).removeInstance(token, INSTANCE_ID);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenTwo = new Token("1", new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(),
				new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(tokenTwo);

		List<Order> orders = new LinkedList<Order>();
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.STORAGE_TERM);
		
		Order orderOne = new Order("orderIdOne", new Token(OCCITestHelper.ACCESS_TOKEN,
				new Token.User(OCCITestHelper.USER_MOCK, ""), DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				attributes), null, attributes, true, "");
		orderOne.setInstanceId(INSTANCE_ID);
		orderOne.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderOne.setState(OrderState.FULFILLED);
		orders.add(orderOne);
		Order orderTwo = new Order("orderIdTwo", new Token("otherToken", new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, attributes), null, attributes, true, "");
		orderTwo.setInstanceId(INSTANCE_ID_TWO);
		orderTwo.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderTwo.setState(OrderState.FULFILLED);
		orders.add(orderTwo);
		Order orderThree = new Order("orderIdTrhee", new Token("otherTokenThree", new Token.User("Other", ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, attributes), null, attributes, true, "");
		orderThree.setInstanceId(INSTANCE_ID_THREE);
		orderThree.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderThree.setState(OrderState.FULFILLED);
		orders.add(orderThree);
		
		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);
		
		AccountingPlugin accountingPlugin = Mockito.mock(AccountingPlugin.class);
		BenchmarkingPlugin benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		
		MapperPlugin mapperPlugin = Mockito
				.mock(MapperPlugin.class);
		Map<String, String> crendentials = new HashMap<String, String>();
		Mockito.when(
				mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(crendentials);
		Mockito.when(identityPlugin.createToken(crendentials)).thenReturn(tokenTwo);
		
		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, orders);
		
		federationIdenityPlugin = Mockito.mock(IdentityPlugin.class);
		
		facade = this.helper.initializeComponentCompute(null, storagePlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, accountingPlugin, benchmarkingPlugin, ordersToAdd,
				mapperPlugin);
		
		facade.setFederationIdentityPlugin(federationIdenityPlugin);
		Mockito.when(federationIdenityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN)).thenReturn(tokenTwo);
	}

	@After
	public void tearDown() throws Exception {
		TestDataStorageHelper.removeDefaultFolderDataStore();
		this.helper.stopComponent();
	}

	@Test
	public void testDelete() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
				
	}

	@Test
	public void testDeleteSpecificInstanceOtherUser() throws Exception {		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());		
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE
				+ INSTANCE_ID_THREE + Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceFound() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());		
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_ID
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpGet);

		Assert.assertEquals(1, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());		
	}
	
	@Test
	public void testDeleteSpecificInstanceFoundWithAttachment() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
		
		Token token = new Token("accessId", new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(), null);
		StorageLink storageLink = new StorageLink("id", "source", INSTANCE_ID, "deviceId", null, token, false);
		facade.getManagerDataStoreController().addStorageLink(storageLink);
		
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		facade.setPacketSender(packetSender);
		IQ iq = Mockito.mock(IQ.class);
		Mockito.when(iq.getError()).thenReturn(null);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(Packet.class))).thenReturn(iq);
		
		Assert.assertEquals(1, facade.getManagerDataStoreController().getStorageLinksByUser(OCCITestHelper.USER_MOCK).size());		
		
		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());		
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_ID
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpDelete);
	
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());		
	}	

	@Test
	public void testDeleteSpecificInstanceNotFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE + "wrong");
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_ID
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testAnyContentType() throws Exception {
		HttpDelete get = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "any");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
}
