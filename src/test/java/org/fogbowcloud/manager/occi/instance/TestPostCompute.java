package org.fogbowcloud.manager.occi.instance;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.instance.InstanceDataStore;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.OrderDataStore;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.util.Series;

public class TestPostCompute {

	private static final String NULL_VALUE = "null";
	private static final String CATEGORY = "Category";
	private static final String X_OCCI_ATTRIBUTE = "X-OCCI-Attribute";
	private static final String DEFAULT_USER = "user";
	
	private static final String INSTANCE_1_ID = "test1";
	private static final String INSTANCE_2_ID = "test2";
	private static final String INSTANCE_3_ID_WITHOUT_USER = "test3";

	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;
	private LocalCredentialsPlugin localCredentialsPlugin;
	private InstanceDataStore instanceDB;
	private ManagerController facade;

	@Before
	public void setup() throws Exception {

		this.helper = new OCCITestHelper();

		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(Token.class), 
				Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString()))
				.thenReturn("");

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.FED_ACCESS_TOKEN)).thenReturn(
				new Token("id", OCCITestHelper.USER_MOCK, new Date(), 
				new HashMap<String, String>()));
		Mockito.when(identityPlugin.getToken(OCCITestHelper.INVALID_TOKEN)).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		List<Request> requests = new LinkedList<Request>();
		Token token = new Token(OCCITestHelper.FED_ACCESS_TOKEN, OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		Request request1 = new Request("1", token, null, null, true, "");
		request1.setInstanceId(INSTANCE_1_ID);
		request1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		requests.add(request1);
		Request request2 = new Request("2", token, null, null, true, "");
		request2.setInstanceId(INSTANCE_2_ID);
		request2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		requests.add(request2);
		Request request3 = new Request("3", new Token("token", "user", DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, null, true, "");
		request3.setInstanceId(INSTANCE_3_ID_WITHOUT_USER);
		request3.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		requests.add(request3);

		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		localCredentialsPlugin = Mockito.mock(LocalCredentialsPlugin.class);
		Mockito.when(localCredentialsPlugin.getLocalCredentials(Mockito.any(Request.class)))
				.thenReturn(new HashMap<String, String>());

		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);

		Map<String, List<Request>> requestsToAdd = new HashMap<String, List<Request>>();
		requestsToAdd.put(OCCITestHelper.USER_MOCK, requests);
		
		facade = this.helper.initializeComponentCompute(computePlugin, identityPlugin, authorizationPlugin, imageStoragePlugin,
				Mockito.mock(AccountingPlugin.class), Mockito.mock(BenchmarkingPlugin.class), requestsToAdd,
				localCredentialsPlugin);
		

	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}
	
	@Test
	public void testPostComputeAcceptOcciOk() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"fbc85206-fbcc-4ad9-ae93-54946fdd5df7; scheme=\"http://schemas.openstack.org/template/os#\"; class=\"mixin\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"m1-medium; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, "occi.core.title=\"Title\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);
		
		assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		assertNotNull(instanceId);
		assertTrue(OCCITestHelper.getLocationIds(response).isEmpty());
		
		
	}
	
	@Test
	public void testPostComputeTextPlainOk() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"fbc85206-fbcc-4ad9-ae93-54946fdd5df7; scheme=\"http://schemas.openstack.org/template/os#\"; class=\"mixin\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"m1-medium; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"; ");
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, "occi.core.title=\"Title\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		String instanceIdHead = OCCITestHelper.getInstanceIdPerLocationHeader(response);
		String instanceIdBody = OCCITestHelper.getLocationIds(response).get(0);
		
		assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		assertNull(instanceIdHead);
		assertNotNull(instanceIdBody);
		
		
	}
	
	
	
	@Test
	public void testPostComputeWrongComputeFailed() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"compute; scheme=\"http://schemas.ogf.org/occi/wrong#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"fbc85206-fbcc-4ad9-ae93-54946fdd5df7; scheme=\"http://schemas.openstack.org/template/os#\"; class=\"mixin\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"m1-medium; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"; ");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);
		assertNull(instanceId);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostComputeNoComputeFailed() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"fbc85206-fbcc-4ad9-ae93-54946fdd5df7; scheme=\"http://schemas.openstack.org/template/os#\"; class=\"mixin\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"m1-medium; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"; ");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);
		assertNull(instanceId);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostComputeNoImageFailed() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"m1-medium; scheme=\"http://schemas.openstack.org/template/resource#\"; class=\"mixin\"; ");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);
		assertNull(instanceId);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostComputeNoRelativeResourceFailed() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"new_os_resource; scheme=\"http://schemas.openstack.org/template/os#\"; class=\"mixin\"; ");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);
		assertNull(instanceId);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testPostComputeNoMappedImageFailed() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, OCCITestHelper.FED_ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; ");
		httpPost.addHeader(OCCIHeaders.CATEGORY,
				"373c16db-7784-43cf-bba9-c05a6f77c77d; scheme=\"http://schemas.openstack.org/template/os#\"; class=\"mixin\"; ");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpPost);
		String instanceId = OCCITestHelper.getInstanceIdPerLocationHeader(response);
		assertNull(instanceId);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

}
