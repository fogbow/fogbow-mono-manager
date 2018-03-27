package org.fogbowcloud.manager.occi.order;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.OCCIComputeApplication;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetOrder {

	private OCCITestHelper orderHelper;
	private String instanceLocation = HeaderUtils.X_OCCI_LOCATION_PREFIX + "http://localhost:"
			+ OCCITestHelper.ENDPOINT_PORT + OCCIComputeApplication.COMPUTE_TARGET
			+ "/b122f3ad-503c-4abb-8a55-ba8d90cfce9f";

	private ManagerController facade;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {		
		this.orderHelper = new OCCITestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
						Mockito.any(Map.class), Mockito.anyString())).thenReturn(instanceLocation);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(new Token("id", new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(),
								new HashMap<String, String>()));
		Mockito.when(identityPlugin.getToken(OCCITestHelper.INVALID_TOKEN)).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

		HashMap<String, String> tokenAttr = new HashMap<String, String>();
		Token userToken = new Token(OCCITestHelper.ACCESS_TOKEN, new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, tokenAttr);

		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN)).thenReturn(userToken);

		MapperPlugin mapperPlugin = Mockito
				.mock(MapperPlugin.class);
		Map<String, Map<String, String>> defaultFederationUsersCrendetials = 
				new HashMap<String, Map<String,String>>();
		HashMap<String, String> credentails = new HashMap<String, String>();
		defaultFederationUsersCrendetials.put("one", credentails);
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(
				defaultFederationUsersCrendetials);
		Mockito.when(identityPlugin.createToken(credentails)).thenReturn(
				new Token("id", new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(), new HashMap<String, String>()));		
		
		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		BenchmarkingPlugin benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		
		//TODO review
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, "")).when(benchmarkingPlugin).remove(Mockito.anyString());
		
		facade = this.orderHelper.initializeComponentExecutorSameThread(computePlugin, identityPlugin,
				authorizationPlugin, benchmarkingPlugin, mapperPlugin);
		TestDataStorageHelper.clearManagerDataStore(
				this.facade.getManagerDataStoreController().getManagerDatabase());
	}
 
	@Test
	public void testGetOrderContent() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(OrderServerResource.NO_ORDERS_MESSAGE,
				EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8)));
	}

	@Test
	public void testGetOrder() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetOrderPlainContent() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetOrderWithoutContentHeader() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetOrderWithAcceptInvalidContent() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.ACCEPT, "invalid-content");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		
		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetOrderInvalidToken() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.INVALID_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetEmptyOrderWithAcceptHeader() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(0, OCCITestHelper.getURIList(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}
	
	@Test
	public void testGetResquestTwoIdsDefaultAccept() throws URISyntaxException, HttpException,
			IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 2");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		// Get		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		//Default accept is text/plain
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetResquestTwoIdsURIListAccept() throws URISyntaxException, HttpException,
			IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 2");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(2, OCCITestHelper.getURIList(response).size());		
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}

	@Test
	public void testGetResquestManyIdsDefaultAccept() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		//Default accept is text/plain
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 10");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		client = HttpClients.createMinimal();
		response = client.execute(post);
		// Get
		get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);
		//Default accept is text/plain
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(10, OCCITestHelper.getLocationIds(response).size());
	}
	
	@Test
	public void testGetResquestManyIdsURIListAccept() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 20");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(20, OCCITestHelper.getURIList(response).size());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE));
	}

	@Test
	public void testGetSpecificOrder() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 1");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.getOrderIdsPerLocationHeader(response).get(0));
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	
	@Test
	public void testGetSpecificOrderWithMethodNotAllowed() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 1");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.getOrderIdsPerLocationHeader(response).get(0));
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.ACCEPT, "invalid-accept");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetOrderNotFound() throws URISyntaxException, ParseException, IOException,
			HttpException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER + "not_found");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetStatusOrder() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 1");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.RESOURCE_KIND.getValue() + "=" 
				+ OrderConstants.COMPUTE_TERM);		
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.getOrderIdsPerLocationHeader(response).get(0));
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		String orderDetails = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		Assert.assertEquals(OrderState.FULFILLED.getValue(),
				orderHelper.getStateFromOrderDetails(orderDetails));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetResquestFilterWithAttribute() throws URISyntaxException, HttpException,
			IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 2");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
				
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		EntityUtils.consume(response.getEntity());

		// Get		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		// Post
		HttpPost postTwo = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		postTwo.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		postTwo.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		postTwo.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		postTwo.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 2");
		postTwo.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.TYPE.getValue()
				+ " = persistent");
				
		client = HttpClients.createMinimal();
		response = client.execute(postTwo);
		
		// Get
		get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.TYPE.getValue() + "=\"one-time\"");
		client = HttpClients.createMinimal();
		response = client.execute(get);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());	
		
		// Get 
		get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.TYPE.getValue() + "=\"notfound\"");
		client = HttpClients.createMinimal();
		response = client.execute(get);
		
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());	
	}

	@Test
	public void testGetResquestFilterWithCategory() throws URISyntaxException, HttpException,
			IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = 2");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		Category categoryFilter = new Category(OCCITestHelper.FOGBOW_SMALL_IMAGE,
				"http://schemas.fogbowcloud.org/template/resource#", "mixin");
		post.addHeader(OCCIHeaders.CATEGORY, categoryFilter.toHeader());

		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(
				OCCIHeaders.CATEGORY,
				OCCITestHelper.FOGBOW_SMALL_IMAGE
						+ "; scheme=\"http://schemas.fogbowcloud.org/template/resource#\"; class=\"mixin\";");

		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		client = HttpClients.createMinimal();
		response = client.execute(get);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.CATEGORY,
				"wrong; scheme=\"http://schemas.fogbowcloud.org/template/resource#\"; class=\"mixin\";");

		response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetOCCIOrderAttributes() throws URISyntaxException, HttpException, IOException {
		
		String orderId="1";
		String instanceId="instance01";
		String networkAddress = "10.10.10.10";
		String networkGateway = "10.10.10.1";
		
		
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(OCCIConstants.NETWORK_ADDRESS, networkAddress);
		attributes.put(OCCIConstants.NETWORK_GATEWAY, networkGateway);
		
		Token federationToken = new Token("1", 
				new Token.User(OCCITestHelper.USER_MOCK, ""), new Date(), attributes);
		
		List<Category> categories = new ArrayList<Category>();
		
		Order order = new Order(orderId, federationToken, instanceId, "", "", new Date().getTime(),
				true, OrderState.OPEN, categories, attributes);
		
		facade.getManagerDataStoreController().addOrder(order);		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER + orderId);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = null;
		responseStr = EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals("\"" + networkAddress + "\"", OCCITestHelper
				.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals("\"" + networkGateway + "\"", OCCITestHelper
				.getOCCIAttByBodyString(responseStr, OCCIConstants.NETWORK_GATEWAY));
		
		for(String attrib : OCCIConstants.getOCCIValues()){
			if(!OCCIConstants.NETWORK_ADDRESS.equals(attrib) && !OCCIConstants.NETWORK_GATEWAY.equals(attrib)){
				Assert.assertEquals("\"Not defined\"", OCCITestHelper.getOCCIAttByBodyString(responseStr, attrib));
			}
		}
		
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());	
	}
	
	@After
	public void tearDown() throws Exception {
		this.orderHelper.stopComponent();
	}

}
