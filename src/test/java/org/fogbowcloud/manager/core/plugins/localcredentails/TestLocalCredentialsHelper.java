package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class TestLocalCredentialsHelper {

	@Test
	public void testGetCredentialsPerRelatedLocalName() {
		Properties p = new Properties();
		p.put("local_credential_eubrazilcc_username", "fogbow");
		p.put("local_credential_eubrazilcc_password", "fogbow");
		p.put("local_credential_eubrazilcc_tenantName", "fogbow");
		
		Map<String, String> localCredentials = LocalCredentialsHelper.getCredentialsPerRelatedLocalName(p,
				"eubrazilcc");
		
		Map<String, String> expectedCredentials = new HashMap<String, String>();
		expectedCredentials.put("username", "fogbow");
		expectedCredentials.put("password", "fogbow");
		expectedCredentials.put("tenantName", "fogbow");
		
		Assert.assertEquals(expectedCredentials, localCredentials);
	}
		
	@Test
	public void testGetCredentialsPerComplexLocalName() {
		Properties p = new Properties();
		p.put("local_credential_CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR_username", "fogbow");
		p.put("local_credential_CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR_password", "fogbow");
		p.put("local_credential_CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR_tenantName", "fogbow");
		
		Map<String, String> localCredentials = LocalCredentialsHelper.getCredentialsPerRelatedLocalName(p,
				"CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR");
		
		Map<String, String> expectedCredentials = new HashMap<String, String>();
		expectedCredentials.put("username", "fogbow");
		expectedCredentials.put("password", "fogbow");
		expectedCredentials.put("tenantName", "fogbow");
		
		Assert.assertEquals(expectedCredentials, localCredentials);
	}

}
