package org.fogbowcloud.manager.occi;

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
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.Response;

public class TestGetCompute {

	private static final String INSTANCE_1_ID = "test1";
	private static final String INSTANCE_2_ID = "test2";
	private static final String INSTANCE_3_ID_WITHOUT_USER = "test3";

	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;

	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();

		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		Instance instance1 = new Instance(INSTANCE_1_ID, list, map, null, InstanceState.PENDING);

		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_1_ID)))
				.thenReturn(instance1);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(INSTANCE_2_ID)))
				.thenReturn(new Instance(INSTANCE_2_ID));
		Mockito.when(
				computePlugin.getInstance(Mockito.any(Token.class),
						Mockito.eq(INSTANCE_3_ID_WITHOUT_USER))).thenReturn(instance1);
		
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.FED_ACCESS_TOKEN)).thenReturn(
				new Token("id", OCCITestHelper.USER_MOCK, new Date(),
				new HashMap<String, String>()));
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");

		List<Request> requests = new LinkedList<Request>();
		Token token = new Token(OCCITestHelper.FED_ACCESS_TOKEN,
				OCCITestHelper.USER_MOCK, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
		Request request1 = new Request("1", token, null, null, true, "");
		request1.setInstanceId(INSTANCE_1_ID);
		request1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		requests.add(request1);
		Request request2 = new Request("2", token, null, null, true, "");
		request2.setInstanceId(INSTANCE_2_ID);
		request2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		requests.add(request2);
		Request request3 = new Request("3", new Token("token", "user",
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null, true, "");
		request3.setInstanceId(INSTANCE_3_ID_WITHOUT_USER);
		request3.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		requests.add(request3);

		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);
		
		this.helper.initializeComponentCompute(computePlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, Mockito.mock(AccountingPlugin.class),
				Mockito.mock(BenchmarkingPlugin.class), requests);
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testGetComputeOk() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(3, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetComputeFiltratedWithAttributes() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		List<Resource> resources = new ArrayList<Resource>();
		Map<String, String> attributesOne = new HashMap<String, String>();
		attributesOne.put("occi.compute.cores", "1");
		Map<String, String> attributesTwo = new HashMap<String, String>();
		attributesTwo.put("occi.compute.cores", "2");
		Instance instanceOne = new Instance("One", resources, attributesOne,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Instance instanceTwo = new Instance("Two", resources, attributesTwo,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Instance instanceThree = new Instance("Three", resources, attributesTwo,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString()))
				.thenReturn(instanceOne, instanceTwo, instanceThree);		
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);		
		httpGet.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, "occi.compute.cores=\"2\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void testGetComputeFiltratedWithNotFoundAttributes() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		List<Resource> resources = new ArrayList<Resource>();
		Map<String, String> attributesOne = new HashMap<String, String>();
		attributesOne.put("occi.compute.cores", "1");
		Map<String, String> attributesTwo = new HashMap<String, String>();
		attributesTwo.put("occi.compute.cores", "2");
		Instance instanceOne = new Instance("One", resources, attributesOne,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Instance instanceTwo = new Instance("Two", resources, attributesTwo,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Instance instanceThree = new Instance("Three", resources, attributesTwo,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString()))
				.thenReturn(instanceOne, instanceTwo, instanceThree);		
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);		
		httpGet.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, "occi.compute.cores=\"2000000\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}		

	@Test
	public void testGetComputeFiltratedWithCategory() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		List<Resource> resources = new ArrayList<Resource>();
		Category category = new Category("m1-small",
				"http://schemas.openstack.org/template/resource#", "mixin");
		resources.add(new Resource(category, new ArrayList<String>(), new ArrayList<String>(), "",
				"", ""));
		List<Resource> resourcesTwo = new ArrayList<Resource>();
		Category categoryTwo = new Category("termwrong", "schemwrong", "classwrong");
		resourcesTwo.add(new Resource(categoryTwo, new ArrayList<String>(), new ArrayList<String>(), "",
				"", ""));
		Map<String, String> attributesOne = new HashMap<String, String>();
		Instance instanceOne = new Instance("One", resourcesTwo, attributesOne,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Instance instanceTwo = new Instance("Two", resources, attributesOne,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Instance instanceThree = new Instance("Three", resources, attributesOne,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);		
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString()))
				.thenReturn(instanceOne, instanceTwo, instanceThree);		
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);		
		httpGet.addHeader(OCCIHeaders.CATEGORY,
				"m1-small; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"");
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void testGetComputeFiltratedWithWrongCategory() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		List<Resource> resources = new ArrayList<Resource>();
		Category category = new Category("m1-small",
				"http://schemas.openstack.org/template/resource#", "mixin");
		resources.add(new Resource(category, new ArrayList<String>(), new ArrayList<String>(), "",
				"", ""));
		List<Resource> resourcesTwo = new ArrayList<Resource>();
		Category categoryTwo = new Category("termwrong", "schemwrong", "classwrong");
		resourcesTwo.add(new Resource(categoryTwo, new ArrayList<String>(), new ArrayList<String>(), "",
				"", ""));
		Map<String, String> attributesOne = new HashMap<String, String>();
		Instance instanceOne = new Instance("One", resourcesTwo, attributesOne,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Instance instanceTwo = new Instance("Two", resources, attributesOne,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);
		Instance instanceThree = new Instance("Three", resources, attributesOne,
				new ArrayList<Instance.Link>(), InstanceState.PENDING);		
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString()))
				.thenReturn(instanceOne, instanceTwo, instanceThree);		
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);		
		httpGet.addHeader(OCCIHeaders.CATEGORY,
				"wrong; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"");
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}		
	
	@Test
	public void testGetComputeOkAcceptURIList() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(3, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}
	
	@Test
	public void testEmptyGetComputeWithAcceptURIList() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		//reseting component
		helper.stopComponent();
		List<Request> requests = new LinkedList<Request>();
		helper.initializeComponentCompute(computePlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, Mockito.mock(AccountingPlugin.class),
				Mockito.mock(BenchmarkingPlugin.class), requests);

		//test
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}

	@Test
	public void testGetSpecificInstanceFound() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID
				+ Request.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetSpecificInstanceFoundWithWrongAccept() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.METHOD_NOT_ALLOWED,
						ResponseConstants.METHOD_NOT_SUPPORTED)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID
				+ Request.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceNotFound() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND,
						ResponseConstants.NOT_FOUND)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong@member");
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceOtherUser() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE
				+ INSTANCE_3_ID_WITHOUT_USER + Request.SEPARATOR_GLOBAL_ID
				+ OCCITestHelper.MEMBER_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDifferentContentType() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, "any");
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testNotAllowedAcceptContent() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.NOT_ACCEPTABLE,
						ResponseConstants.ACCEPT_NOT_ACCEPTABLE)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, "invalid-content");
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testAccessToken() throws Exception {
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED,
						ResponseConstants.UNAUTHORIZED)).when(computePlugin)
					.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED,
						ResponseConstants.UNAUTHORIZED)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, "");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(ResponseConstants.UNAUTHORIZED,
				EntityUtils.toString(response.getEntity()));
	}
	
	@Test
	public void testWithoutAccessToken() throws Exception {
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED,
						ResponseConstants.UNAUTHORIZED)).when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));	
		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith("text/plain"));
		Assert.assertEquals(ResponseConstants.UNAUTHORIZED,
				EntityUtils.toString(response.getEntity()));
	}
}
