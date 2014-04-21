package org.fogbowcloud.manager.occi;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.RequestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDeleteCompute {

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
	@Test(expected = OCCIException.class)
	public void TestWrongAccessToken() throws Exception{
		HttpDelete httpDelete = new HttpDelete(RequestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		client.execute(httpDelete);	
	}
	
	@Ignore
	@Test(expected = OCCIException.class)
	public void TestInvalidVM() throws Exception{
		HttpDelete get = new HttpDelete(RequestHelper.URI_FOGBOW_COMPUTE);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, RequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		client.execute(get);	
	}	
	
	
}
