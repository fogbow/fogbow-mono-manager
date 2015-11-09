package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestMemberServerResource {

	private final String ID_RESOURCEINFO1 = "id1";
	private final String ID_RESOURCEINFO2 = "id2";

	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AccountingPlugin accoutingPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private LocalCredentialsPlugin localCredentialsPlugin;
	
	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
						"", "", "", "", "", ""));
		this.localCredentialsPlugin = Mockito.mock(LocalCredentialsPlugin.class);
		Map<String, Map<String, String>> defaultFederationUsersCrendetials = 
				new HashMap<String, Map<String,String>>();
		defaultFederationUsersCrendetials.put("one", new HashMap<String, String>());
		Mockito.when(localCredentialsPlugin.getAllLocalCredentials()).thenReturn(
				defaultFederationUsersCrendetials);
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
	public void testGetMember() throws Exception {
		List<FederationMember> federationMembers = new ArrayList<FederationMember>();
		ResourcesInfo resourcesInfo = new ResourcesInfo(ID_RESOURCEINFO1, "2", "1", "100", "35",
				"", "");
		ResourcesInfo resourcesInfo2 = new ResourcesInfo(ID_RESOURCEINFO2, "2", "1", "100", "35",
				"", "");
		federationMembers.add(new FederationMember(resourcesInfo));
		federationMembers.add(new FederationMember(resourcesInfo));
		federationMembers.add(new FederationMember(resourcesInfo2));

		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, federationMembers, localCredentialsPlugin);		
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, "x_federation_auth_token");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		Assert.assertTrue(checkResponse(responseStr));
		Assert.assertTrue(responseStr.contains(ID_RESOURCEINFO1));
		Assert.assertTrue(responseStr.contains(ID_RESOURCEINFO2));
		Assert.assertTrue(responseStr.contains(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetMemberOnlyMe() throws Exception {
		List<FederationMember> federationMembers = new ArrayList<FederationMember>();
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, federationMembers, localCredentialsPlugin);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, "x_federation_auth_token");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		// Should come with a single member (the manager itself)
		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		Assert.assertTrue(responseStr.contains(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetMemberWithoutFederationToken() throws Exception {
		List<FederationMember> federationMembers = new ArrayList<FederationMember>();
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, federationMembers, localCredentialsPlugin);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void testGetMemberWrongContentType() throws Exception {
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, new LinkedList<FederationMember>(), localCredentialsPlugin);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, "x_federation_auth_token");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	
	private boolean checkResponse(String response) {
		String[] tokens = response.split("\n");
		for (String token : tokens) {
			if (!token.contains("id=")) {
				return false;
			}
		}
		return true;
	}
}
