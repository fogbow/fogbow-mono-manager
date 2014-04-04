package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.fogbowcloud.manager.occi.model.TestRequestHelper;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.engine.adapter.HttpRequest;

public class TestPostRequest {

	private TestRequestHelper testRequestHelper;

	@Before
	public void setup() throws Exception {
		this.testRequestHelper = new TestRequestHelper();

		HttpResponse response = new DefaultHttpResponseFactory().newHttpResponse(
				new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(HttpRequest.class))).thenReturn(
				response);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.isValidToken(TestRequestHelper.ACCESS_TOKEN)).thenReturn(true);

		this.testRequestHelper.initializeComponent(computePlugin, identityPlugin);
	}

	@Test
	public void testPostRequest() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestLocations(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestTwoInstances() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 2");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestLocations(response);

		Assert.assertEquals(2, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestManyInstances() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestLocations(response);

		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryTermRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category("wrong", FogbowResourceConstants.SCHEME_FOGBOW_REQUEST,
				HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategorySchemeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				"http://schemas.fogbowcloud.org/wrong#", HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryClassRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, "mixin");
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidAttributeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidAcessTokenRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, "invalid_acess_token");
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidContentTypeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, "text/plain");
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeInstances() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeType() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_TYPE_FOGBOW_REQUEST + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeValidFrom() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_VALID_FROM_FOGBOW_REQUEST + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeValidUntil() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestAllAttributes() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + "=10");
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_TYPE_FOGBOW_REQUEST + "=\"one-time\"");
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST + "=\"2014-04-01\"");
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_VALID_FROM_FOGBOW_REQUEST + "=\"2014-03-30\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(10, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.testRequestHelper.stopComponent();
	}
}
