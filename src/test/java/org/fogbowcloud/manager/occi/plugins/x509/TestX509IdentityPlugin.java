package org.fogbowcloud.manager.occi.plugins.x509;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.x509.X509IdentityPlugin;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestX509IdentityPlugin {

//	private static final String CERTIFICATE_PATH = "src/test/resources/x509/CAdir/selfsigned.pem";
	private static final String CERTIFICATE_PATH = "src/test/resources/x509/cert.pem";
	private static final String CA_DIR_PATH = "src/test/resources/x509/CAdir";
	private static final long TWENTY_HOURS = 72000000;
	private static final String FEDERATION_CERTIFICATE_PATH = null;
	private final String CERTIFICATE_USER = "CN=Test, OU=Fogbow, O=LSD, L=CG, ST=PB, C=Br";
	private final Date CERTIFICATE_EXPIRATION_DATE = new Date(2015, Calendar.AUGUST, 26);
	private final String CERTIFICATE_ACCESS_ID = CertificateUtils.generateAcessId(CertificateUtils
			.getCertificateChainFromFile(CERTIFICATE_PATH)); 
	
	Properties properties;
	X509IdentityPlugin x509IdentityPlugin;
	
	@Before
	public void setup() {
		properties = new Properties();
//		properties.put(OneConfigurationConstants.COMPUTE_ONE_URL, );
		
	}
	
	@Test
	public void testCreateToken() {	
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);
		
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(X509IdentityPlugin.CERTIFICATE_PATH_KEY, CERTIFICATE_PATH);
		System.out.println("userCred: " + userCredentials);
		Token token = x509IdentityPlugin.createToken(userCredentials);
		
		Assert.assertEquals(CERTIFICATE_ACCESS_ID, token.getAccessId());
		Assert.assertEquals(CERTIFICATE_USER, token.getUser());		
//		Assert.assertTrue(Math.abs(CERTIFICATE_EXPIRATION_DATE.getTime()
//				- token.getExpirationDate().getTime()) < TWENTY_HOURS);
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE.getDate(), token.getExpirationDate().getDate());
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE.getMonth(), token.getExpirationDate().getMonth());
	}
	
	@Test
	public void testIsValidAccessId() {
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);
		Assert.assertTrue(x509IdentityPlugin.isValid(CERTIFICATE_ACCESS_ID));
	}
	
	@Test
	public void testIsInvalidAccessId() {
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);
		Assert.assertFalse(x509IdentityPlugin.isValid("invalid_accessId"));
	}
	
	@Test
	public void testReIssueToken() {	
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);

		// getting token
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(X509IdentityPlugin.CERTIFICATE_PATH_KEY, CERTIFICATE_PATH);
		Token token = x509IdentityPlugin.createToken(userCredentials);
		
		Assert.assertEquals(CERTIFICATE_ACCESS_ID, token.getAccessId());
		Assert.assertEquals(CERTIFICATE_USER, token.getUser());		
//		Assert.assertTrue(Math.abs(CERTIFICATE_EXPIRATION_DATE.getTime()
//				- token.getExpirationDate().getTime()) < TWENTY_HOURS);
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE.getDate(), token.getExpirationDate().getDate());
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE.getMonth(), token.getExpirationDate().getMonth());
		
		// reissue token
		Assert.assertEquals(token, x509IdentityPlugin.reIssueToken(token));
	}
	
	@Test
	public void testGetToken() {	
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);

		// getting token
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(X509IdentityPlugin.CERTIFICATE_PATH_KEY, CERTIFICATE_PATH);
		Token token = x509IdentityPlugin.getToken(CERTIFICATE_ACCESS_ID);
		
		Assert.assertEquals(CERTIFICATE_ACCESS_ID, token.getAccessId());
		Assert.assertEquals(CERTIFICATE_USER, token.getUser());		
//		Assert.assertTrue(Math.abs(CERTIFICATE_EXPIRATION_DATE.getTime()
//				- token.getExpirationDate().getTime()) < TWENTY_HOURS);
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE.getDate(), token.getExpirationDate().getDate());
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE.getMonth(), token.getExpirationDate().getMonth());
	}
	
	@Test
	public void testCreateFederationUser() {
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		properties.put(ConfigurationConstants.FEDERATION_USER_X509_CERTIFICATE_PATH_KEY, CERTIFICATE_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);

		Token federationToken = x509IdentityPlugin.createFederationUserToken();
		
		Assert.assertEquals(CERTIFICATE_ACCESS_ID, federationToken.getAccessId());
		Assert.assertEquals(CERTIFICATE_USER, federationToken.getUser());		
//		Assert.assertTrue(Math.abs(CERTIFICATE_EXPIRATION_DATE.getTime()
//				- token.getExpirationDate().getTime()) < TWENTY_HOURS);
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE.getDate(), federationToken.getExpirationDate().getDate());
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE.getMonth(), federationToken.getExpirationDate().getMonth());
	}
		
	@Test(expected=OCCIException.class)
	public void testCreateTokenWithoutCertificatePath() {	
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);
		x509IdentityPlugin.createToken(new HashMap<String, String>());		
	}
	

}
