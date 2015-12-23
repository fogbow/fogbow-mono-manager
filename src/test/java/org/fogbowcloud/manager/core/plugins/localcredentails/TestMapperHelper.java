package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class TestMapperHelper {

	@Test
	public void testGetCredentialsPerRelatedLocalName() {
		Properties p = new Properties();
		p.put(MapperHelper.MAPPER_PREFIX + "eubrazilcc_username", "fogbow");
		p.put(MapperHelper.MAPPER_PREFIX + "eubrazilcc_password", "fogbow");
		p.put(MapperHelper.MAPPER_PREFIX + "eubrazilcc_tenantName", "fogbow");
		
		Map<String, String> localCredentials = MapperHelper.getCredentialsPerRelatedLocalName(p,
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
		p.put(MapperHelper.MAPPER_PREFIX + "CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR_username", "fogbow");
		p.put(MapperHelper.MAPPER_PREFIX + "CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR_password", "fogbow");
		p.put(MapperHelper.MAPPER_PREFIX + "CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR_tenantName", "fogbow");
		
		Map<String, String> localCredentials = MapperHelper.getCredentialsPerRelatedLocalName(p,
				"CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR");
		
		Map<String, String> expectedCredentials = new HashMap<String, String>();
		expectedCredentials.put("username", "fogbow");
		expectedCredentials.put("password", "fogbow");
		expectedCredentials.put("tenantName", "fogbow");
		
		Assert.assertEquals(expectedCredentials, localCredentials);
	}
	
	@Test
	public void testGetCredentialsFromFile() throws IOException {		
		String propertiesFile = "src/test/resources/mapper/fake_properties";

		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(propertiesFile);
		properties.load(input);

		String normalizeUser = MapperHelper
				.normalizeUser("CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR");
		Map<String, String> localCredentials = MapperHelper
				.getCredentialsPerRelatedLocalName(properties, normalizeUser);

		Map<String, String> expectedCredentials = new HashMap<String, String>();
		expectedCredentials.put("username", "user1");
		expectedCredentials.put("password", "userpass1");
		expectedCredentials.put("tenantName", "usertenant1");

		Assert.assertEquals(expectedCredentials, localCredentials);
	}
}
