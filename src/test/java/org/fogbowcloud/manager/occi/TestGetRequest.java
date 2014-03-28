package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class TestGetRequest {

	private Component component;

	@Before
	public void setup() throws Exception {
		this.component = new Component();
		component.getServers().add(Protocol.HTTP, 8182);
		component.getDefaultHost().attach(new OCCIApplication());
		component.start();
	}
	
	@Test
	public void testHeader1() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet("http://localhost:8182/request");
		get.addHeader("X-OCCI-Attribute", "name=value");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);
		String responseStr = EntityUtils.toString(response.getEntity(), "utf-8");
		
		Assert.assertEquals("name=value", responseStr);
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
	}
	
	@After
	public void tearDown() throws Exception {
		component.stop();
	}
	
}
