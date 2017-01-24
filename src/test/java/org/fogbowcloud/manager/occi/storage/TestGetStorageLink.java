package org.fogbowcloud.manager.occi.storage;

import java.io.File;
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
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetStorageLink {

	private static final String FAKE_POST_INSTANCE_HOST = "102.102.02.01";
	private static final String FAKE_POST_INSTANCE_PORT = "9001";
	private static final String STORAGE_LINK_INSTANCE_1_ID = "testStorageLinkOne";
	private static final String STORAGE_LINK_INSTANCE_2_ID = "testStorageLinkTwo";
	@SuppressWarnings("unused")
	private static final String ACCESS_TOKEN = "access_token";

	private StoragePlugin storagePlugin;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private OCCITestHelper helper;
	private MapperPlugin mapperPlugin;
	private ManagerController managerController;

	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();
		
		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		Instance instance1 = new Instance(STORAGE_LINK_INSTANCE_1_ID, list, map, null, InstanceState.PENDING);

		Map<String, String> postMap = new HashMap<String, String>();
		postMap.put(Instance.SSH_PUBLIC_ADDRESS_ATT, FAKE_POST_INSTANCE_HOST+":"+FAKE_POST_INSTANCE_PORT);
		postMap.put("test", "test");
		
		computePlugin = Mockito.mock(ComputePlugin.class);
		 
		storagePlugin = Mockito.mock(StoragePlugin.class);
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(STORAGE_LINK_INSTANCE_1_ID)))
				.thenReturn(instance1);		
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(STORAGE_LINK_INSTANCE_1_ID)))
				.thenReturn(instance1);
		Mockito.when(storagePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(STORAGE_LINK_INSTANCE_2_ID)))
				.thenReturn(new Instance(STORAGE_LINK_INSTANCE_2_ID));

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token token = new Token("id", new Token.User(OCCITestHelper.USER_MOCK, ""), 
		new Date(), new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(token);
		Mockito.when(identityPlugin.isValid(OCCITestHelper.ACCESS_TOKEN)).thenReturn(true);				
				
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");

		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		mapperPlugin = Mockito.mock(MapperPlugin.class);

		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, new LinkedList<Order>());
		
		List<StorageLink> storageLinks = new ArrayList<StorageLink>();
		storageLinks.add(new StorageLink("One", "sourceOne", "targetOne", "deviceOne", null, token, true));
		storageLinks.add(new StorageLink("Two", "sourceTwo", "targetTwo", "deviceTwo", null, token, true));
		storageLinks.add(new StorageLink("Three", "sourceThree", "targetThree", "deviceThree", null, token, true));
		storageLinks.add(new StorageLink("Four", "sourceFour", "targetFour", "deviceFour", null, token, true));
		
		Map<String, List<StorageLink>> storageLinksToAdd = new HashMap<String, List<StorageLink>>();
		storageLinksToAdd.put(OCCITestHelper.USER_MOCK, storageLinks);	
		
		managerController = this.helper.initializeComponentCompute(computePlugin, storagePlugin,
				identityPlugin, authorizationPlugin, null, Mockito.mock(AccountingPlugin.class),
				Mockito.mock(BenchmarkingPlugin.class), ordersToAdd, storageLinksToAdd, mapperPlugin);
	}

	@After
	public void tearDown() throws Exception {
		TestDataStorageHelper.clearManagerDataStore(
				this.managerController.getManagerDataStoreController().getManagerDatabase());
		File dbFile = new File(OCCITestHelper.INSTANCE_DB_FILE + ".mv.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}				
		this.helper.stopComponent();
	}

	@Test
	public void testGetStorageLinkOk() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(4, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetStorageLinkWithNotAcceptableContentType() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, "wrong");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificStorageLinkFound() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK
				+ "wrong" + Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificStorageLinkOtherUser() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testAccessToken() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK + STORAGE_LINK_INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testWrongAccessTokenGetStorageLinks() throws Exception {

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}	

}
