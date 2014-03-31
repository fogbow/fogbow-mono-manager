package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
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

public class TestDeleteRequest {

	private Component component;
	
	private final String ACCESS_TOKEN = "xxxxxxxxxxxxxxxxxxxxxxxxxxx";
	private final String CONTENT_TYPE = "text/occi";	
	
	private final int PORT_ENDPOINT = 8182;
	
	@Before
	public void setup() throws Exception {
		this.component = new Component();
		component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);
		component.getDefaultHost().attach(new OCCIApplication());
		component.start();
	}	
	
	@Test
	public void testRequest() throws URISyntaxException, HttpException, IOException{
		HttpDelete delete = new HttpDelete("http://localhost:8182/request");
		
		delete.addHeader("X-Auth-Token", ACCESS_TOKEN);

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);
		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testDeleteEmptyRequest() throws URISyntaxException, HttpException, IOException{
		HttpGet get = new HttpGet("http://localhost:8182/request");
		
		get.addHeader("Content-Type", CONTENT_TYPE);
		get.addHeader("X-Auth-Token", ACCESS_TOKEN);

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);		
		
		Assert.assertEquals(0, getRequestIds(response).size());		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
		
		HttpDelete delete = new HttpDelete("http://localhost:8182/request");
		
		delete.addHeader("X-Auth-Token", ACCESS_TOKEN);

		client = new DefaultHttpClient();
		response = client.execute(delete);
		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
		
		response = client.execute(get);	
		Assert.assertEquals(0, getRequestIds(response).size());		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());				
	}	
		
	@Test
	public void testDeleteSpecificRequest() throws URISyntaxException, HttpException, IOException{
		//Post
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
				
		//Delete
		HttpDelete delete = new HttpDelete("http://localhost:8182/request/" + requestIDs.get(0));
		
		delete.addHeader("X-Auth-Token", ACCESS_TOKEN);

		response = client.execute(delete);
		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
		
		//Get
		HttpGet get = new HttpGet("http://localhost:8182/request");
		
		get.addHeader("Content-Type", CONTENT_TYPE);
		get.addHeader("X-Auth-Token", ACCESS_TOKEN);

		response = client.execute(get);		
		
		Assert.assertEquals(0, getRequestIds(response).size());		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());				
	}		
	
	@Test
	public void testDeleteManyRequest() throws URISyntaxException, HttpException, IOException{
		//Post
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
			
		HttpDelete delete;
		//Delete
		for (String requestId : requestIDs) {
			delete = new HttpDelete("http://localhost:8182/request/" + requestId);
			delete.addHeader("X-Auth-Token", ACCESS_TOKEN);
			response = client.execute(delete);
			Assert.assertEquals(200, response.getStatusLine().getStatusCode());
		}
		
		//Get
		HttpGet get = new HttpGet("http://localhost:8182/request");
		
		get.addHeader("Content-Type", CONTENT_TYPE);
		get.addHeader("X-Auth-Token", ACCESS_TOKEN);

		response = client.execute(get);		
		
		Assert.assertEquals(0, getRequestIds(response).size());		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());				
	}			
	
	@Test
	public void testDeleteAllRequest() throws URISyntaxException, HttpException, IOException{
		//Post
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
					
		//Delete
		HttpDelete delete = new HttpDelete("http://localhost:8182/request");
		delete.addHeader("X-Auth-Token", ACCESS_TOKEN);
		response = client.execute(delete);
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
		
		//Get
		HttpGet get = new HttpGet("http://localhost:8182/request");
		
		get.addHeader("Content-Type", CONTENT_TYPE);
		get.addHeader("X-Auth-Token", ACCESS_TOKEN);

		response = client.execute(get);		
		
		Assert.assertEquals(0, getRequestIds(response).size());		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());				
	}	
	
	@Test
	public void testDeleteRequestNotFound() throws URISyntaxException, HttpException, IOException{
		HttpDelete delete = new HttpDelete("http://localhost:8182/request/" + "invalid_id");
		
		delete.addHeader("X-Auth-Token", ACCESS_TOKEN);

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);
		
		Assert.assertEquals(404, response.getStatusLine().getStatusCode());
	}
		
	@Test
	public void testDeleteRequestInvalidToken() throws URISyntaxException, HttpException, IOException{
		HttpDelete delete = new HttpDelete("http://localhost:8182/request/");
		
		delete.addHeader("X-Auth-Token", "invalid_token");

		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);
		
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
