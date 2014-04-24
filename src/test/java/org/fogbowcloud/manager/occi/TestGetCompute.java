package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.fogbowcloud.manager.occi.util.RequestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetCompute {

	//TODO rename 
	RequestHelper requestHelper;
	List<Instance> instances;
	
	@Before
	public void setup() throws Exception {
		this.requestHelper = new RequestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString())).thenReturn(new Instance());
		instances = new ArrayList<Instance>();
		instances.add(new Instance("test1"));
		instances.add(new Instance("test2"));
		Mockito.when(computePlugin.getInstances(Mockito.anyString())).thenReturn(instances);
		
		List<Resource> list = new ArrayList<Resource>();
		Map<String, String> map = new HashMap<String, String>();
		map.put("test", "test");
		Link link = null;
		Instance instance = new Instance(list,map,link);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString())).thenReturn(instance);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(RequestHelper.ACCESS_TOKEN)).thenReturn(RequestHelper.USER_MOCK);
		
		this.requestHelper.initializeComponentCompute(computePlugin, identityPlugin);
	}
	
	@After 
	public void tearDown() throws Exception{
		this.requestHelper.stopComponent();
	}
	
	@Test
	public void TestGetComputeOk() throws Exception{
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);	
		
		Assert.assertEquals(instances.size(), RequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestGetSpecificInstanceFound() throws Exception {
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE + RequestHelper.INSTANCE_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);	
		
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void TestGetSpecificInstanceNotFound() throws Exception {
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);	
		
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void TestWrongContentType() throws Exception {
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void TestWrongAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE + RequestHelper.INSTANCE_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void TestEmptyAccessToken() throws Exception {
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
		
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}	
}