package org.fogbowcloud.manager.core.plugins.identity.ec2;

import java.util.Properties;

import org.junit.Test;

public class TestEC2IdentityPlugin {

	@Test
	public void testGetToken() {
		EC2IdentityPlugin ec2IdentityPlugin = new EC2IdentityPlugin(new Properties());
		ec2IdentityPlugin.getToken("AccessId:SecretKey");
	}
	
}
