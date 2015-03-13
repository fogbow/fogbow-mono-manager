package org.fogbowcloud.manager.core.plugins.accounting;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.accounting.DataStore;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDataStore {
	
	private static final Logger LOGGER = Logger.getLogger(TestDataStore.class);
	
	private final double ACCEPTABLE_ERROR = 0.01; 
	private final String DATASTORE_PATH = "src/test/resources/accounting/";
	
	Properties properties = null;
	DataStore db = null; 
	
	@Before
	public void initialize() {		
		LOGGER.debug("Creating data store.");
		properties = new Properties();
		properties.put(ConfigurationConstants.ACCOUNTING_DATASTORE_URL_KEY, "jdbc:h2:mem:"
				+ new File(DATASTORE_PATH).getAbsolutePath() + "usage");

		db = new DataStore(properties);
	}
	
	@After
	public void tearDown() throws IOException{
		FileUtils.cleanDirectory(new File (DATASTORE_PATH));
	}
	
	@Test
	public void testUpdateMembersInsertCase() throws SQLException{
		
		String memberIdM3 = "m3";
		ResourceUsage resourceUsageM3 = new ResourceUsage(memberIdM3);
		resourceUsageM3.addConsumption(10);
		resourceUsageM3.addDonation(10);
		
		Map<String, ResourceUsage> members = new HashMap<String, ResourceUsage>();
		members.put(memberIdM3, resourceUsageM3);
		
		Assert.assertTrue(db.updateMembers(members));
		
		String sql = "select * from " + DataStore.MEMBER_TABLE_NAME + " where " + DataStore.MEMBER_ID+ "='" + memberIdM3 + "'";
		ResultSet rs = db.getConnection().createStatement().executeQuery(sql);
		rs.next();
		Assert.assertEquals(10, rs.getDouble(DataStore.CONSUMED), ACCEPTABLE_ERROR);	
		Assert.assertEquals(10, rs.getDouble(DataStore.DONATED), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testUpdateMembersNormalCase() throws SQLException{
		
		Map<String, ResourceUsage> members = new HashMap<String, ResourceUsage>();
		String memberIdM5 = "m5";
		ResourceUsage resourceUsageM5 = new ResourceUsage(memberIdM5);
		resourceUsageM5.addConsumption(0);
		resourceUsageM5.addDonation(0);
		members.put(memberIdM5, resourceUsageM5);		
		Assert.assertTrue(db.updateMembers(members));
		
		resourceUsageM5.addConsumption(5);
		resourceUsageM5.addDonation(5);
		members.put(memberIdM5, resourceUsageM5);		
		Assert.assertTrue(db.updateMembers(members));
		
		String sql = "select * from " + DataStore.MEMBER_TABLE_NAME + " where " + DataStore.MEMBER_ID + "='" + memberIdM5 + "'";
		ResultSet rs = db.getConnection().createStatement().executeQuery(sql);
		rs.next();
		Assert.assertEquals(5, rs.getDouble(DataStore.CONSUMED), ACCEPTABLE_ERROR);	
		Assert.assertEquals(5, rs.getDouble(DataStore.DONATED), ACCEPTABLE_ERROR); 
	}
	
	@Test
	public void testGetUsageEmptyList() throws SQLException{
		List <String> memberIds = new ArrayList<String>();
		Assert.assertTrue(db.getMemberUsage(memberIds).isEmpty());
	}
	
	@Test
	public void testGetUsage() throws SQLException{
		
		String memberIdM6 = "m6";
		ResourceUsage resourceUsageM6 = new ResourceUsage(memberIdM6);
		resourceUsageM6.addConsumption(5);
		resourceUsageM6.addDonation(5);
		String memberIdM7 = "m7";
		ResourceUsage resourceUsageM7 = new ResourceUsage(memberIdM7);
		resourceUsageM7.addConsumption(10);
		resourceUsageM7.addDonation(10);
		
		Map<String, ResourceUsage> members = new HashMap<String, ResourceUsage>();
		members.put(memberIdM6, resourceUsageM6);
		members.put(memberIdM7, resourceUsageM7);
		
		Assert.assertTrue(db.updateMembers(members));
		
		List <String> memberIds = new ArrayList<String>();
		memberIds.add(memberIdM6);
		memberIds.add(memberIdM7);		
		
		members = db.getMemberUsage(memberIds);
		Assert.assertEquals(5, members.get(memberIdM6).getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(5, members.get(memberIdM6).getDonated(), ACCEPTABLE_ERROR);
		Assert.assertEquals(10, members.get(memberIdM7).getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(10, members.get(memberIdM7).getDonated(), ACCEPTABLE_ERROR);
	}

}