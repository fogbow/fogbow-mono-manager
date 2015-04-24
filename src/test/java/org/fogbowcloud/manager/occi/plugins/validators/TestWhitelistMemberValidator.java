package org.fogbowcloud.manager.occi.plugins.validators;

import java.util.Properties;

import org.fogbowcloud.manager.core.WhitelistMemberValidator;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestWhitelistMemberValidator {

	private Properties properties;
	private WhitelistMemberValidator memberValidator;

	@Before
	public void setup() {
		this.properties = new Properties();
		this.properties.put(WhitelistMemberValidator.PROP_WHITELIST_DONATE, 
				"donate1,donate2,donate3");
		this.properties.put(WhitelistMemberValidator.PROP_WHITELIST_RECEIVE, 
				"receive1,receive2,receive3");
		this.memberValidator = new WhitelistMemberValidator(properties);
	}
	
	@Test
	public void testCanDonateTo() throws Exception {
		Assert.assertTrue(memberValidator.canDonateTo(
				new FederationMember(new ResourcesInfo("donate1", "", "", "", "", "", "")), 
				null));
	}
	
	@Test
	public void testCantDonateTo() throws Exception {
		Assert.assertFalse(memberValidator.canDonateTo(
				new FederationMember(new ResourcesInfo("donate4", "", "", "", "", "", "")), 
				null));
	}
	
	@Test
	public void testCanReceiveFrom() throws Exception {
		Assert.assertTrue(memberValidator.canReceiveFrom(
				new FederationMember(new ResourcesInfo("receive1", "", "", "", "", "", ""))));
	}
	
	@Test
	public void testCantReceiveFrom() throws Exception {
		Assert.assertFalse(memberValidator.canReceiveFrom(
				new FederationMember(new ResourcesInfo("receive4", "", "", "", "", "", ""))));
	}
}
