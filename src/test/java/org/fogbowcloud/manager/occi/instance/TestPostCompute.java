package org.fogbowcloud.manager.occi.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class TestPostCompute {
	
	private static final String FED_COMPUTE_ATT_PUBLICKEY_NAME = "org.openstack.credentials.publickey.data org.openstack.credentials.publickey.name";
	private static final String FED_COMPUTE_USER_DATA = "org.openstack.compute.user_data";
	
	private static final String INSTANCE_1_ID = "test1";
	private static final String INSTANCE_2_ID = "test2";
	private static final String INSTANCE_3_ID_WITHOUT_USER = "test3";

	private IdentityPlugin identityPlugin;
	private OCCITestHelper helper;
	private ImageStoragePlugin imageStoragePlugin;
	private LocalCredentialsPlugin localCredentialsPlugin;

	@Rule
	public final ExpectedException exception = ExpectedException.none();
	
	@Before
	public void setup() throws Exception {

		this.helper = new OCCITestHelper();

		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
				Mockito.any(Map.class), Mockito.anyString())).thenReturn("");

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.FED_ACCESS_TOKEN))
				.thenReturn(new Token("id", OCCITestHelper.USER_MOCK, new Date(), new HashMap<String, String>()));
		Mockito.when(identityPlugin.getToken(OCCITestHelper.INVALID_TOKEN))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

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

		this.helper.initializeComponentCompute(computePlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, Mockito.mock(AccountingPlugin.class), Mockito.mock(BenchmarkingPlugin.class),
				requestsToAdd, localCredentialsPlugin);

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

	@Test
	public void testConvertUserData() throws Exception {

		String fakeScriptEncoded = new String(Base64.encodeBase64(
				"#!/bin/sh echo \"Hello World.  The time is now $(date -R)!\" | tee /root/output.txt".getBytes()));


		Resource occiUserdataResource = new Resource("user_data; scheme=\"http://schemas.openstack.org/compute/instance#\"; class=\"mixin\"");

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + RequestConstants.USER_DATA_TERM,
				"user_data");
		properties.put(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + RequestAttribute.EXTRA_USER_DATA_ATT.getValue(),
				FED_COMPUTE_USER_DATA);
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(occiUserdataResource);
		List<Category> requestCategories = new ArrayList<Category>();
		Map<String, String> requestXOCCIAtt = new HashMap<String, String>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(FED_COMPUTE_USER_DATA, fakeScriptEncoded);

		ComputeServerResource csr = new ComputeServerResource();
		csr.convertUserData(properties, resources, requestCategories, requestXOCCIAtt,
				xOCCIAtt);
		
		assertEquals(1, requestCategories.size());
		assertEquals(new Category(RequestConstants.USER_DATA_TERM, RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS), requestCategories.get(0));
		assertEquals(2, requestXOCCIAtt.size());
		assertEquals(fakeScriptEncoded, requestXOCCIAtt.get(RequestAttribute.EXTRA_USER_DATA_ATT.getValue()));
		assertEquals(MIMEMultipartArchive.SHELLSCRIPT.getType(), requestXOCCIAtt.get(RequestAttribute.EXTRA_USER_DATA_CONTENT_TYPE_ATT.getValue()));
	}
	
	@Test
	public void testConvertUserDataBadRequest() throws Exception {

		exception.expect(OCCIException.class);

		Resource occiUserdataResource = new Resource("user_data; scheme=\"http://schemas.openstack.org/compute/instance#\"; class=\"mixin\"");

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + RequestConstants.USER_DATA_TERM,
				"user_data");
		properties.put(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + RequestAttribute.EXTRA_USER_DATA_ATT.getValue(),
				FED_COMPUTE_USER_DATA);
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(occiUserdataResource);
		List<Category> requestCategories = new ArrayList<Category>();
		Map<String, String> requestXOCCIAtt = new HashMap<String, String>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();

		ComputeServerResource csr = new ComputeServerResource();
		csr.convertUserData(properties, resources, requestCategories, requestXOCCIAtt,
				xOCCIAtt);
		
	}

	@Test
	public void testConvertPublicKey() throws Exception {

		
		String fakePublicKey = "org.openstack.credentials.publickey.data org.openstack.credentials.publickey.name=ssh-rsa "
				+ "AAAAB3NzaC1yc2EAAAADAQABAAABAQDI6g9Q7epXV1ciIsPHin";
		
		Resource fogbowPublicKey = new Resource("public_key; scheme=\"http://schemas.openstack.org/instance/credentials#\"; class=\"mixin\"");	

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + RequestConstants.PUBLIC_KEY_TERM,
				"public_key");
		properties.put(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + RequestAttribute.DATA_PUBLIC_KEY.getValue(),
				FED_COMPUTE_ATT_PUBLICKEY_NAME);

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(fogbowPublicKey);
		List<Category> requestCategories = new ArrayList<Category>();
		Map<String, String> requestXOCCIAtt = new HashMap<String, String>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(FED_COMPUTE_ATT_PUBLICKEY_NAME, fakePublicKey);

		
		ComputeServerResource csr = new ComputeServerResource();
		csr.convertPublicKey(properties, resources, requestCategories, requestXOCCIAtt,
				xOCCIAtt);
		
		assertEquals(1, requestCategories.size());
		assertEquals(RequestConstants.PUBLIC_KEY_TERM, requestCategories.get(0).getTerm());
		assertEquals(1, requestXOCCIAtt.size());
		assertEquals(fakePublicKey, requestXOCCIAtt.get(RequestAttribute.DATA_PUBLIC_KEY.getValue()));

	}
	
	@Test
	public void testConvertPublicKeyBadRequest() throws Exception {
		
		exception.expect(OCCIException.class);
		
		Resource fogbowPublicKey = new Resource("public_key; scheme=\"http://schemas.openstack.org/instance/credentials#\"; class=\"mixin\"");	

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + RequestConstants.PUBLIC_KEY_TERM,
				"public_key");
		properties.put(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + RequestAttribute.DATA_PUBLIC_KEY.getValue(),
				FED_COMPUTE_ATT_PUBLICKEY_NAME);

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(fogbowPublicKey);
		List<Category> requestCategories = new ArrayList<Category>();
		Map<String, String> requestXOCCIAtt = new HashMap<String, String>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		
		ComputeServerResource csr = new ComputeServerResource();
		csr.convertPublicKey(properties, resources, requestCategories, requestXOCCIAtt,
				xOCCIAtt);

	}
	
	
//	@Test
//	public void testBypassPostComputeWithWrongMediaTypeTextPlain() throws URISyntaxException, HttpException, IOException {
//		//post compute through fogbow endpoint
//		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
//		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, "invalid-type");
//		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, PluginHelper.ACCESS_ID);
//		httpPost.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
//				OCCIComputeApplication.OS_SCHEME, RequestConstants.MIXIN_CLASS).toHeader());
//		
//		HttpClient client = HttpClients.createMinimal();
//		HttpResponse response = client.execute(httpPost);
//
//		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
//	}
//	
//	@Test
//	public void testBypassPostComputeWithoutMediaType() throws URISyntaxException, HttpException, IOException {
//		//post compute through fogbow endpoint
//		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
//		httpPost.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, PluginHelper.ACCESS_ID);
//		httpPost.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
//				OCCIComputeApplication.OS_SCHEME, RequestConstants.MIXIN_CLASS).toHeader());
//		
//		HttpClient client = HttpClients.createMinimal();
//		HttpResponse response = client.execute(httpPost);
//
//		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
//	}

}
