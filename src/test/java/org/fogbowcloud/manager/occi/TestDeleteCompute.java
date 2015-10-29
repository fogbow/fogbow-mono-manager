package org.fogbowcloud.manager.occi;

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
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
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

public class TestDeleteCompute {

	public static String INSTANCE_ID = "1234567ujhgf45hdb4w";
	public static String OTHER_INSTANCE_ID = "otherInstanceId";

	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;

	@SuppressWarnings("deprecation")
	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();
		Token token = new Token(OCCITestHelper.FED_ACCESS_TOKEN,
				OCCITestHelper.USER_MOCK, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.doNothing().when(computePlugin).removeInstances(token);
		Mockito.doNothing().when(computePlugin)
				.removeInstance(token, INSTANCE_ID);
		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(computePlugin).bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenTwo = new Token("1", OCCITestHelper.USER_MOCK, new Date(),
		new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(OCCITestHelper.FED_ACCESS_TOKEN))
				.thenReturn(tokenTwo);

		List<Request> requests = new LinkedList<Request>();
		Request request1 = new Request("1", new Token(OCCITestHelper.FED_ACCESS_TOKEN,
				OCCITestHelper.USER_MOCK, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, null, true, "");
		request1.setInstanceId(INSTANCE_ID);
		request1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		requests.add(request1);
		Request request2 = new Request("2", new Token("otherToken", "otherUser",
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null, true, "");
		request2.setInstanceId(OTHER_INSTANCE_ID);
		request2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		requests.add(request2);

		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);
		
		AccountingPlugin accountingPlugin = Mockito.mock(AccountingPlugin.class);
		BenchmarkingPlugin benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		
		LocalCredentialsPlugin localCredentialsPlugin = Mockito
				.mock(LocalCredentialsPlugin.class);
		Map<String, String> crendentials = new HashMap<String, String>();
		Mockito.when(
				localCredentialsPlugin.getLocalCredentials(Mockito.any(Request.class)))
				.thenReturn(crendentials);
		Mockito.when(identityPlugin.createToken(crendentials)).thenReturn(tokenTwo);
		
		Map<String, List<Request>> requestsToAdd = new HashMap<String, List<Request>>();
		requestsToAdd.put(OCCITestHelper.USER_MOCK, requests);
		
		this.helper.initializeComponentCompute(computePlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, accountingPlugin, benchmarkingPlugin, requestsToAdd,
				localCredentialsPlugin);
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testDelete() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceOtherUser() throws Exception {		
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE
				+ OTHER_INSTANCE_ID + Request.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_ID
				+ Request.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceNotFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_ID
				+ Request.SEPARATOR_GLOBAL_ID + OCCITestHelper.MEMBER_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, "");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testAnyContentType() throws Exception {
		HttpDelete get = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "any");
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
}
