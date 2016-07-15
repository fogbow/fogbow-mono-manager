package org.fogbowcloud.manager.occi.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
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
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
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
	private MapperPlugin mapperPlugin;

	@Rule
	public final ExpectedException exception = ExpectedException.none();
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();

		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
				Mockito.any(Map.class), Mockito.anyString())).thenReturn("");

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(new Token("id", OCCITestHelper.USER_MOCK, new Date(), new HashMap<String, String>()));
		Mockito.when(identityPlugin.getToken(OCCITestHelper.INVALID_TOKEN))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		List<Order> orders = new LinkedList<Order>();
		Token token = new Token(OCCITestHelper.ACCESS_TOKEN, OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		Order order1 = new Order("1", token, null, null, true, "");
		order1.setInstanceId(INSTANCE_1_ID);
		order1.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orders.add(order1);
		Order order2 = new Order("2", token, null, null, true, "");
		order2.setInstanceId(INSTANCE_2_ID);
		order2.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orders.add(order2);
		Order order3 = new Order("3", new Token("token", "user", DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, null, true, "");
		order3.setInstanceId(INSTANCE_3_ID_WITHOUT_USER);
		order3.setProvidingMemberId(OCCITestHelper.MEMBER_ID);
		orders.add(order3);

		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);

		mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(new HashMap<String, String>());

		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);

		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, orders);

		this.helper.initializeComponentCompute(computePlugin, identityPlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, Mockito.mock(AccountingPlugin.class), Mockito.mock(AccountingPlugin.class), 
				Mockito.mock(BenchmarkingPlugin.class), ordersToAdd, mapperPlugin);

	}

	@After
	public void tearDown() throws Exception {
		File dbFile = new File(OCCITestHelper.INSTANCE_DB_FILE + ".mv.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}		
		this.helper.stopComponent();
	}

	@Test
	public void testPostComputeAcceptOcciOk() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
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
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
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
	public void testPostComputeAcceptOcciWithNetworkOk() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.LINK,
				"</network/123>; rel=\"http://schemas.ogf.org/occi/infrastructure#network\"; category=\"http://schemas.ogf.org/occi/infrastructure#networkinterface\"; ");
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
	public void testPostComputeAcceptOcciWithNetworkNotFounded() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.LINK,
				"</network/federated_network_123>; rel=\"http://schemas.ogf.org/occi/infrastructure#network\"; category=\"http://schemas.ogf.org/occi/infrastructure#networkinterface\"; ");
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
		assertNull(instanceId);
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());

	}

	@Test
	public void testPostComputeWrongComputeFailed() throws Exception {

		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
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
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
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
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
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
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
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
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
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
		properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderConstants.USER_DATA_TERM,
				"user_data");
		properties.put(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderAttribute.EXTRA_USER_DATA_ATT.getValue(),
				FED_COMPUTE_USER_DATA);
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(occiUserdataResource);
		List<Category> orderCategories = new ArrayList<Category>();
		Map<String, String> orderXOCCIAtt = new HashMap<String, String>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(FED_COMPUTE_USER_DATA, fakeScriptEncoded);

		ComputeServerResource csr = new ComputeServerResource();
		csr.convertUserData(properties, resources, orderCategories, orderXOCCIAtt,
				xOCCIAtt);
		
		assertEquals(1, orderCategories.size());
		assertEquals(new Category(OrderConstants.USER_DATA_TERM, OrderConstants.SCHEME, OrderConstants.MIXIN_CLASS), orderCategories.get(0));
		assertEquals(2, orderXOCCIAtt.size());
		assertEquals(fakeScriptEncoded, orderXOCCIAtt.get(OrderAttribute.EXTRA_USER_DATA_ATT.getValue()));
		assertEquals(MIMEMultipartArchive.SHELLSCRIPT.getType(), orderXOCCIAtt.get(OrderAttribute.EXTRA_USER_DATA_CONTENT_TYPE_ATT.getValue()));
	}
	
	@Test
	public void testConvertUserDataBadRequest() throws Exception {
		exception.expect(OCCIException.class);

		Resource occiUserdataResource = new Resource("user_data; scheme=\"http://schemas.openstack.org/compute/instance#\"; class=\"mixin\"");

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderConstants.USER_DATA_TERM,
				"user_data");
		properties.put(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderAttribute.EXTRA_USER_DATA_ATT.getValue(),
				FED_COMPUTE_USER_DATA);
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(occiUserdataResource);
		List<Category> orderCategories = new ArrayList<Category>();
		Map<String, String> orderXOCCIAtt = new HashMap<String, String>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();

		ComputeServerResource csr = new ComputeServerResource();
		csr.convertUserData(properties, resources, orderCategories, orderXOCCIAtt,
				xOCCIAtt);
		
	}

	@Test
	public void testConvertPublicKey() throws Exception {
		String fakePublicKey = "org.openstack.credentials.publickey.data org.openstack.credentials.publickey.name=ssh-rsa "
				+ "AAAAB3NzaC1yc2EAAAADAQABAAABAQDI6g9Q7epXV1ciIsPHin";
		
		Resource fogbowPublicKey = new Resource("public_key; scheme=\"http://schemas.openstack.org/instance/credentials#\"; class=\"mixin\"");	

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderConstants.PUBLIC_KEY_TERM,
				"public_key");
		properties.put(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderAttribute.DATA_PUBLIC_KEY.getValue(),
				FED_COMPUTE_ATT_PUBLICKEY_NAME);

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(fogbowPublicKey);
		List<Category> orderCategories = new ArrayList<Category>();
		Map<String, String> orderXOCCIAtt = new HashMap<String, String>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(FED_COMPUTE_ATT_PUBLICKEY_NAME, fakePublicKey);
		
		ComputeServerResource csr = new ComputeServerResource();
		csr.convertPublicKey(properties, resources, orderCategories, orderXOCCIAtt,
				xOCCIAtt);
		
		assertEquals(1, orderCategories.size());
		assertEquals(OrderConstants.PUBLIC_KEY_TERM, orderCategories.get(0).getTerm());
		assertEquals(1, orderXOCCIAtt.size());
		assertEquals(fakePublicKey, orderXOCCIAtt.get(OrderAttribute.DATA_PUBLIC_KEY.getValue()));

	}
	
	@Test
	public void testConvertPublicKeyBadRequest() throws Exception {
		
		exception.expect(OCCIException.class);
		
		Resource fogbowPublicKey = new Resource("public_key; scheme=\"http://schemas.openstack.org/instance/credentials#\"; class=\"mixin\"");	

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderConstants.PUBLIC_KEY_TERM,
				"public_key");
		properties.put(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderAttribute.DATA_PUBLIC_KEY.getValue(),
				FED_COMPUTE_ATT_PUBLICKEY_NAME);

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(fogbowPublicKey);
		List<Category> orderCategories = new ArrayList<Category>();
		Map<String, String> orderXOCCIAtt = new HashMap<String, String>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		
		ComputeServerResource csr = new ComputeServerResource();
		csr.convertPublicKey(properties, resources, orderCategories, orderXOCCIAtt,
				xOCCIAtt);

	}
}
