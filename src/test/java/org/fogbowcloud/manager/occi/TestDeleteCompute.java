package org.fogbowcloud.manager.occi;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.util.RequestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDeleteCompute {

	// TODO rename
	RequestHelper requestHelper;

	@Before
	public void setup() throws Exception {
		this.requestHelper = new RequestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.doNothing().when(computePlugin).removeInstances(RequestHelper.ACCESS_TOKEN);
		Mockito.doNothing().when(computePlugin)
				.removeInstance(RequestHelper.ACCESS_TOKEN, RequestHelper.INSTANCE_ID);
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(RequestHelper.ACCESS_TOKEN)).thenReturn(
				RequestHelper.USER_MOCK);

		this.requestHelper.initializeComponentCompute(computePlugin, identityPlugin);
	}

	@After
	public void tearDown() throws Exception {
		this.requestHelper.stopComponent();
	}

	@Test
	public void TestDelete() throws Exception {
		HttpDelete httpDelete = new HttpDelete(RequestHelper.URI_FOGBOW_COMPUTE + "");
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, requestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestDeleteSpecificInstanceFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(RequestHelper.URI_FOGBOW_COMPUTE
				+ RequestHelper.INSTANCE_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestDeleteSpecificInstanceNotFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(RequestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestWrongAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(RequestHelper.URI_FOGBOW_COMPUTE
				+ RequestHelper.INSTANCE_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestEmptyAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(RequestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void TestWrongContentType() throws Exception {
		HttpDelete get = new HttpDelete(RequestHelper.URI_FOGBOW_COMPUTE);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
}
