package org.fogbowcloud.manager.core.plugins.identity.x509;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.identity.x509.X509IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@Deprecated
public class TestX509IdentityPlugin {

	private static final String CERTIFICATE_PATH = "src/test/resources/x509/cert.pem";
	private static final String CA_DIR_PATH = "src/test/resources/x509/CAdir";
	private final String CERTIFICATE_USER = "CN=Test, OU=Fogbow, O=LSD, L=CG, ST=PB, C=Br";
	private String dateFormat = "yyyy-MM-dd";
	private Date CERTIFICATE_EXPIRATION_DATE;
	private String CERTIFICATE_ACCESS_ID;

	private Properties properties;
	private X509IdentityPlugin x509IdentityPlugin;

	@Before
	public void setup() throws CertificateException, IOException, ParseException {
		properties = new Properties();
		CERTIFICATE_ACCESS_ID = CertificateUtils.generateAccessId(CertificateUtils
				.getCertificateChainFromFile(CERTIFICATE_PATH));
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
		CERTIFICATE_EXPIRATION_DATE = sdf.parse("2015-08-26");
	}

	@Test
	public void testCreateToken() throws ParseException {
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);

		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(X509IdentityPlugin.CERTIFICATE_PATH_KEY, CERTIFICATE_PATH);
		Token token = x509IdentityPlugin.createToken(userCredentials);

		Assert.assertEquals(CERTIFICATE_ACCESS_ID, token.getAccessId());
		Assert.assertEquals(CERTIFICATE_USER, token.getUser());
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE,
				PluginHelper.formatExpirationDate(dateFormat, token));
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
	public void testReIssueToken() throws ParseException {
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);

		// getting token
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(X509IdentityPlugin.CERTIFICATE_PATH_KEY, CERTIFICATE_PATH);
		Token token = x509IdentityPlugin.createToken(userCredentials);

		Assert.assertEquals(CERTIFICATE_ACCESS_ID, token.getAccessId());
		Assert.assertEquals(CERTIFICATE_USER, token.getUser());
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE,
				PluginHelper.formatExpirationDate(dateFormat, token));

		// reissue token
		Assert.assertEquals(token, x509IdentityPlugin.reIssueToken(token));
	}

	@Test
	public void testGetToken() throws ParseException {
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);

		// getting token
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(X509IdentityPlugin.CERTIFICATE_PATH_KEY, CERTIFICATE_PATH);
		Token token = x509IdentityPlugin.getToken(CERTIFICATE_ACCESS_ID);

		Assert.assertEquals(CERTIFICATE_ACCESS_ID, token.getAccessId());
		Assert.assertEquals(CERTIFICATE_USER, token.getUser());
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE,
				PluginHelper.formatExpirationDate(dateFormat, token));
	}

	@Test
	public void testCreateFederationUser() throws ParseException {
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		properties.put(ConfigurationConstants.FEDERATION_USER_X509_CERTIFICATE_PATH_KEY,
				CERTIFICATE_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);

		Token federationToken = x509IdentityPlugin.createFederationUserToken();

		Assert.assertEquals(CERTIFICATE_ACCESS_ID, federationToken.getAccessId());
		Assert.assertEquals(CERTIFICATE_USER, federationToken.getUser());
		Assert.assertEquals(CERTIFICATE_EXPIRATION_DATE,
				PluginHelper.formatExpirationDate(dateFormat, federationToken));
	}

	@Test(expected = OCCIException.class)
	public void testCreateTokenWithoutCertificatePath() {
		properties.put(ConfigurationConstants.X509_CA_DIR_PATH_KEY, CA_DIR_PATH);
		x509IdentityPlugin = new X509IdentityPlugin(properties);
		x509IdentityPlugin.createToken(new HashMap<String, String>());
	}

	@Test
	public void testGetCredential() {
		x509IdentityPlugin = new X509IdentityPlugin(properties);
		Credential[] credentials = x509IdentityPlugin.getCredentials();
		Assert.assertEquals(1, credentials.length);
		Assert.assertEquals(new Credential(X509IdentityPlugin.CERTIFICATE_PATH_KEY, true, null),
				credentials[0]);
	}

}
