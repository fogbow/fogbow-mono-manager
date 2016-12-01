package org.fogbowcloud.manager.occi.instance;

import static org.junit.Assert.fail;

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
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.Response;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class TestDeleteCompute {

	public static String INSTANCE_ID = "1234567ujhgf45hdb4w";
	public static String OTHER_INSTANCE_ID = "otherInstanceId";

	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;
	
	private static final String INSTANCE_DB_FILE = "./src/test/resources/fedInstance.db";
	private static final String INSTANCE_DB_URL = "jdbc:h2:file:"+INSTANCE_DB_FILE;
	private InstanceDataStore instanceDB;
	private ManagerController facade;
	private ComputePlugin computePlugin;
	private IdentityPlugin federationIdenityPlugin;
	
	@SuppressWarnings("deprecation")
	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();
		Token token = new Token(OCCITestHelper.ACCESS_TOKEN,
				new Token.User(OCCITestHelper.USER_MOCK, OCCITestHelper.USER_MOCK) , DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
		
		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.doNothing().when(computePlugin).removeInstances(token);
		Mockito.doNothing().when(computePlugin)
				.removeInstance(token, INSTANCE_ID);
		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(computePlugin).bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenTwo = new Token("1", new Token.User(OCCITestHelper.USER_MOCK, OCCITestHelper.USER_MOCK), new Date(),
		new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(tokenTwo);

		List<Order> orders = new LinkedList<Order>();
		HashMap<String, String> xOCCIAttr = new HashMap<String, String>();
		xOCCIAttr.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		Order orderOne = new Order("1", new Token(OCCITestHelper.ACCESS_TOKEN,
				new Token.User(OCCITestHelper.USER_MOCK, OCCITestHelper.USER_MOCK), 
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, xOCCIAttr), null, xOCCIAttr, true, "");
		orderOne.setInstanceId(INSTANCE_ID);
		orderOne.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderOne.setState(OrderState.FULFILLED);
		orders.add(orderOne);
		Order orderTwo = new Order("2", new Token("otherToken", new Token.User("otheruser", "otheruser"),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, xOCCIAttr), null, xOCCIAttr, true, "");
		orderTwo.setInstanceId(OTHER_INSTANCE_ID);
		orderTwo.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orderTwo.setState(OrderState.FULFILLED);
		orders.add(orderTwo);

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
		
		federationIdenityPlugin = Mockito.mock(IdentityPlugin.class);
		
		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, orders);
		
		instanceDB = new InstanceDataStore(INSTANCE_DB_URL);
		facade = this.helper.initializeComponentCompute(computePlugin, identityPlugin, identityPlugin, 
				authorizationPlugin, imageStoragePlugin, accountingPlugin, accountingPlugin, benchmarkingPlugin, ordersToAdd,
				mapperPlugin);		
	}

	@After
	public void tearDown() throws Exception {
		instanceDB.deleteAll();
		File dbFile = new File(INSTANCE_DB_FILE + ".mv.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}				
		this.helper.stopComponent();
	}

	@Test
	public void testDelete() throws Exception {		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteWithRemoteAttachment() throws Exception {	
		StorageLinkRepository storageLinkRepository = new StorageLinkRepository();
		StorageLink storageLink = new StorageLink("id", INSTANCE_ID, "target", "deviceId");
		storageLink.setLocal(false);
		storageLinkRepository.addStorageLink(OCCITestHelper.USER_MOCK, storageLink);
		facade.setStorageLinkRepository(storageLinkRepository);
		
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		facade.setPacketSender(packetSender);
		IQ iq = Mockito.mock(IQ.class);
		Mockito.when(iq.getError()).thenReturn(null);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(Packet.class))).thenReturn(iq);
		
		Assert.assertEquals(1, facade.getStorageLinkRepository().getByUser(OCCITestHelper.USER_MOCK).size());
		
		Token token = new Token("accessId", new Token.User("user", "user"), new Date(), new HashMap<String, String>());
		Mockito.when(federationIdenityPlugin.getToken(Mockito.anyString())).thenReturn(token);
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);
	
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testDeleteWithAttachment() throws Exception {	
		StorageLinkRepository storageLinkRepository = new StorageLinkRepository();
		StorageLink storageLink = new StorageLink("id", INSTANCE_ID, "target", "deviceId");
		storageLink.setLocal(true);
		storageLinkRepository.addStorageLink(OCCITestHelper.USER_MOCK, storageLink);
		facade.setStorageLinkRepository(storageLinkRepository);	
		
		Assert.assertEquals(1, facade.getStorageLinkRepository().getByUser(OCCITestHelper.USER_MOCK).size());
		
		Token token = new Token("accessId", new Token.User("user", "user"), new Date(), new HashMap<String, String>());
		Mockito.when(federationIdenityPlugin.getToken(Mockito.anyString())).thenReturn(token);
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);
	
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void testDeleteSpecificInstanceOtherUser() throws Exception {		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE
				+ OTHER_INSTANCE_ID + Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_ID
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceNotFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_ID
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testAnyContentType() throws Exception {
		HttpDelete get = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "any");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void testDeletePostCompute() throws Exception {
		
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));
		
		String fakeInstanceId_A = ComputeServerResource.FED_INSTANCE_PREFIX+INSTANCE_ID;
		String fakeOrderId_A = "1";
		String fakeUser = OCCITestHelper.USER_MOCK;
		String fakeInstanceGlobalId = INSTANCE_ID + "@" + OCCITestHelper.MEMBER_ID;

		List<Category> categories = new ArrayList<Category>();
		List<Link> links = new ArrayList<Link>();
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, categories, links, 
				fakeInstanceGlobalId, fakeUser);

		List<FedInstanceState> fakeFedInstanceStateList = new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);

		instanceDB.insert(fakeFedInstanceStateList);
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(0, instanceDB.getAllByUser(fakeUser).size());
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void testDeleteSpecificPostCompute() throws Exception {
		
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));
		
		String fakeInstanceId_A = ComputeServerResource.FED_INSTANCE_PREFIX+INSTANCE_ID;
		String fakeOrderId_A = "1";
		String fakeUser = OCCITestHelper.USER_MOCK;
		String fakeInstanceGlobalIdA = INSTANCE_ID + "@" + OCCITestHelper.MEMBER_ID;
		
		String fakeInstanceId_B = ComputeServerResource.FED_INSTANCE_PREFIX+INSTANCE_ID+"_B";
		String fakeOrderId_B = "2";
		String fakeInstanceGlobalIdB = INSTANCE_ID+"_B" + "@" + OCCITestHelper.MEMBER_ID;

		List<Category> categories = new ArrayList<Category>();
		List<Link> links = new ArrayList<Link>();
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, categories, links, 
				fakeInstanceGlobalIdA, fakeUser);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, categories, links, 
				fakeInstanceGlobalIdB, fakeUser);

		List<FedInstanceState> fakeFedInstanceStateList = new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);

		instanceDB.insert(fakeFedInstanceStateList);
		
		Assert.assertNotNull(facade.getOrder(OCCITestHelper.ACCESS_TOKEN, fakeOrderId_A));
		Assert.assertEquals(2, instanceDB.getAllByUser(fakeUser).size());
		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE+fakeInstanceId_A);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(1, instanceDB.getAllByUser(fakeUser).size());
		try {
			facade.getOrder(OCCITestHelper.ACCESS_TOKEN, fakeOrderId_A);
			fail();
		} catch (OCCIException e) {
			
		}
		
	}
}
