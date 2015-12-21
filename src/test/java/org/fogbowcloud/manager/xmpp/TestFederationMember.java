package org.fogbowcloud.manager.xmpp;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestFederationMember {

	private ResourcesInfo resources;
	private String DATE = "2000-10-31T01:30:00.000+0000";
	ManagerTestHelper managerTestHelper;
	
	@Before
	public void setUp() throws CertificateException, IOException {
		managerTestHelper = new ManagerTestHelper();
		resources = managerTestHelper.getResources();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidResources() {
		ResourcesInfo resourcesInfo = null;
		new FederationMember(resourcesInfo);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesCpuIdle() throws CertificateException, IOException {
		new ResourcesInfo("id", null,
				"cpuInUse", "memIdle", "memInUse", "", "");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesCpuInUse() throws CertificateException, IOException {
		new ResourcesInfo("id", "CpuIdle",
				null, "memIdle", "memInUse", "", "");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesMemIdle() throws CertificateException, IOException {
		new ResourcesInfo("id", "CpuIdle",
				"cpuInUse", null, "memInUse", "", "");
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testNullResourcesMemInUse() throws CertificateException, IOException {
		new ResourcesInfo("id", "CpuIdle",
				"cpuInUse", "memIdle", null, "", "");
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
