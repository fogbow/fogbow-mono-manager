package org.fogbowcloud.manager.core.plugins.identity.saml;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class TestSAMLIdentityPlugin {

	@Test
	public void testValidSAMLAssertion() throws Exception {
		SAMLIdentityPlugin identityPlugin = new SAMLIdentityPlugin(new Properties());
		identityPlugin.getToken(IOUtils.toString(
				new FileInputStream("src/test/resources/saml/gisela.example")));
	}
	
}
