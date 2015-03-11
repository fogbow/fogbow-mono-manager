package org.fogbowcloud.manager.occi.plugins.accounting;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.accounting.DataStore;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDataStore {
	
	private static final Logger LOGGER = Logger.getLogger(TestDataStore.class);
	
	private final double acceptedError = 0.01; 
	
	Properties properties = null;
	DataStore db = null; 
	
	@Before
	public void initialize() {		
		LOGGER.debug("Creating data store.");
		properties = new Properties();
		properties.put("accounting_datastore_path", System.getProperty("user.dir")+"/reputations");
		db = new DataStore(properties);
	}
	
	@After
	public void tearDown(){
		File file = new File(System.getProperty("user.dir")+"/reputations.mv.db");		
		if(file.delete()){
			LOGGER.debug("Data store is removed.");
		}
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
		
		String sql = "select * from " + db.getDatabaseTableName()+ " where " + DataStore.getDatabaseFieldMemberId()+ "='" + memberIdM3 + "'";
		ResultSet rs = db.getConnection().createStatement().executeQuery(sql);
		rs.next();
		Assert.assertEquals(10, rs.getDouble(DataStore.getDatabaseFieldConsumed()), acceptedError);	
		Assert.assertEquals(10, rs.getDouble(DataStore.getDatabaseFieldDonated()), acceptedError);
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
		
		String sql = "select * from " + db.getDatabaseTableName()+ " where " + DataStore.getDatabaseFieldMemberId()+ "='" + memberIdM5 + "'";
		ResultSet rs = db.getConnection().createStatement().executeQuery(sql);
		rs.next();
		Assert.assertEquals(5, rs.getDouble(DataStore.getDatabaseFieldConsumed()), acceptedError);	
		Assert.assertEquals(5, rs.getDouble(DataStore.getDatabaseFieldDonated()), acceptedError); 
	}
	
	@Test
	public void testGetUsageEmptyList() throws SQLException{
		List <String> memberIds = new ArrayList<String>();
		Assert.assertTrue(db.getUsage(memberIds).isEmpty());
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
		
		members = db.getUsage(memberIds);
		Assert.assertEquals(5, members.get(memberIdM6).getConsumed(), acceptedError);
		Assert.assertEquals(5, members.get(memberIdM6).getDonated(), acceptedError);
		Assert.assertEquals(10, members.get(memberIdM7).getConsumed(), acceptedError);
		Assert.assertEquals(10, members.get(memberIdM7).getDonated(), acceptedError);
	}

}