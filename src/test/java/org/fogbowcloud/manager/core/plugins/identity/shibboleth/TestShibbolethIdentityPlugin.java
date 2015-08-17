package org.fogbowcloud.manager.core.plugins.identity.shibboleth;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

public class TestShibbolethIdentityPlugin {

	@Test
	public void testCreateToken() {
		Properties props = new Properties();
		props.setProperty("identity_shibboleth_get_assertion_url", "http://localhost/Shibboleth.sso/GetAssertion");
		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(ShibbolethIdentityPlugin.CRED_ASSERTION_ID, "fakeAssertionId");
		credentials.put(ShibbolethIdentityPlugin.CRED_ASSERTION_KEY, "fakeAssertionKey");
		new ShibbolethIdentityPlugin(props).createToken(credentials);
	}
	
}
