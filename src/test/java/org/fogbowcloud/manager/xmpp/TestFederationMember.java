package org.fogbowcloud.manager.xmpp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavour;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestFederationMember {

	private ResourcesInfo resources;
	private String DATE = "2000-10-31T01:30:00.000+0000";
	ManagerTestHelper managerTestHelper;
	
	@Before
	public void setUp() {
		managerTestHelper = new ManagerTestHelper();
		resources = managerTestHelper.getResources();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidResources() {
		new FederationMember(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullResourceId() {
		new ResourcesInfo(null, "cpuIdle",
				"cpuInUse", "memIdle", "memInUse", new LinkedList<Flavour>());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesCpuIdle() {
		new ResourcesInfo("id", null,
				"cpuInUse", "memIdle", "memInUse", new LinkedList<Flavour>());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesCpuInUse() {
		new ResourcesInfo("id", "CpuIdle",
				null, "memIdle", "memInUse", new LinkedList<Flavour>());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesMemIdle() {
		new ResourcesInfo("id", "CpuIdle",
				"cpuInUse", null, "memInUse", new LinkedList<Flavour>());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesMemInUse() {
		new ResourcesInfo("id", "CpuIdle",
				"cpuInUse", "memIdle", null, new LinkedList<Flavour>());
	}
	
	@Test
	public void testConstructor() {
		FederationMember managerItem = new FederationMember(resources);
		Assert.assertEquals(resources, managerItem.getResourcesInfo());
	}

	@Test
	public void getFormattedTime() throws ParseException {
		FederationMember managerItem = new FederationMember(resources);
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				FederationMember.ISO_8601_DATE_FORMAT);
		Date date = dateFormat.parse(DATE);
		Long lastTime = date.getTime();
		managerItem.setLastTime(lastTime);
		Assert.assertEquals(DATE, managerItem.getFormattedTime());
	}
}
