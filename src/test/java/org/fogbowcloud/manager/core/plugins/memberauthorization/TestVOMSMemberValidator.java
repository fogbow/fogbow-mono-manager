package org.fogbowcloud.manager.core.plugins.memberauthorization;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.bouncycastle.util.io.pem.PemObject;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.memberauthorization.VOMSMemberAuthorizationPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.SecurityRestrictionHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestVOMSMemberValidator {

	private Properties properties;
	private VOMSMemberAuthorizationPlugin memberValidator;

	@Before
	public void setup() {
		this.properties = new Properties();
		this.properties.put(VOMSMemberAuthorizationPlugin.PROP_PATH_TRUST_ANCHORS,
				"src/test/resources/voms/trust-anchors");
		this.properties.put(VOMSMemberAuthorizationPlugin.PROP_PATH_VOMSES, 
				"src/test/resources/voms/vomses");
		this.properties.put(VOMSMemberAuthorizationPlugin.PROP_PATH_VOMSDIR, 
				"src/test/resources/voms/vomsdir");
		this.memberValidator = new VOMSMemberAuthorizationPlugin(properties);
	}
	
	@Test
	public void testCanDonateToConsideringPrivateKey() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		this.properties.put(VOMSMemberAuthorizationPlugin.PROP_CHECK_FORWARDED_PRIVATE_KEY, "true");
		Token token = VOMSTestHelper.createToken();
		Assert.assertTrue(memberValidator.canDonateTo(
				new FederationMember(new ResourcesInfo("Any", "", "", "", "", null)), 
				token));
	}
	
	@Test
	public void testCantDonateToConsideringPrivateKey() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		this.properties.put(VOMSMemberAuthorizationPlugin.PROP_CHECK_FORWARDED_PRIVATE_KEY, "true");
		Token token = VOMSTestHelper.createToken();
		List<PemObject> chain = CertificateUtils.parseChain(token.getAccessId());
		Collection<X509Certificate> certificates = CertificateUtils.extractCertificates(chain);
		String accessIdWithNoPrivateKey = CertificateUtils.generateAccessId(certificates);
		
		Assert.assertFalse(memberValidator.canDonateTo(new FederationMember(
				new ResourcesInfo("Any", "", "", "", "", null)), 
				new Token(accessIdWithNoPrivateKey, null, null, null)));
	}
	
	@Test
	public void testCanDonateToDisregardingPrivateKey() throws Exception {
		if (!SecurityRestrictionHelper.checkUnlimitedStrengthPolicy()) {
			return;
		}
		this.properties.put(VOMSMemberAuthorizationPlugin.PROP_CHECK_FORWARDED_PRIVATE_KEY, "false");
		Token token = VOMSTestHelper.createToken();
		List<PemObject> chain = CertificateUtils.parseChain(token.getAccessId());
		Collection<X509Certificate> certificates = CertificateUtils.extractCertificates(chain);
		String accessIdWithNoPrivateKey = CertificateUtils.generateAccessId(certificates);
		
		Assert.assertTrue(memberValidator.canDonateTo(new FederationMember(
				new ResourcesInfo("Any", "", "", "", "", null)), 
				new Token(accessIdWithNoPrivateKey, null, null, null)));
	}
	
	@Test
	public void testCanReceiveFrom() throws Exception {
		Assert.assertTrue(memberValidator.canReceiveFrom(
				new FederationMember(new ResourcesInfo("Any", "", "", "", "", null))));
	}
}
