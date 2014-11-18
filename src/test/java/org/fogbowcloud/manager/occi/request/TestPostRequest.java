package org.fogbowcloud.manager.occi.request;

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
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.data.MediaType;
import org.restlet.engine.header.HeaderConstants;

public class TestPostRequest {

	private OCCITestHelper requestHelper;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		this.requestHelper = new OCCITestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn("");

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN)).thenReturn(
				new Token("id", OCCITestHelper.USER_MOCK, new Date(), 
				new HashMap<String, String>()));
		Mockito.when(identityPlugin.getToken(OCCITestHelper.INVALID_TOKEN)).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		this.requestHelper.initializeComponent(computePlugin, identityPlugin, authorizationPlugin);
	}

	@Test
	public void testPostRequest() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.ACCEPT, MediaType.TEXT_PLAIN.toString());
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = OCCITestHelper.getRequestIds(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestTwoInstances() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 2");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = OCCITestHelper.getRequestIds(response);

		Assert.assertEquals(2, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestManyInstances() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = OCCITestHelper.getRequestIds(response);
		
		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryTermRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category("wrong", RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategorySchemeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				"http://schemas.fogbowcloud.org/wrong#", RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryClassRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, "mixin");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidAttributeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidAcessTokenRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.INVALID_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidContentTypeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, "text/plain");
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeInstances() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeType() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.TYPE.getValue() + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeValidFrom() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeValidUntil() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.VALID_UNTIL.getValue() + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestAllAttributes() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + "=10");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.TYPE.getValue() + "=\"one-time\"");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.VALID_FROM.getValue() + "=\"2014-04-01\"");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(10, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAccept() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.ACCEPT, "invalid");
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}
	
	@After
	public void tearDown() throws Exception {
		this.requestHelper.stopComponent();
	}
}
