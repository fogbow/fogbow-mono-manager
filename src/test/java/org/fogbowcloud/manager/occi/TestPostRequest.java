package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.RequestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPostRequest {

	private RequestHelper requestHelper;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		this.requestHelper = new RequestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn("");

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(RequestHelper.ACCESS_TOKEN)).thenReturn(RequestHelper.USER_MOCK);
		Mockito.when(identityPlugin.getUser(RequestHelper.INVALID_TOKEN)).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

		this.requestHelper.initializeComponent(computePlugin, identityPlugin);
	}

	@Test
	public void testPostRequest() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = RequestHelper.getRequestLocations(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestTwoInstances() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 2");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = RequestHelper.getRequestLocations(response);

		Assert.assertEquals(2, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestManyInstances() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = RequestHelper.getRequestLocations(response);
		
		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryTermRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category("wrong", RequestConstants.SCHEME,
				OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategorySchemeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				"http://schemas.fogbowcloud.org/wrong#", OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryClassRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, "mixin");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidAttributeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
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
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.INVALID_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidContentTypeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, "text/plain");
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeInstances() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
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
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
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
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
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
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
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
		HttpPost post = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
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

		Assert.assertEquals(10, RequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.requestHelper.stopComponent();
	}
}
