package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestAccountingServerResource {	

	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private AccountingPlugin computeAccountingPlugin;
	private AccountingPlugin storageAccountingPlugin;
	private Token defaultToken;

	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		this.computeAccountingPlugin = Mockito.mock(AccountingPlugin.class);
		this.storageAccountingPlugin = Mockito.mock(AccountingPlugin.class);
		
		defaultToken = new Token("accessId", new Token.User("user", ""), new Date(), null);
		
		this.helper = new OCCITestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}
	
	@Test
	public void testGetAccountingWrongAccept() throws Exception {
		
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID)).thenReturn(defaultToken);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		List<AccountingInfo> accountingInfos = new ArrayList<AccountingInfo>();
		accountingInfos.add(new AccountingInfo("user", "requestingMember", "providingMember"));
		Mockito.when(computeAccountingPlugin.getAccountingInfo()).thenReturn(accountingInfos);
		
		this.helper.initializeComponentCompute(computePlugin, identityPlugin,
				identityPlugin, authorizationPlugin, null,
				computeAccountingPlugin, computeAccountingPlugin, null,
				new HashMap<String, List<Order>>(), null);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ACCOUNTING + OrderConstants.COMPUTE_TERM);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		get.addHeader(OCCIHeaders.ACCEPT, "wrong");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
	}	
	
	@Test(expected=HttpHostConnectException.class)
	public void testGetAccountingWrongEndpoint() throws Exception {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ACCOUNTING);
		HttpClient client = HttpClients.createMinimal();
		client.execute(get);
	}	
	
	@Test
	public void testGetComputeAccounting() throws Exception {
		
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID)).thenReturn(defaultToken);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		List<AccountingInfo> accountingInfos = new ArrayList<AccountingInfo>();
		AccountingInfo accountingInfoOne = new AccountingInfo("userOne", "requestingMemberOne", "providingMemberOne");
		accountingInfoOne.addConsumption(10.0);
		accountingInfos.add(accountingInfoOne);
		AccountingInfo accountingInfoTwo = new AccountingInfo("userTwo", "requestingMemberTwo", "providingMemberTwo");
		accountingInfoTwo.addConsumption(20.0);
		accountingInfos.add(accountingInfoTwo);
		Mockito.when(computeAccountingPlugin.getAccountingInfo()).thenReturn(accountingInfos);
		
		this.helper.initializeComponentCompute(computePlugin, identityPlugin,
				identityPlugin, authorizationPlugin, null,
				computeAccountingPlugin, storageAccountingPlugin, null,
				new HashMap<String, List<Order>>(), null);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ACCOUNTING + OrderConstants.COMPUTE_TERM);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		
		Mockito.verify(storageAccountingPlugin, Mockito.times(0)).getAccountingInfo();
		Mockito.verify(computeAccountingPlugin, Mockito.times(1)).getAccountingInfo();
		
		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(containsAccountingInfo(responseStr, accountingInfoOne));
		Assert.assertTrue(containsAccountingInfo(responseStr, accountingInfoTwo));
	}		
	
	@Test
	public void testGetStorageAccounting() throws Exception {
		
		Mockito.when(identityPlugin.getToken(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID)).thenReturn(defaultToken);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		List<AccountingInfo> accountingInfos = new ArrayList<AccountingInfo>();
		AccountingInfo accountingInfoOne = new AccountingInfo("userOne", "requestingMemberOne", "providingMemberOne");
		accountingInfoOne.addConsumption(10.0);
		accountingInfos.add(accountingInfoOne);
		AccountingInfo accountingInfoTwo = new AccountingInfo("userTwo", "requestingMemberTwo", "providingMemberTwo");
		accountingInfoTwo.addConsumption(20.0);
		accountingInfos.add(accountingInfoTwo);
		Mockito.when(storageAccountingPlugin.getAccountingInfo()).thenReturn(accountingInfos);
		
		this.helper.initializeComponentCompute(computePlugin, identityPlugin,
				identityPlugin, authorizationPlugin, null,
				computeAccountingPlugin, storageAccountingPlugin,null,
				new HashMap<String, List<Order>>(), null);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ACCOUNTING + OrderConstants.STORAGE_TERM);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		
		Mockito.verify(storageAccountingPlugin, Mockito.times(1)).getAccountingInfo();
		Mockito.verify(computeAccountingPlugin, Mockito.times(0)).getAccountingInfo();		
		
		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(containsAccountingInfo(responseStr, accountingInfoOne));
		Assert.assertTrue(containsAccountingInfo(responseStr, accountingInfoTwo));
	}		
	
	public boolean containsAccountingInfo(String responseStr, AccountingInfo accountingInfo) {
		String[] responseStrLines = responseStr.split("\n");
		for (String responseStrLine : responseStrLines) {
			String[] lineDatas = responseStrLine.split(";");
			if (lineDatas.length >= 4) {
				if (lineDatas[0].contains(accountingInfo.getUser()) 
						&& lineDatas[1].contains(accountingInfo.getRequestingMember())
						&& lineDatas[2].contains(accountingInfo.getProvidingMember())
						&& lineDatas[3].contains(String.valueOf(accountingInfo.getUsage()))) {
					return true;
				}
			}
		}
		return false;
	}
}
