package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.model.Category;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class TestPostRequest {

	private Component component;

	private final String ACCESS_TOKEN = "xxxxxxxxxxxxxxxxxxxxxxxxxxx";
	private final String CONTENT_TYPE = "text/occi";

	@Before
	public void setup() throws Exception {
		this.component = new Component();
		component.getServers().add(Protocol.HTTP, 8182);
		component.getDefaultHost().attach(new OCCIApplication());
		component.start();
	}

	@Test
	public void testRequest() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("fogbow-request",
				"http://schemas.fogbowcloud.org/request#", "kind");

		post.addHeader("Content-Type", CONTENT_TYPE);
		post.addHeader("X-Auth-Token", ACCESS_TOKEN);
		post.addHeader("Category", category.getHeaderFormat());

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		List<String> requestIDs = getRequestIds(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());

	}

	@Test
	public void testRequestTwoInstances() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("fogbow-request",
				"http://schemas.fogbowcloud.org/request#", "kind");

		post.addHeader("Content-Type", CONTENT_TYPE);
		post.addHeader("X-Auth-Token", ACCESS_TOKEN);
		post.addHeader("Category", category.getHeaderFormat());
		post.addHeader("X-OCCI-Attribute", "org.fogbowcloud.request.instance = 2");

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		List<String> requestIDs = getRequestIds(response);

		Assert.assertEquals(2, requestIDs.size());
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());

	}

	@Test
	public void testRequestManyInstances() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("fogbow-request",
				"http://schemas.fogbowcloud.org/request#", "kind");

		post.addHeader("Content-Type", CONTENT_TYPE);
		post.addHeader("X-Auth-Token", ACCESS_TOKEN);
		post.addHeader("Category", category.getHeaderFormat());
		post.addHeader("X-OCCI-Attribute", "org.fogbowcloud.request.instance = 200");

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		List<String> requestIDs = getRequestIds(response);

		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());

	}

	@Test
	public void testInvalidCategoryTermRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("wrong", "http://schemas.fogbowcloud.org/request#", "kind");

		post.addHeader("Content-Type", CONTENT_TYPE);
		post.addHeader("X-Auth-Token", ACCESS_TOKEN);
		post.addHeader("Category", category.getHeaderFormat());

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(400, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testInvalidCategorySchemeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("fogbow-request", "http://schemas.fogbowcloud.org/wrong#",
				"kind");

		post.addHeader("Content-Type", CONTENT_TYPE);
		post.addHeader("X-Auth-Token", ACCESS_TOKEN);
		post.addHeader("Category", category.getHeaderFormat());

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(400, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testInvalidCategoryClassRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("fogbow-request",
				"http://schemas.fogbowcloud.org/request#", "mixin");

		post.addHeader("Content-Type", CONTENT_TYPE);
		post.addHeader("X-Auth-Token", ACCESS_TOKEN);
		post.addHeader("Category", category.getHeaderFormat());

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(400, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testInvalidAttributeRequest() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("fogbow-request",
				"http://schemas.fogbowcloud.org/request#", "kind");

		post.addHeader("Content-Type", CONTENT_TYPE);
		post.addHeader("X-Auth-Token", ACCESS_TOKEN);
		post.addHeader("Category", category.getHeaderFormat());
		post.addHeader("X-OCCI-Attribute", "org.fogbowcloud.invalidAttribute=\"x\"");

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(400, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testInvalidAcessTokenRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("fogbow-request",
				"http://schemas.fogbowcloud.org/request#", "kind");

		post.addHeader("Content-Type", CONTENT_TYPE);
		post.addHeader("X-Auth-Token", "invalid_acess_token");
		post.addHeader("Category", category.getHeaderFormat());

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(401, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testInvalidContentTypeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost("http://localhost:8182/request");
		Category category = new Category("fogbow-request",
				"http://schemas.fogbowcloud.org/request#", "kind");

		post.addHeader("Content-Type", "text/plain");
		post.addHeader("X-Auth-Token", ACCESS_TOKEN);
		post.addHeader("Category", category.getHeaderFormat());

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(401, response.getStatusLine().getStatusCode());
	}

	private List<String> getRequestIds(HttpResponse response) throws ParseException, IOException {
		String responseStr = EntityUtils.toString(response.getEntity(), "utf-8");

		String[] tokens = responseStr.split("X-OCCI-RequestId:");
		List<String> requestIds = new ArrayList<String>();

		for (int i = 0; i < tokens.length; i++) {
			if (!tokens[i].equals("")) {
				requestIds.add(tokens[i]);
			}
		}

		return requestIds;
	}

	@After
	public void tearDown() throws Exception {
		component.stop();
	}

}
