package org.fogbowcloud.manager.core.plugins.authorization;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestAllowAllAuthorizationPlugin {

	private AllowAllAuthorizationPlugin allowAllAuthorizationPlugin;

	@Before
	public void setUp() {
		this.allowAllAuthorizationPlugin = new AllowAllAuthorizationPlugin(new Properties());
	}

	@Test
	public void testIsAuthorized() {
		Assert.assertTrue(allowAllAuthorizationPlugin.isAuthorized(null));
	}

}
