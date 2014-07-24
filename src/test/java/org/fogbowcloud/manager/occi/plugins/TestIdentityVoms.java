package org.fogbowcloud.manager.occi.plugins;

import java.io.FileInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.core.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TestIdentityVoms {

	private final String VOMS_PASSWORD = "pass";
	private final String VOMS_SERVER = "atlas";

	private VomsIdentityPlugin vomsIdentityPlugin;
	private Properties properties;

	@Before
	public void setUp() {
		properties = new Properties();
		properties.put(ConfigurationConstants.VOMS_PATH_TRUST,
				"/src/test/resources/voms/trust-anchors");
		properties.put(ConfigurationConstants.VOMS_PATH_VOMSES, "/src/test/resources/voms/vomses");
		properties
				.put(ConfigurationConstants.VOMS_PATH_VOMSDIR, "/src/test/resources/voms/vomsdir");
		properties.put(ConfigurationConstants.FEDERATION_USER_PASS_VOMS, VOMS_PASSWORD);
		properties.put(ConfigurationConstants.FEDERATION_USER_SERVER_VOMS, VOMS_SERVER);

		vomsIdentityPlugin = new VomsIdentityPlugin(properties);
	}

	@Ignore
	@Test
	public void testR() {
		Token token = new Token("", "", new Date(), new HashMap<String, String>());

		Token reIssueToken = vomsIdentityPlugin.reIssueToken(token);
	}

	@Ignore
	@Test
	public void testCreateToken() {
		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(Token.Constants.VOMS_PASSWORD.getValue(), VOMS_PASSWORD);
		credentials.put(Token.Constants.VOMS_SERVER.getValue(), VOMS_SERVER);

		 Token token = vomsIdentityPlugin.createToken(credentials);
	}

	@Test
	public void testGetToken() throws Exception {
		FileInputStream fileInputStream = new FileInputStream(
				"src/test/resources/voms/certs/expired.cert.pem");

		String accessId = IOUtils.toString(fileInputStream, "UTF-8");
		Token token = vomsIdentityPlugin.getToken(accessId);

		Assert.assertEquals("CN=Test CA, O=IGI, C=IT", token.getUser());
		Assert.assertEquals(accessId, token.getAccessId());
		Assert.assertEquals("Thu Dec 01 21:00:00 BRT 2011", token.getExpirationDate().toString());
	}

	@Ignore
	@Test
	public void testValidCetificate() throws Exception {
		FileInputStream fileInputStream = new FileInputStream(
				"src/test/resources/voms/certs/expired.cert.pem");

		String accessId = IOUtils.toString(fileInputStream, "UTF-8");
		Assert.assertTrue(vomsIdentityPlugin.isValid(accessId));

	}

	@Test
	public void testExpiredCertificate() throws Exception {
		FileInputStream fileInputStream = new FileInputStream(
				"src/test/resources/voms/certs/expired.cert.pem");

		String accessId = IOUtils.toString(fileInputStream, "UTF-8");
		Assert.assertFalse(vomsIdentityPlugin.isValid(accessId));
	}

	@Test
	public void testNotYetValidCertificate() throws Exception {
		FileInputStream fileInputStream = new FileInputStream(
				"src/test/resources/voms/certs/expired.cert.pem");

		String accessId = IOUtils.toString(fileInputStream, "UTF-8");
		Assert.assertFalse(vomsIdentityPlugin.isValid(accessId));
	}
}
