package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetCompute {

	private static final String INSTANCE_1_ID = "test1";
	private static final String INSTANCE_2_ID = "test2";
	
	OCCITestHelper helper;
	
	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();

		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		Link link = null;
		Instance instance1 = new Instance(INSTANCE_1_ID, list, map, link);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), 
				Mockito.eq(INSTANCE_1_ID))).thenReturn(instance1);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), 
				Mockito.eq(INSTANCE_2_ID))).thenReturn(new Instance(INSTANCE_2_ID));
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(OCCITestHelper.ACCESS_TOKEN)).thenReturn(
				OCCITestHelper.USER_MOCK);
		
		List<Request> requests = new LinkedList<Request>();
		Request request1 = new Request("1", OCCITestHelper.ACCESS_TOKEN, 
				OCCITestHelper.USER_MOCK, null, null);
		request1.setInstanceId(INSTANCE_1_ID);
		requests.add(request1);
		Request request2 = new Request("2", OCCITestHelper.ACCESS_TOKEN, 
				OCCITestHelper.USER_MOCK, null, null);
		request2.setInstanceId(INSTANCE_2_ID);
		requests.add(request2);
		
		this.helper.initializeComponentCompute(computePlugin, identityPlugin, requests);
	}
	
	@After 
	public void tearDown() throws Exception{
		this.helper.stopComponent();
	}
	
	@Test
	public void TestGetComputeOk() throws Exception{
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);	
		
		Assert.assertEquals(2, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestGetSpecificInstanceFound() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);	
		
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void TestGetSpecificInstanceNotFound() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);	
		
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void TestWrongContentType() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void TestWrongAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_1_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void TestEmptyAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}	
}