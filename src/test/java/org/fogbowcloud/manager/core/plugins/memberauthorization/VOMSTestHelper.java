package org.fogbowcloud.manager.core.plugins.memberauthorization;

import java.util.Arrays;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.identity.voms.Fixture;
import org.fogbowcloud.manager.core.plugins.identity.voms.Utils;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin.ProxyCertificateGenerator;
import org.fogbowcloud.manager.occi.model.Token;
import org.mockito.Mockito;

import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;

public class VOMSTestHelper {

	private static final String VOMS_PASSWORD = "pass";
	private static final String VOMS_SERVER = "test.vo";
	
	public static Token createToken() throws Exception {
		PEMCredential holder = Utils.getTestUserCredential();
		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);

		String accessId = CertificateUtils.generateAccessId(Arrays.asList(proxy
				.getCertificateChain()), proxy.getCredential());
		
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.VOMS_PATH_TRUST_ANCHORS,
				"src/test/resources/voms/trust-anchors");
		properties.put(ConfigurationConstants.VOMS_PATH_VOMSES, "src/test/resources/voms/vomses");
		properties.put(ConfigurationConstants.VOMS_PATH_VOMSDIR, "src/test/resources/voms/vomsdir");
		properties.put(ConfigurationConstants.FEDERATION_USER_PASS_VOMS, VOMS_PASSWORD);
		properties.put(ConfigurationConstants.FEDERATION_USER_SERVER_VOMS, VOMS_SERVER);

		VomsIdentityPlugin vomsIdentityPlugin = new VomsIdentityPlugin(properties);
		ProxyCertificateGenerator generatorProxyCertificate = Mockito.mock(ProxyCertificateGenerator.class);
		vomsIdentityPlugin.setGenerateProxyCertificate(generatorProxyCertificate);
		
		return vomsIdentityPlugin.getToken(accessId);
	}
	
}
