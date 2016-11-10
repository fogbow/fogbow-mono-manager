package org.fogbowcloud.manager;

import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.junit.Assert;
import org.junit.Test;

public class TestMainHelper {
	
	@Test
	public void testGetXMPPTimeout() {
		Properties properties = new Properties();
		String timeout = "20000";
		properties.put(ConfigurationConstants.XMPP_TIMEOUT, timeout);
		long xmppTimeout = MainHelper.getXMPPTimeout(properties);
		
		Assert.assertEquals(Long.parseLong(timeout), xmppTimeout);
	}
	
	@Test(expected=Error.class)
	public void testGetXMPPTimeoutMinimumValue() {
		Properties properties = new Properties();
		String timeout = "2000";
		properties.put(ConfigurationConstants.XMPP_TIMEOUT, timeout);
		MainHelper.getXMPPTimeout(properties);
	}
	
	@Test(expected=Error.class)
	public void testGetXMPPTimeoutWorngValue() {
		Properties properties = new Properties();
		String timeout = "worng";
		properties.put(ConfigurationConstants.XMPP_TIMEOUT, timeout);
		MainHelper.getXMPPTimeout(properties);	
	}	
	
}
