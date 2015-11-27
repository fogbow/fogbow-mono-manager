package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.io.FileInputStream;
import java.io.IOException;
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
	
	@Test
	public void testGetCredentialsFromFile() throws IOException {		
		String propertiesFile = "src/test/resources/local_credentials/fake_properties";

		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(propertiesFile);
		properties.load(input);

		String normalizeUser = LocalCredentialsHelper
				.normalizeUser("CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR");
		Map<String, String> localCredentials = LocalCredentialsHelper
				.getCredentialsPerRelatedLocalName(properties, normalizeUser);

		Map<String, String> expectedCredentials = new HashMap<String, String>();
		expectedCredentials.put("username", "user1");
		expectedCredentials.put("password", "userpass1");
		expectedCredentials.put("tenantName", "usertenant1");

		Assert.assertEquals(expectedCredentials, localCredentials);
	}
}
