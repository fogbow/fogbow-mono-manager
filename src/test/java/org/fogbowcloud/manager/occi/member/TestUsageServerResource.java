package org.fogbowcloud.manager.occi.member;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
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
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestUsageServerResource {
	
	private static final double ACCEPTABLE_ERROR = 0.00;
	private final String ID_RESOURCEINFO1 = "memberId1";
	private final String ID_RESOURCEINFO2 = "memberId2";
	
	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AccountingPlugin accountingPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private MapperPlugin mapperPlugin;
	
	private List<FederationMember> federationMembers = new ArrayList<FederationMember>();
	private Token defaultToken = new Token("access", "user", null, new HashMap<String, String>());
	
	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
						"", "", "", "", "", ""));
		this.mapperPlugin = Mockito.mock(MapperPlugin.class);
		Map<String, Map<String, String>> defaultFederationUsersCrendetials = 
				new HashMap<String, Map<String,String>>();
		defaultFederationUsersCrendetials.put("one", new HashMap<String, String>());
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(
				defaultFederationUsersCrendetials);	
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.accountingPlugin = Mockito.mock(AccountingPlugin.class);
		this.authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		
		ResourcesInfo resourcesInfo = new ResourcesInfo(ID_RESOURCEINFO1, "", "", "", "",
				"", "");
		ResourcesInfo resourcesInfo2 = new ResourcesInfo(ID_RESOURCEINFO2, "", "", "", "",
				"", "");
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
		
		Mockito.when(accountingPlugin.getAccountingInfo()).thenReturn(
				new ArrayList<AccountingInfo>());
		Mockito.when(authorizationPlugin.isAuthorized(defaultToken)).thenReturn(true);
		
		// initializing component
		helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers, mapperPlugin);
		
		// checking there is not usage on member1
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER + "/" + ID_RESOURCEINFO1 + "/usage");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		
		// checking usage
		AccountingInfo memberUsageByUser = getUsageFrom(defaultToken.getUser(), responseStr, OrderConstants.COMPUTE_TERM);
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, memberUsageByUser.getRequestingMember());
		Assert.assertEquals(ID_RESOURCEINFO1, memberUsageByUser.getProvidingMember());
		Assert.assertEquals(0, memberUsageByUser.getUsage(), ACCEPTABLE_ERROR);

		// checking there is not usage on member2
		get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER + "/" + ID_RESOURCEINFO2 + "/usage");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		
		// checking usage
		memberUsageByUser = getUsageFrom(defaultToken.getUser(), responseStr, OrderConstants.COMPUTE_TERM);
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, memberUsageByUser.getRequestingMember());
		Assert.assertEquals(ID_RESOURCEINFO2, memberUsageByUser.getProvidingMember());
		Assert.assertEquals(0, memberUsageByUser.getUsage(), ACCEPTABLE_ERROR);
	}
	
	public static AccountingInfo getUsageFrom(String user, String responseStr, String resourceKing) {
		StringTokenizer st = new StringTokenizer(responseStr, "\n");
		String providingMember = "";
		double consuption = -1;
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
			if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memberId=")) {
				String[] tokens = line.split("=");
				providingMember = tokens[1].trim();
			} else if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE + ": compute usage=")) {
				String[] tokens = line.split("=");
				if (resourceKing.equals(OrderConstants.COMPUTE_TERM)) {
					consuption = Double.parseDouble(tokens[1].trim());
				}				
			} else if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE + ": storage usage=")) {
				String[] tokens = line.split("=");
				if (resourceKing.equals(OrderConstants.STORAGE_TERM)) {
					consuption = Double.parseDouble(tokens[1].trim());
				}
			}			
		}		
		
		Assert.assertNotEquals("", providingMember);
		Assert.assertNotEquals(-1, consuption, 0.00000);

		AccountingInfo accountingInfo = new AccountingInfo(user,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, providingMember);
		accountingInfo.addConsuption(consuption);
		return accountingInfo;
	}

	@Test
	public void testUsageEmptyInAMemberAndIsNotEmptyInAnotherMember() throws Exception {		
		// mocking
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID))
				.thenReturn(defaultToken);
		
		AccountingInfo accountingEntry1 = new AccountingInfo(defaultToken.getUser(), DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, ID_RESOURCEINFO1);
		accountingEntry1.addConsuption(0);
		
		AccountingInfo accountingEntry2 = new AccountingInfo(defaultToken.getUser(), DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, ID_RESOURCEINFO2);
		accountingEntry2.addConsuption(20.5);
		
		Mockito.when(accountingPlugin.getAccountingInfo(defaultToken.getUser(),
						DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, ID_RESOURCEINFO1))
				.thenReturn(accountingEntry1);
		Mockito.when(accountingPlugin.getAccountingInfo(defaultToken.getUser(),
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, ID_RESOURCEINFO2))
		.thenReturn(accountingEntry2);

		Mockito.when(authorizationPlugin.isAuthorized(defaultToken)).thenReturn(true);
		
		// initializing component
		helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers, mapperPlugin);
		
		// checking usage on member1
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER + "/" + ID_RESOURCEINFO1
				+ "/usage");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		// checking usage
		AccountingInfo memberUsageByUser = getUsageFrom(defaultToken.getUser(), responseStr, OrderConstants.COMPUTE_TERM);
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				memberUsageByUser.getRequestingMember());
		Assert.assertEquals(ID_RESOURCEINFO1, memberUsageByUser.getProvidingMember());
		Assert.assertEquals(0, memberUsageByUser.getUsage(), ACCEPTABLE_ERROR);

		// checking there is not usage on member2
		get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER + "/" + ID_RESOURCEINFO2
				+ "/usage");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		// checking usage
		memberUsageByUser = getUsageFrom(defaultToken.getUser(), responseStr, OrderConstants.COMPUTE_TERM);
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				memberUsageByUser.getRequestingMember());
		Assert.assertEquals(ID_RESOURCEINFO2, memberUsageByUser.getProvidingMember());
		Assert.assertEquals(20.5, memberUsageByUser.getUsage(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testUsageIsNotEmptyInBothMembers() throws Exception {		
		// mocking
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID))
				.thenReturn(defaultToken);
		
		AccountingInfo accountingEntry1 = new AccountingInfo(defaultToken.getUser(), DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, ID_RESOURCEINFO1);
		accountingEntry1.addConsuption(10.5);
		
		AccountingInfo accountingEntry2 = new AccountingInfo(defaultToken.getUser(), DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, ID_RESOURCEINFO2);
		accountingEntry2.addConsuption(20.5);
		
		Mockito.when(accountingPlugin.getAccountingInfo(defaultToken.getUser(),
						DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, ID_RESOURCEINFO1))
				.thenReturn(accountingEntry1);
		Mockito.when(accountingPlugin.getAccountingInfo(defaultToken.getUser(),
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, ID_RESOURCEINFO2))
		.thenReturn(accountingEntry2);
		Mockito.when(authorizationPlugin.isAuthorized(defaultToken)).thenReturn(true);
		
		// initializing component
		helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers, mapperPlugin);
		
		// checking usage on member1
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER + "/" + ID_RESOURCEINFO1
				+ "/usage");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		// checking usage
		AccountingInfo memberUsageByUser = getUsageFrom(defaultToken.getUser(), responseStr, OrderConstants.COMPUTE_TERM);
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				memberUsageByUser.getRequestingMember());
		Assert.assertEquals(ID_RESOURCEINFO1, memberUsageByUser.getProvidingMember());
		Assert.assertEquals(10.5, memberUsageByUser.getUsage(), ACCEPTABLE_ERROR);

		// checking there is not usage on member2
		get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER + "/" + ID_RESOURCEINFO2
				+ "/usage");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));		

		// checking usage
		memberUsageByUser = getUsageFrom(defaultToken.getUser(), responseStr,
				OrderConstants.COMPUTE_TERM);
		AccountingInfo memberStorageUsageByUser = getUsageFrom(defaultToken.getUser(), responseStr,
				OrderConstants.STORAGE_TERM);
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				memberUsageByUser.getRequestingMember());
		Assert.assertEquals(ID_RESOURCEINFO2, memberUsageByUser.getProvidingMember());
		Assert.assertEquals(20.5, memberUsageByUser.getUsage(), ACCEPTABLE_ERROR);
		Assert.assertEquals(20.5, memberStorageUsageByUser.getUsage(), ACCEPTABLE_ERROR);
	}
}
