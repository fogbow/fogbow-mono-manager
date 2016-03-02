package org.fogbowcloud.manager.occi.storage;

import java.util.ArrayList;
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
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDeleteStorageLink {

	private static final String OTHER_ACCESS_TOKEN = "other_token";
	
	public static String STORAGE_LINK_ID_ONE = "SL_ONE";
	public static String STORAGE_LINK_ID_TWO = "SL_TWO";
	public static String STORAGE_LINK_ID_TRHEE = "SL_TRHEE";
	public static String STORAGE_LINK_ID_FOUR = "SL_FOUR";

	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;
	
	@SuppressWarnings("unused")
	private ManagerController facade;
	private StoragePlugin storagePlugin;
	
	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();
		
		storagePlugin = Mockito.mock(StoragePlugin.class);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenTwo = new Token("1", OCCITestHelper.USER_MOCK, new Date(),
		new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(tokenTwo);
		Mockito.when(identityPlugin.getToken(OTHER_ACCESS_TOKEN))
		.thenReturn(tokenTwo);		
		Token otherToken = new Token("other", "other", null, null);
		Mockito.when(identityPlugin.getToken(OTHER_ACCESS_TOKEN)).thenReturn(otherToken);
		Mockito.when(identityPlugin.isValid(OCCITestHelper.ACCESS_TOKEN)).thenReturn(true);	
		
		
		storagePlugin = Mockito.mock(StoragePlugin.class);

		List<Order> orders = new LinkedList<Order>();
		
		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);
		
		List<StorageLink> storageLinks = new ArrayList<StorageLinkRepository.StorageLink>();
		storageLinks.add(new StorageLink(STORAGE_LINK_ID_ONE, "sourceOne", "targetOne", "deviceOne", true));
		storageLinks.add(new StorageLink(STORAGE_LINK_ID_TWO, "sourceTwo", "targetTwo", "deviceTwo", true));
		storageLinks.add(new StorageLink(STORAGE_LINK_ID_TRHEE, "sourceThree", "targetThree", "deviceThree", true));
		storageLinks.add(new StorageLink(STORAGE_LINK_ID_FOUR, "sourceFour", "targetFour", "deviceFour", true));
		
		Map<String, List<StorageLink>> storageLinksToAdd = new HashMap<String, List<StorageLink>>();
		storageLinksToAdd.put(OCCITestHelper.USER_MOCK, storageLinks);			
		
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
				imageStoragePlugin, accountingPlugin, benchmarkingPlugin, ordersToAdd, storageLinksToAdd,
				mapperPlugin);		
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testDeleteSpecificStorageLink() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(4, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE_LINK + STORAGE_LINK_ID_ONE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpGet);

		Assert.assertEquals(3, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());		
	}

	@Test
	public void testDeleteSpecificInstanceOtherUser() throws Exception {		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE_LINK
				+ STORAGE_LINK_ID_ONE + Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OTHER_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceNotFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE_LINK + "wrong"
						+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE_LINK + STORAGE_LINK_ID_ONE
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
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
