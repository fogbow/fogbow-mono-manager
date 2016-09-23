package org.fogbowcloud.manager.occi.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.Response;

public class TestGetCompute {

	private static final String FAKE_POST_INSTANCE_HOST = "102.102.02.01";
	private static final String FAKE_POST_INSTANCE_PORT = "9001";
	private static final String INSTANCE_1_ID = "test1";
	private static final String INSTANCE_2_ID = "test2";
	private static final String INSTANCE_3_ID_WITHOUT_USER = "test3";

	private static final String POST_INSTANCE_1_ID = "postInstance1";
	private static final String POST_INSTANCE_2_ID = "postInstance2";
	private static final String POST_INSTANCE_3_ID = "postInstance3";

	private static final String INSTANCE_DB_FILE = "./src/test/resources/fedInstance.db";
	private static final String INSTANCE_DB_URL = "jdbc:h2:file:" + INSTANCE_DB_FILE;	

	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;
	private MapperPlugin mapperPlugin;
	private InstanceDataStore instanceDB;

	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();

		Token postToken = new Token(OCCITestHelper.POST_FED_ACCESS_TOKEN, new Token.User(OCCITestHelper.USER_MOCK+"_post", "") ,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		Instance instance1 = new Instance(INSTANCE_1_ID, list, map, null, InstanceState.PENDING);

		Map<String, String> postMap = new HashMap<String, String>();
		postMap.put(Instance.SSH_PUBLIC_ADDRESS_ATT, FAKE_POST_INSTANCE_HOST
				+ ":" + FAKE_POST_INSTANCE_PORT);
		postMap.put("test", "test");

		Instance postInstance1 = new Instance(POST_INSTANCE_1_ID, list, postMap, null, InstanceState.RUNNING);
		Instance postInstance2 = new Instance(POST_INSTANCE_2_ID, list, postMap, null, InstanceState.PENDING);

		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_1_ID)))
				.thenReturn(instance1);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_2_ID)))
				.thenReturn(new Instance(INSTANCE_2_ID));
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_3_ID_WITHOUT_USER)))
				.thenReturn(instance1);

		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(POST_INSTANCE_1_ID)))
				.thenReturn(postInstance1);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(POST_INSTANCE_2_ID)))
				.thenReturn(postInstance2);

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(new Token("id", new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(), new HashMap<String, String>()));
		Mockito.when(identityPlugin.getToken(OCCITestHelper.POST_FED_ACCESS_TOKEN))
		.thenReturn(new Token("id", new Token.User(OCCITestHelper.USER_MOCK + "_post", ""), new Date(), new HashMap<String, String>()));
		
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");

		List<Order> ordersA = new LinkedList<Order>();
		List<Order> ordersB = new LinkedList<Order>();
		
		Token token = new Token(OCCITestHelper.ACCESS_TOKEN, new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		HashMap<String, String> xOCCIAttr = new HashMap<String, String>();
		xOCCIAttr.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		Order order1 = new Order("1", token, null, xOCCIAttr, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(order1);
		Order order2 = new Order("2", token, null, xOCCIAttr, true, "");
		order2.setInstanceId(INSTANCE_2_ID);
		order2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(order2);
		Order order3 = new Order("3", new Token("token", new Token.User("user", ""), DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, xOCCIAttr, true, "");
		order3.setInstanceId(INSTANCE_3_ID_WITHOUT_USER);
		order3.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersA.add(order3);
		Order orderPostCompute1 = new Order("Order1", postToken, null, xOCCIAttr, true, "");
		orderPostCompute1.setInstanceId(POST_INSTANCE_1_ID);
		orderPostCompute1.setState(OrderState.FULFILLED);
		orderPostCompute1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersB.add(orderPostCompute1);
		Order orderPostCompute2 = new Order("Order2", postToken, null, xOCCIAttr, true, "");
		orderPostCompute2.setInstanceId(POST_INSTANCE_2_ID);
		orderPostCompute2.setState(OrderState.FULFILLED);
		orderPostCompute2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersB.add(orderPostCompute2);
		Order orderPostCompute3 = new Order("Order3", postToken, null, xOCCIAttr, true, "");
		orderPostCompute3.setInstanceId(null);
		orderPostCompute3.setState(OrderState.OPEN);
		orderPostCompute3.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		ordersB.add(orderPostCompute3);

		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(new HashMap<String, String>());

		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);

		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, ordersA);
		ordersToAdd.put(OCCITestHelper.USER_MOCK+"_post", ordersB);
		
		this.helper.initializeComponentCompute(computePlugin, identityPlugin, identityPlugin, authorizationPlugin, imageStoragePlugin,
				Mockito.mock(AccountingPlugin.class), Mockito.mock(AccountingPlugin.class), Mockito.mock(BenchmarkingPlugin.class), ordersToAdd,
				mapperPlugin);

		instanceDB = new InstanceDataStore(INSTANCE_DB_URL);
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
	public void testGetComputeOk() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(3, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetComputeFiltratedWithAttributes() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));
		List<Resource> resources = new ArrayList<Resource>();
		Map<String, String> attributesOne = new HashMap<String, String>();
		attributesOne.put("occi.compute.cores", "1");
		Map<String, String> attributesTwo = new HashMap<String, String>();
		attributesTwo.put("occi.compute.cores", "2");
		Instance instanceOne = new Instance("One", resources, attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Instance instanceTwo = new Instance("Two", resources, attributesTwo, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Instance instanceThree = new Instance("Three", resources, attributesTwo, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString())).thenReturn(instanceOne,
				instanceTwo, instanceThree);

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, "occi.compute.cores=\"2\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetComputeFiltratedWithNotFoundAttributes() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));
		List<Resource> resources = new ArrayList<Resource>();
		Map<String, String> attributesOne = new HashMap<String, String>();
		attributesOne.put("occi.compute.cores", "1");
		Map<String, String> attributesTwo = new HashMap<String, String>();
		attributesTwo.put("occi.compute.cores", "2");
		Instance instanceOne = new Instance("One", resources, attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Instance instanceTwo = new Instance("Two", resources, attributesTwo, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Instance instanceThree = new Instance("Three", resources, attributesTwo, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString())).thenReturn(instanceOne,
				instanceTwo, instanceThree);

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, "occi.compute.cores=\"2000000\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetComputeFiltratedWithCategory() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));
		List<Resource> resources = new ArrayList<Resource>();
		Category category = new Category("m1-small", "http://schemas.openstack.org/template/resource#", "mixin");
		resources.add(new Resource(category, new ArrayList<String>(), new ArrayList<String>(), "", "", ""));
		List<Resource> resourcesTwo = new ArrayList<Resource>();
		Category categoryTwo = new Category("termwrong", "schemwrong", "classwrong");
		resourcesTwo.add(new Resource(categoryTwo, new ArrayList<String>(), new ArrayList<String>(), "", "", ""));
		Map<String, String> attributesOne = new HashMap<String, String>();
		Instance instanceOne = new Instance("One", resourcesTwo, attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Instance instanceTwo = new Instance("Two", resources, attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Instance instanceThree = new Instance("Three", resources, attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString())).thenReturn(instanceOne,
				instanceTwo, instanceThree);

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.CATEGORY,
				"m1-small; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"");

		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetComputeFiltratedWithWrongCategory() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));
		List<Resource> resources = new ArrayList<Resource>();
		Category category = new Category("m1-small", "http://schemas.openstack.org/template/resource#", "mixin");
		resources.add(new Resource(category, new ArrayList<String>(), new ArrayList<String>(), "", "", ""));
		List<Resource> resourcesTwo = new ArrayList<Resource>();
		Category categoryTwo = new Category("termwrong", "schemwrong", "classwrong");
		resourcesTwo.add(new Resource(categoryTwo, new ArrayList<String>(), new ArrayList<String>(), "", "", ""));
		Map<String, String> attributesOne = new HashMap<String, String>();
		Instance instanceOne = new Instance("One", resourcesTwo, attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Instance instanceTwo = new Instance("Two", resources, attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Instance instanceThree = new Instance("Three", resources, attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.PENDING);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString())).thenReturn(instanceOne,
				instanceTwo, instanceThree);

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpGet.addHeader(OCCIHeaders.CATEGORY,
				"wrong; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"");

		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetComputeOkAcceptURIList() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(3, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}

	@Test
	public void testEmptyGetComputeWithAcceptURIList() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		// reseting component
		helper.stopComponent();
		List<Order> orders = new LinkedList<Order>();
		
		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, orders);
		
		helper.initializeComponentCompute(computePlugin, identityPlugin, identityPlugin, authorizationPlugin, imageStoragePlugin,
				Mockito.mock(AccountingPlugin.class), Mockito.mock(AccountingPlugin.class), Mockito.mock(BenchmarkingPlugin.class), 
				ordersToAdd, null);

		// test
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}

	@Test
	public void testGetSpecificInstanceFound() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceFoundWithWrongAccept() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.METHOD_NOT_ALLOWED, ResponseConstants.METHOD_NOT_SUPPORTED))
				.when(computePlugin).bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID + Order.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceNotFound() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong@member");
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceOtherUser() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_3_ID_WITHOUT_USER
				+ Order.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDifferentContentType() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, "any");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testNotAllowedAcceptContent() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE))
				.when(computePlugin).bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, "invalid-content");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testAccessToken() throws Exception {
		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
//		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
//				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
//		Assert.assertEquals(ResponseConstants.UNAUTHORIZED, EntityUtils.toString(response.getEntity()));
	}

	@Test
	public void testWithoutAccessToken() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
//		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue().startsWith("text/plain"));
//		Assert.assertEquals(ResponseConstants.UNAUTHORIZED, EntityUtils.toString(response.getEntity()));
	}

	@Test
	public void testGetPostComputeOk() throws Exception {

		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeInstanceId_C = "InstanceC";
		String fakeOrderId_A = "Order1";
		String fakeOrderId_B = "Order2";
		String fakeOrderId_C = "Order3";
		String fakeUser = OCCITestHelper.USER_MOCK+"_post";

		List<Category> categories = new ArrayList<Category>();
		List<Link> links = new ArrayList<Link>();

		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, categories, links,
				null, fakeUser);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, categories, links,
				POST_INSTANCE_2_ID + "@" + OCCITestHelper.MEMBER_ID, fakeUser);
		FedInstanceState fedInstanceStateC = new FedInstanceState(fakeInstanceId_C, fakeOrderId_C, categories, links,
				null, fakeUser);
		
		List<FedInstanceState> fakeFedInstanceStateList = new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		fakeFedInstanceStateList.add(fedInstanceStateC);

		instanceDB.insert(fakeFedInstanceStateList);

		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.POST_FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		List<String> intanceIds = OCCITestHelper.getLocationIds(response);

		assertEquals(3, intanceIds.size());
		assertTrue(intanceIds.contains(fakeInstanceId_A));
		assertTrue(intanceIds.contains(fakeInstanceId_B));
		assertTrue(intanceIds.contains(fakeInstanceId_C));
		assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

	}

	@Test
	public void testGetSpecificFedInstanceFound() throws Exception {

		String fakeInstanceId_A = ComputeServerResource.FED_INSTANCE_PREFIX+"InstanceA";
		String fakeOrderId_A = "Order1";
		String fakeUser = OCCITestHelper.USER_MOCK+"_post";
		String fakeInstanceGlobalId = POST_INSTANCE_1_ID + "@" + OCCITestHelper.MEMBER_ID;

		List<Category> categories = new ArrayList<Category>();
		List<Link> links = new ArrayList<Link>();
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, categories, links, 
				"", fakeUser);

		List<FedInstanceState> fakeFedInstanceStateList = new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);

		instanceDB.insert(fakeFedInstanceStateList);

		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + fakeInstanceId_A);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.POST_FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
		Map<String, String> linkAttrs = OCCITestHelper.getLinkAttributes(response);

		assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		assertEquals("\"" + FAKE_POST_INSTANCE_HOST + ":" + FAKE_POST_INSTANCE_PORT + "\"",
				linkAttrs.get("occi.networkinterface.address"));
	}
	
	@Test
	public void testGetSpecificFedInstanceNotReady() throws Exception {

		String fakeInstanceId = ComputeServerResource.FED_INSTANCE_PREFIX+"InstanceA";
		String fakeOrderId = "Order3";
		String fakeUser = OCCITestHelper.USER_MOCK+"_post";

		List<Category> categories = new ArrayList<Category>();
		List<Link> links = new ArrayList<Link>();
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId, fakeOrderId, categories, links, 
				"", fakeUser);

		List<FedInstanceState> fakeFedInstanceStateList = new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);

		instanceDB.insert(fakeFedInstanceStateList);

		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + fakeInstanceId);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.POST_FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		assertEquals("\""+InstanceState.PENDING.getOcciState()+"\"",OCCITestHelper.getOCCIAttByBody(response, "occi.compute.state"));
		
	}
	
	@Test
	public void testGetSpecificFedInstanceNotInDB() throws Exception {

		String fakeInstanceId_A = ComputeServerResource.FED_INSTANCE_PREFIX+"InstanceA";

		Mockito.doNothing().when(computePlugin).bypass(Mockito.any(org.restlet.Request.class),
				Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + fakeInstanceId_A);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.POST_FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
		Map<String, String> linkAttrs = OCCITestHelper.getLinkAttributes(response);

		assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}
}