package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestUsageServerResource {
	
	private static final double ACCEPTABLE_ERROR = 0.00;
	private final String ID_RESOURCEINFO1 = "id1";
	private final String ID_RESOURCEINFO2 = "id2";
	
	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AccountingPlugin accountingPlugin;
	private AuthorizationPlugin authorizationPlugin;
	
	private List<FederationMember> federationMembers = new ArrayList<FederationMember>();
	private Token defaultToken = new Token("access", "user", null, new HashMap<String, String>());
	
	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
						"", "", "", "", new LinkedList<Flavor>()));
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.accountingPlugin = Mockito.mock(AccountingPlugin.class);
		this.authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		
		ResourcesInfo resourcesInfo = new ResourcesInfo(ID_RESOURCEINFO1, "", "", "", "",
				new ArrayList<Flavor>());
		ResourcesInfo resourcesInfo2 = new ResourcesInfo(ID_RESOURCEINFO2, "", "", "", "",
				new ArrayList<Flavor>());
		federationMembers.add(new FederationMember(resourcesInfo));
		federationMembers.add(new FederationMember(resourcesInfo2));

		this.helper = new OCCITestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}
	
	@Test
	public void testEmptyUsage() throws Exception {		
		// mocking
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID))
				.thenReturn(defaultToken);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		Mockito.when(accountingPlugin.getUsersUsage()).thenReturn(new HashMap<String, Double>());
		Mockito.when(authorizationPlugin.isAuthorized(defaultToken)).thenReturn(true);
		
		// initializing component
		helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_USAGE);
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		get.addHeader(OCCIHeaders.X_LOCAL_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		
		// checking usage
		List<ResourceUsage> membersUsage = UsageServerResource.getMembersUsage(responseStr);
		Map<String, Double> usersUsage = UsageServerResource.getUsersUsage(responseStr);
		
		Assert.assertTrue(membersUsage.isEmpty());
		Assert.assertTrue(usersUsage.isEmpty());
	}
	
	@Test
	public void testUsersUsageAndEmptyMembers() throws Exception {		
		// mocking
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID))
				.thenReturn(defaultToken);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		HashMap<String, Double> expectedUsersUsage = new HashMap<String, Double>();
		expectedUsersUsage.put("user1", 1.5);
		expectedUsersUsage.put("user2", 5.5);
		Mockito.when(accountingPlugin.getUsersUsage()).thenReturn(expectedUsersUsage);
		Mockito.when(authorizationPlugin.isAuthorized(defaultToken)).thenReturn(true);
		
		// initializing component
		helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_USAGE);
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		get.addHeader(OCCIHeaders.X_LOCAL_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		
		// checking usage
		List<ResourceUsage> membersUsage = UsageServerResource.getMembersUsage(responseStr);
		Map<String, Double> usersUsage = UsageServerResource.getUsersUsage(responseStr);
		
		Assert.assertTrue(membersUsage.isEmpty());
		Assert.assertFalse(usersUsage.isEmpty());
		Assert.assertTrue(usersUsage.containsKey("user1"));
		Assert.assertEquals(expectedUsersUsage.get("user1"), usersUsage.get("user1"), ACCEPTABLE_ERROR);
		Assert.assertTrue(usersUsage.containsKey("user2"));
		Assert.assertEquals(expectedUsersUsage.get("user2"), usersUsage.get("user2"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testUsersUsageOnUsersEndpoint() throws Exception {		
		// mocking
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID))
				.thenReturn(defaultToken);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		HashMap<String, Double> expectedUsersUsage = new HashMap<String, Double>();
		expectedUsersUsage.put("user1", 1.5);
		expectedUsersUsage.put("user2", 5.5);
		Mockito.when(accountingPlugin.getUsersUsage()).thenReturn(expectedUsersUsage);
		Mockito.when(authorizationPlugin.isAuthorized(defaultToken)).thenReturn(true);
		
		// initializing component
		helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_USAGE + "/users");
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		get.addHeader(OCCIHeaders.X_LOCAL_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		
		// checking usage
		List<ResourceUsage> membersUsage = UsageServerResource.getMembersUsage(responseStr);
		Map<String, Double> usersUsage = UsageServerResource.getUsersUsage(responseStr);
		
		Assert.assertTrue(membersUsage.isEmpty());
		Assert.assertFalse(usersUsage.isEmpty());
		Assert.assertTrue(usersUsage.containsKey("user1"));
		Assert.assertEquals(expectedUsersUsage.get("user1"), usersUsage.get("user1"), ACCEPTABLE_ERROR);
		Assert.assertTrue(usersUsage.containsKey("user2"));
		Assert.assertEquals(expectedUsersUsage.get("user2"), usersUsage.get("user2"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testMembersUsageOnMembersEndpoint() throws Exception {		
		// mocking
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID))
				.thenReturn(defaultToken);
		
		HashMap<String, ResourceUsage> expectedMembersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage resourceUsage1 = new ResourceUsage(ID_RESOURCEINFO1);
		ResourceUsage resourceUsage2 = new ResourceUsage(ID_RESOURCEINFO2);
		expectedMembersUsage.put(ID_RESOURCEINFO1, resourceUsage1);
		expectedMembersUsage.put(ID_RESOURCEINFO2, resourceUsage2);		
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				expectedMembersUsage);
		Mockito.when(accountingPlugin.getUsersUsage()).thenReturn(new HashMap<String, Double>());
		Mockito.when(authorizationPlugin.isAuthorized(defaultToken)).thenReturn(true);
		
		// initializing component
		helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_USAGE + "/members");
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		get.addHeader(OCCIHeaders.X_LOCAL_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		
		// checking usage
		List<ResourceUsage> membersUsage = UsageServerResource.getMembersUsage(responseStr);
		Map<String, Double> usersUsage = UsageServerResource.getUsersUsage(responseStr);
		
		Assert.assertTrue(usersUsage.isEmpty());
		Assert.assertEquals(2, membersUsage.size());
		Assert.assertTrue(membersUsage.contains(resourceUsage1));
		Assert.assertTrue(membersUsage.contains(resourceUsage2));
	}
	
	@Test
	public void testUsersAndMembersUsage() throws Exception {		
		// mocking
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID))
				.thenReturn(defaultToken);
		HashMap<String, ResourceUsage> expectedMembersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage resourceUsage1 = new ResourceUsage(ID_RESOURCEINFO1);
		ResourceUsage resourceUsage2 = new ResourceUsage(ID_RESOURCEINFO2);
		expectedMembersUsage.put(ID_RESOURCEINFO1, resourceUsage1);
		expectedMembersUsage.put(ID_RESOURCEINFO2, resourceUsage2);		
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				expectedMembersUsage);
		HashMap<String, Double> expectedUsersUsage = new HashMap<String, Double>();
		expectedUsersUsage.put("user1", 1.5);
		expectedUsersUsage.put("user2", 5.5);
		Mockito.when(accountingPlugin.getUsersUsage()).thenReturn(expectedUsersUsage);
		Mockito.when(authorizationPlugin.isAuthorized(defaultToken)).thenReturn(true);
		
		// initializing component
		helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_USAGE);
		get.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		get.addHeader(OCCIHeaders.X_LOCAL_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		
		// checking usage
		List<ResourceUsage> membersUsage = UsageServerResource.getMembersUsage(responseStr);
		Map<String, Double> usersUsage = UsageServerResource.getUsersUsage(responseStr);
		
		Assert.assertFalse(membersUsage.isEmpty());
		Assert.assertFalse(usersUsage.isEmpty());
		Assert.assertTrue(usersUsage.containsKey("user1"));
		Assert.assertEquals(expectedUsersUsage.get("user1"), usersUsage.get("user1"), ACCEPTABLE_ERROR);
		Assert.assertTrue(usersUsage.containsKey("user2"));
		Assert.assertEquals(expectedUsersUsage.get("user2"), usersUsage.get("user2"), ACCEPTABLE_ERROR);
		Assert.assertTrue(membersUsage.contains(resourceUsage1));
		Assert.assertTrue(membersUsage.contains(resourceUsage2));
	}
	
	@Test
	public void testGetUsersUsage(){		
		String responseStr = "userId=user1, consumed=3.0\nuserId=user2, consumed=5.5\nuserId=user3, consumed=8.9 ";
		Map<String, Double> usage = UsageServerResource.getUsersUsage(responseStr);
		
		Assert.assertTrue(UsageServerResource.getMembersUsage(responseStr).isEmpty());
		Assert.assertEquals(3, usage.size());
		Assert.assertTrue(usage.containsKey("user1"));		
		Assert.assertEquals(3.0, usage.get("user1"), ACCEPTABLE_ERROR);
		Assert.assertTrue(usage.containsKey("user2"));
		Assert.assertEquals(5.5, usage.get("user2"), ACCEPTABLE_ERROR);
		Assert.assertTrue(usage.containsKey("user3"));
		Assert.assertEquals(8.9, usage.get("user3"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testGetMembersUsage(){		
		String responseStr = "memberId=member1, consumed=5.3, donated=4.2\nmemberId=member2, consumed=8.1, donated=9.2 ";
		List<ResourceUsage> usage = UsageServerResource.getMembersUsage(responseStr);
		
		Assert.assertTrue(UsageServerResource.getUsersUsage(responseStr).isEmpty());
		Assert.assertEquals(2, usage.size());
		Assert.assertTrue(usage.contains(ResourceUsage.parse("memberId=member1, consumed=5.3, donated=4.2")));
		Assert.assertTrue(usage.contains(ResourceUsage.parse("memberId=member2, consumed=8.1, donated=9.2 ")));
	}
}
