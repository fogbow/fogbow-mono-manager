package org.fogbowcloud.manager.occi.storage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPostStorageLink {
	private static final String OTHER_ACCESS_TOKEN = "other_token";

	private static final String INSTANCE_STORAGE_ONE = "Instance_storage_one";
	private static final String INSTANCE_STORAGE_TWO = "Instance_storage_two";

	private static final String INSTANCE_COMPUTE_THREE = "Instance_storage_three";
	private static final String INSTANCE_COMPUTE_FOUR = "Instance_storage_four";

	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;
	
	@SuppressWarnings("unused")
	private ManagerController facade;
	private StoragePlugin storagePlugin;
	private ComputePlugin computePlugin;
	
	@Before
	public void setup() throws Exception {
		TestDataStorageHelper.removeDefaultFolderDataStore();
		this.helper = new OCCITestHelper();
		
		storagePlugin = Mockito.mock(StoragePlugin.class);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenTwo = new Token("1", new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(),
		new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(tokenTwo);
		Mockito.when(identityPlugin.getToken(OTHER_ACCESS_TOKEN))
		.thenReturn(tokenTwo);		
		Token otherToken = new Token("other", new Token.User("other", ""), null, null);
		Mockito.when(identityPlugin.getToken(OTHER_ACCESS_TOKEN)).thenReturn(otherToken);
		Mockito.when(identityPlugin.isValid(OCCITestHelper.ACCESS_TOKEN)).thenReturn(true);	
				
		storagePlugin = Mockito.mock(StoragePlugin.class);
		computePlugin = Mockito.mock(ComputePlugin.class);

		List<Order> orders = new LinkedList<Order>();
		Token tokenUserOne = new Token("accessIdUserOne", new Token.User("userOne", ""), null, null);
		// storage
		HashMap<String, String> xOCCIAttStorage = new HashMap<String, String>();
		xOCCIAttStorage.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.STORAGE_TERM);
		Order orderOne = new Order("One", tokenUserOne, null, xOCCIAttStorage, true, "");
		orderOne.setInstanceId(INSTANCE_STORAGE_ONE);
		orderOne.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderOne.setState(OrderState.FULFILLED);
		orders.add(orderOne);
		Order orderTwo = new Order("Two", tokenUserOne, null, xOCCIAttStorage, true, "");
		orderTwo.setInstanceId(INSTANCE_STORAGE_TWO);
		orderTwo.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderTwo.setState(OrderState.FULFILLED);
		orders.add(orderTwo);
		// compute
		HashMap<String, String> xOCCIAttCompute = new HashMap<String, String>();
		xOCCIAttCompute.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		Order orderThree = new Order("Three", tokenUserOne, null, xOCCIAttCompute, true, "");
		orderThree.setInstanceId(INSTANCE_COMPUTE_THREE);
		orderThree.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderThree.setState(OrderState.FULFILLED);
		orders.add(orderThree);		
		Order orderFour = new Order("Four", tokenUserOne, null, xOCCIAttCompute, true, "");
		orderFour.setInstanceId(INSTANCE_COMPUTE_FOUR);
		orderFour.setState(OrderState.FULFILLED);
		orderFour.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orders.add(orderFour);
		
		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);
		
		List<StorageLink> storageLinks = new ArrayList<StorageLinkRepository.StorageLink>();
		
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
		
		facade = this.helper.initializeComponentCompute(computePlugin, storagePlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, accountingPlugin, benchmarkingPlugin, ordersToAdd, storageLinksToAdd,
				mapperPlugin);		
	}

	@After
	public void tearDown() throws Exception {
		TestDataStorageHelper.removeDefaultFolderDataStore();
		File dbFile = new File(OCCITestHelper.INSTANCE_DB_FILE + ".mv.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}				
		this.helper.stopComponent();
	}

	@Test
	public void testPostStorageLink() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader("Category", OrderConstants.STORAGELINK_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");				
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.SOURCE.getValue() + "=" + INSTANCE_COMPUTE_THREE);
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.TARGET.getValue() + "=" + INSTANCE_STORAGE_ONE);
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.DEVICE_ID.getValue() + "=" + "aaa");				
		
		client = HttpClients.createMinimal();
		response = client.execute(httpPost);

		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		
		httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(httpGet);

		Assert.assertEquals(1, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());		
	}
	
	@Test
	public void testPostStorageLinkBadRequestWithoutTargetAttr() throws Exception {
		
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader("Category", OrderConstants.STORAGELINK_TERM + "; scheme=\""
				+ OrderConstants.INFRASTRUCTURE_OCCI_SCHEME + "\"; class=\"" + OrderConstants.KIND_CLASS
				+ "\"");				
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.SOURCE.getValue() + "=" + INSTANCE_COMPUTE_THREE);
		httpPost.addHeader("X-OCCI-Attribute",
				StorageAttribute.DEVICE_ID.getValue() + "=" + "aaa");				
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());	
	}	
	
	@Test
	public void testNotAcceptHeader() throws ClientProtocolException, IOException {
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_STORAGE_LINK);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.ACCEPT, "wrong");
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		
		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}

}
