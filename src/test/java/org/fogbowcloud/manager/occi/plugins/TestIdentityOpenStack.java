package org.fogbowcloud.manager.occi.plugins;

import org.fogbowcloud.manager.occi.KeyStoneApplication;
import org.fogbowcloud.manager.occi.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestIdentityOpenStack {

	private IdentityOpenStackPlugin identityOpenStack;
	private PluginHelper pluginHelper;
	
	public static final String MY_TOKEN = "HgfugGJHgJgHJGjGJgJg-857GHGYHjhHjH";
	public static final String USERNAME_AUTH = "admin";
	
	private final String KEYSTONE_END_POINT = "http://localhost:" + PluginHelper.PORT_ENDPOINT + 
			KeyStoneApplication.TARGET;

	@Before
	public void setUp() throws Exception {
		this.identityOpenStack = new IdentityOpenStackPlugin(KEYSTONE_END_POINT);
		this.pluginHelper = new PluginHelper();
		this.pluginHelper.initializeComponent();	
	}
	
	@After
	public void tearDown() throws Exception {
		this.pluginHelper.disconnectComponent();
	}

	@Test
	public void testValidToken() {
		Assert.assertTrue(this.identityOpenStack.isValidToken(MY_TOKEN));
	}

	@Test
	public void testInvalidToken() {
		Assert.assertFalse(this.identityOpenStack.isValidToken("Invalid Token"));
	}
	
	@Test
	public void testGetNameUserFromToken() {
		Assert.assertEquals(USERNAME_AUTH, this.identityOpenStack.getUser(MY_TOKEN));
	}
}
