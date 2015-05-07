package org.fogbowcloud.manager.occi;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestLocalQuotaServerResource {

	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AccountingPlugin accoutingPlugin;
	private AuthorizationPlugin authorizationPlugin;
	
	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo("", "", "", "", "", ""));
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.accoutingPlugin = Mockito.mock(AccountingPlugin.class);
		this.authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		this.helper = new OCCITestHelper();
	}
	
	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}
	
	@Test
	public void testGetLocalQuota() throws Exception {
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo("2", "1", "100", "35", "9", "1"));

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.LOCAL_ACCESS_TOKEN)).thenReturn(
				new Token(OCCITestHelper.LOCAL_ACCESS_TOKEN, OCCITestHelper.USER_MOCK, new Date(),
				new HashMap<String, String>()));
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");
		
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, new LinkedList<FederationMember>());
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_LOCAL_QUOTA);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_LOCAL_AUTH_TOKEN, OCCITestHelper.LOCAL_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		String[] responseStrSplit = responseStr.split(";");
		Map<String, String> responseMap = new HashMap<String, String>();
		for (String responseStrEl : responseStrSplit) {
			String[] responseStrElSplit = responseStrEl.split("=");
			responseMap.put(responseStrElSplit[0], responseStrElSplit[1]);
		}
		Assert.assertEquals(responseMap.get("cpuIdle"), "2");
		Assert.assertEquals(responseMap.get("cpuInUse"), "1");
		Assert.assertEquals(responseMap.get("memIdle"), "100");
		Assert.assertEquals(responseMap.get("memInUse"), "35");
		Assert.assertEquals(responseMap.get("instancesIdle"), "9");
		Assert.assertEquals(responseMap.get("instancesInUse"), "1");
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetLocalQuotaUnauthorizedNoHeader() throws Exception {
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo("2", "1", "100", "35", "9", "1"));

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.LOCAL_ACCESS_TOKEN)).thenReturn(
				new Token(OCCITestHelper.LOCAL_ACCESS_TOKEN, OCCITestHelper.USER_MOCK, new Date(),
				new HashMap<String, String>()));
		
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, new LinkedList<FederationMember>());
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_LOCAL_QUOTA);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetLocalQuotaUnauthorizedOnCloud() throws Exception {
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo("2", "1", "100", "35", "9", "1"));

		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.LOCAL_ACCESS_TOKEN)).thenThrow(new OCCIException(
				ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));
		
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, new LinkedList<FederationMember>());
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_LOCAL_QUOTA);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_LOCAL_AUTH_TOKEN, OCCITestHelper.LOCAL_ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}
}
