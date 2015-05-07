package org.fogbowcloud.manager.core.plugins.identity.voms;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin.ProxyCertificateGenerator;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.SecurityRestrictionHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import eu.emi.security.authn.x509.helpers.proxy.ProxyCertificateImpl;
import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;

public class TestIdentityVoms {

	private static final long TWELVE_HOURS = 1000 * 60 * 60 * 12;
	private static final long ONE_MINUTE = 1000 * 60;
	private final String VOMS_PASSWORD = "pass";
	private final String VOMS_SERVER = "test.vo";

	private ProxyCertificateGenerator generatorProxyCertificate;
	private VomsIdentityPlugin vomsIdentityPlugin;
	private Properties properties;

	@Before
	public void setUp() {
		properties = new Properties();
		properties.put(ConfigurationConstants.VOMS_PATH_TRUST_ANCHORS,
				"src/test/resources/voms/trust-anchors");
		properties.put(ConfigurationConstants.VOMS_PATH_VOMSES, "src/test/resources/voms/vomses");
		properties.put(ConfigurationConstants.VOMS_PATH_VOMSDIR, "src/test/resources/voms/vomsdir");
		properties.put(ConfigurationConstants.FEDERATION_USER_PASS_VOMS, VOMS_PASSWORD);
		properties.put(ConfigurationConstants.FEDERATION_USER_SERVER_VOMS, VOMS_SERVER);

		vomsIdentityPlugin = new VomsIdentityPlugin(properties);
		generatorProxyCertificate = Mockito.mock(ProxyCertificateGenerator.class);
		vomsIdentityPlugin.setGenerateProxyCertificate(generatorProxyCertificate);
	}

	@Test
	public void testReIssueToken() throws Exception {
		Token token = new Token("accessId", "user", new Date(), new HashMap<String, String>());
		Assert.assertEquals(token, vomsIdentityPlugin.reIssueToken(token));
	}

	@Test
	public void testCreateToken() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		Date before = new Date(System.currentTimeMillis() + TWELVE_HOURS - ONE_MINUTE);

		PEMCredential holder = Utils.getTestUserCredential();
		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		X509Certificate[] extendedChain = proxy.getCertificateChain();
		PrivateKey proxyPrivateKey = proxy.getPrivateKey();
		ProxyCertificateImpl proxyCertificate = new ProxyCertificateImpl(extendedChain,
				proxyPrivateKey);

		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(VomsIdentityPlugin.PASSWORD, VOMS_PASSWORD);
		credentials.put(VomsIdentityPlugin.SERVER_NAME, VOMS_SERVER);

		Mockito.when(generatorProxyCertificate.generate(credentials)).thenReturn(proxyCertificate);
		Token token = vomsIdentityPlugin.createToken(credentials);

		Date after = new Date(System.currentTimeMillis() + TWELVE_HOURS);

		Assert.assertEquals(
				CertificateUtils.generateAccessId(
						Arrays.asList(proxy.getCertificateChain()), proxy.getCredential()),
				token.getAccessId());
		Assert.assertEquals("CN=test0, O=IGI, C=IT", token.getUser());
		Assert.assertTrue(token.getExpirationDate().after(before));
		Assert.assertTrue(token.getExpirationDate().before(after));
	}

	@Test
	public void testGetToken() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		Date before = new Date(System.currentTimeMillis() + TWELVE_HOURS - ONE_MINUTE);
		PEMCredential holder = Utils.getTestUserCredential();

		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);

		String accessId = CertificateUtils.generateAccessId(Arrays.asList(proxy
				.getCertificateChain()), proxy.getCredential());
		
		Token token = vomsIdentityPlugin.getToken(accessId);
		Date after = new Date(System.currentTimeMillis() + TWELVE_HOURS);

		Assert.assertEquals("CN=test0, O=IGI, C=IT", token.getUser());
		Assert.assertEquals(accessId, token.getAccessId());
		Assert.assertTrue(token.getExpirationDate().after(before));
		Assert.assertTrue(token.getExpirationDate().before(after));
	}

	@Test
	public void testValidCertificate() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		PEMCredential holder = Utils.getTestUserCredential();

		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		Assert.assertTrue(vomsIdentityPlugin.isValid(CertificateUtils.generateAccessId(
				Arrays.asList(proxy.getCertificateChain()), 
				proxy.getCredential())));
	}

	@Test
	public void testExpiredCertificate() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		PEMCredential holder = Utils.getExpiredCredential();

		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		Assert.assertFalse(vomsIdentityPlugin.isValid(CertificateUtils.generateAccessId(
				Arrays.asList(proxy.getCertificateChain()), 
				proxy.getCredential())));
	}

	@Test
	public void testGetCredential() {
		Credential[] credentials = vomsIdentityPlugin.getCredentials();
		Assert.assertEquals(4, credentials.length);
		Assert.assertEquals(new Credential(VomsIdentityPlugin.PASSWORD, true, null), credentials[0]);
		Assert.assertEquals(new Credential(VomsIdentityPlugin.SERVER_NAME, true, null),
				credentials[1]);
		Assert.assertEquals(new Credential(VomsIdentityPlugin.PATH_USERCRED, false,
				VomsIdentityPlugin.CREDENTIALS_PATH_DEFAULT), credentials[2]);
		Assert.assertEquals(new Credential(VomsIdentityPlugin.PATH_USERKEY, false,
				VomsIdentityPlugin.CREDENTIALS_PATH_DEFAULT), credentials[3]);
	}
	
	private static final String PRIVATE_KEY_REGEX = 
			"(-----BEGIN [A-Z\\s]* PRIVATE KEY-----)([^\\-]*)(-----END [A-Z\\s]* PRIVATE KEY-----)";
	
	@Test
	public void testGetForwardableTokenAsAWhole() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		
		properties.put(ConfigurationConstants.VOMS_SHOULD_FORWARD_PRIVATE_KEY, "true");
		
		PEMCredential holder = Utils.getTestUserCredential();

		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		String accessId = CertificateUtils.generateAccessId(Arrays.asList(proxy
				.getCertificateChain()), proxy.getCredential());
		Token token = vomsIdentityPlugin.getToken(accessId);
		
		
		Pattern pattern = Pattern.compile(PRIVATE_KEY_REGEX);
		String forwardableToken = vomsIdentityPlugin.getForwardableToken(token).getAccessId();
		Matcher matcher = pattern.matcher(forwardableToken);
		
		Assert.assertTrue(matcher.find());
		Assert.assertEquals(token.getAccessId(), forwardableToken);
	}
	
	@Test
	public void testGetForwardableTokenWithNoPrivateKey() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		
		properties.put(ConfigurationConstants.VOMS_SHOULD_FORWARD_PRIVATE_KEY, "false");
		
		PEMCredential holder = Utils.getTestUserCredential();

		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		String accessId = CertificateUtils.generateAccessId(Arrays.asList(proxy
				.getCertificateChain()), proxy.getCredential());
		Token token = vomsIdentityPlugin.getToken(accessId);
		
		Pattern pattern = Pattern.compile(PRIVATE_KEY_REGEX);
		Matcher matcher = pattern.matcher(vomsIdentityPlugin.getForwardableToken(token).getAccessId());
		
		Assert.assertFalse(matcher.find());
	}
}
