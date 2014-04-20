package org.fogbowcloud.manager.occi;

import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.RequestHelper;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetCompute {

	//TODO rename 
	RequestHelper requestHelper;
	
	@Before
	public void setup() throws Exception {
		this.requestHelper = new RequestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.isValidToken(RequestHelper.ACCESS_TOKEN)).thenReturn(true);
		Mockito.when(identityPlugin.getUser(RequestHelper.ACCESS_TOKEN)).thenReturn(RequestHelper.USER_MOCK);

		this.requestHelper.initializeComponent(computePlugin, identityPlugin);
	}
	
	@After 
	public void tearDown() throws Exception{
		this.requestHelper.stopComponent();
	}
	
	@Ignore
	@Test
	public void TestGetComputeEmpty() throws Exception{
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);	
		
		Assert.assertEquals(0, RequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Ignore
	@Test
	public void TestGetComputeOneVM() throws Exception{
		//Post /request
		HttpPost httpPost = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpPost);
		List<String> requestIDs = RequestHelper.getRequestLocations(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		//Get /compute/
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(httpGet);	
		
		Assert.assertEquals(1, RequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Ignore
	@Test
	public void TestGetComputeManyVMs() throws Exception{
		//Post /request
		HttpPost httpPost = new HttpPost(RequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		httpPost.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 3");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpPost);
		List<String> requestIDs = RequestHelper.getRequestLocations(response);

		Assert.assertEquals(3, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		//Get /compute/
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(httpGet);	
		
		Assert.assertEquals(3, RequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
	
	@Ignore
	@Test(expected = OCCIException.class)
	public void TestWrongContentType() throws Exception{
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		client.execute(httpGet);	
	}

	@Ignore
	@Test(expected = OCCIException.class)
	public void TestWrongAccessToken() throws Exception{
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		client.execute(httpGet);	
	}
	
	@Ignore
	@Test(expected = OCCIException.class)
	public void TestInvalidVM() throws Exception{
		HttpGet httpGet = new HttpGet(RequestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		client.execute(httpGet);	
	}	
}
