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
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
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

	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();

		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		Instance instance1 = new Instance(INSTANCE_1_ID, list, map, null);

		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.eq(INSTANCE_1_ID)))
				.thenReturn(instance1);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.eq(INSTANCE_2_ID)))
				.thenReturn(new Instance(INSTANCE_2_ID));
		Mockito.when(
				computePlugin.getInstance(Mockito.anyString(),
						Mockito.eq(INSTANCE_3_ID_WITHOUT_USER))).thenReturn(instance1);
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN)).thenReturn(
				new Token("id", OCCITestHelper.USER_MOCK, new Date(),
				new HashMap<String, String>()));

		List<Request> requests = new LinkedList<Request>();
		Request request1 = new Request("1", new Token(OCCITestHelper.ACCESS_TOKEN,
				OCCITestHelper.USER_MOCK, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, null);
		request1.setInstanceId(INSTANCE_1_ID);
		requests.add(request1);
		Request request2 = new Request("2", new Token(OCCITestHelper.ACCESS_TOKEN,
				OCCITestHelper.USER_MOCK, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, null);
		request2.setInstanceId(INSTANCE_2_ID);
		requests.add(request2);
		Request request3 = new Request("3", new Token("token", "user",
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null);
		request3.setInstanceId(INSTANCE_3_ID_WITHOUT_USER);
		requests.add(request3);

		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAutorized(Mockito.any(Token.class))).thenReturn(true);
		
		this.helper.initializeComponentCompute(computePlugin, identityPlugin ,authorizationPlugin , requests);
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testGetComputeOk() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(3, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetComputeOkAcceptURIList() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(3, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}
	
	@Test
	public void testEmptyGetComputeWithAcceptURIList() throws Exception {
		//reseting component
		helper.stopComponent();
		List<Request> requests = new LinkedList<Request>();
		helper.initializeComponentCompute(computePlugin, identityPlugin, authorizationPlugin, requests);

		//test
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}

	@Test
	public void testGetSpecificInstanceFound() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetSpecificInstanceFoundWithWrongAccept() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceNotFound() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificInstanceOtherUser() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE
				+ INSTANCE_3_ID_WITHOUT_USER);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDifferentContentType() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, "any");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testNotAllowedAcceptContent() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.ACCEPT, "invalid-content");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = new DefaultHttpClient();
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
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = new DefaultHttpClient();
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