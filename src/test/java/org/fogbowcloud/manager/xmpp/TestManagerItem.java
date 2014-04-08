package org.fogbowcloud.manager.xmpp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.fogbowcloud.manager.xmpp.core.ManagerItem;
import org.fogbowcloud.manager.xmpp.core.ResourcesInfo;
import org.fogbowcloud.manager.xmpp.model.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestManagerItem {

	private ResourcesInfo resources;
	private String DATE = "2000-10-31T01:30:00.000+0000";

	@Before
	public void setUp() {
		resources = ManagerTestHelper.getResources();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidResources() {
		new ManagerItem(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNullResourceId() {
		new ResourcesInfo(null, "cpuIdle",
				"cpuInUse", "memIdle", "memInUse");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesCpuIdle() {
		new ResourcesInfo("id", null,
				"cpuInUse", "memIdle", "memInUse");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesCpuInUse() {
		new ResourcesInfo("id", "CpuIdle",
				null, "memIdle", "memInUse");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesMemIdle() {
		new ResourcesInfo("id", "CpuIdle",
				"cpuInUse", null, "memInUse");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesMemInUse() {
		new ResourcesInfo("id", "CpuIdle",
				"cpuInUse", "memIdle", null);
	}
	
	@Test
	public void testConstructor() {
		ManagerItem managerItem = new ManagerItem(resources);
		Assert.assertEquals(resources, managerItem.getResourcesInfo());
	}

	@Test
	public void getFormattedTime() throws ParseException {
		ManagerItem managerItem = new ManagerItem(resources);
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				ManagerItem.ISO_8601_DATE_FORMAT);
		Date date = dateFormat.parse(DATE);
		Long lastTime = date.getTime();
		managerItem.setLastTime(lastTime);
		Assert.assertEquals(DATE, managerItem.getFormattedTime());
	}
}
