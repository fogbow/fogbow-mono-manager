package org.fogbowcloud.manager.occi.order;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.data.MediaType;

public class TestPostOrder {

	private OCCITestHelper orderHelper;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		this.orderHelper = new OCCITestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(Token.class), 
				Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString()))
				.thenReturn("");

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN)).thenReturn(
				new Token("id", OCCITestHelper.USER_MOCK, new Date(), 
				new HashMap<String, String>()));
		Mockito.when(identityPlugin.getToken(OCCITestHelper.INVALID_TOKEN)).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		this.orderHelper.initializeComponent(computePlugin, identityPlugin, authorizationPlugin);
	}

	@Test
	public void testPostOrder() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.ACCEPT, MediaType.TEXT_PLAIN.toString());
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		List<String> orderIDs = OCCITestHelper.getOrderIdsPerLocationHeader(response);

		Assert.assertEquals(1, orderIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostOrderTwoInstances() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.INSTANCE_COUNT.getValue() + " = 2");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		List<String> orderIDs = OCCITestHelper.getOrderIdsPerLocationHeader(response);

		Assert.assertEquals(2, orderIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostOrderManyInstances() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.INSTANCE_COUNT.getValue() + " = 200");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		List<String> orderIDs = OCCITestHelper.getOrderIdsPerLocationHeader(response);
		
		Assert.assertEquals(200, orderIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryTermOrder() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category("wrong", OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategorySchemeOrder() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				"http://schemas.fogbowcloud.org/wrong#", OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryClassOrder() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, "mixin");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidAttributeOrder() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.INSTANCE_COUNT.getValue() + " =\"x\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidAcessTokenOrder() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.INVALID_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidContentTypeOrder() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, "text/plain");
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostOrderInvalidAttributeInstances() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.INSTANCE_COUNT.getValue() + " =\"x\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostOrderInvalidAttributeType() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.TYPE.getValue() + " =\"x\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostOrderInvalidAttributeValidFrom() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.INSTANCE_COUNT.getValue() + " =\"x\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostOrderInvalidAttributeValidUntil() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.VALID_UNTIL.getValue() + " =\"x\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostOrderAllAttributes() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.INSTANCE_COUNT.getValue() + "=10");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.TYPE.getValue() + "=\"one-time\"");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.VALID_FROM.getValue() + "=\"2014-04-01\"");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(10, OCCITestHelper.getOrderIdsPerLocationHeader(response).size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostOrderInvalidAccept() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.ACCEPT, "invalid");
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}
	
	@After
	public void tearDown() throws Exception {
		this.orderHelper.stopComponent();
	}
}
