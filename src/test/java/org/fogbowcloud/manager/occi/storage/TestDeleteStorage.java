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
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDeleteStorage {

	public static String INSTANCE_ID = "1234567ujhgf45hdb4w";
	public static String OTHER_INSTANCE_ID = "otherInstanceId";

	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;
	
	@SuppressWarnings("unused")
	private ManagerController facade;
	private StoragePlugin storagePlugin;
	
	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();
		Token token = new Token(OCCITestHelper.ACCESS_TOKEN,
				OCCITestHelper.USER_MOCK, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
		
		storagePlugin = Mockito.mock(StoragePlugin.class);
		Mockito.doNothing().when(storagePlugin).removeInstances(token);
		Mockito.doNothing().when(storagePlugin)
				.removeInstance(token, INSTANCE_ID);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenTwo = new Token("1", OCCITestHelper.USER_MOCK, new Date(),
		new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(tokenTwo);

		List<Order> orders = new LinkedList<Order>();
		Order order1 = new Order("1", new Token(OCCITestHelper.ACCESS_TOKEN,
				OCCITestHelper.USER_MOCK, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, null, true, "");
		order1.setInstanceId(INSTANCE_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orders.add(order1);
		Order order2 = new Order("2", new Token("otherToken", "otherUser",
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null, true, "");
		order2.setInstanceId(OTHER_INSTANCE_ID);
		order2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orders.add(order2);

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
		
		facade = this.helper.initializeComponentCompute(null, storagePlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, accountingPlugin, benchmarkingPlugin, ordersToAdd,
				mapperPlugin);
		
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testDelete() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceOtherUser() throws Exception {		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE
				+ OTHER_INSTANCE_ID + Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE + INSTANCE_ID
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
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
