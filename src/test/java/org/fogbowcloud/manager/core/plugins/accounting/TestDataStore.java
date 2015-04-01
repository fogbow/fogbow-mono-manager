package org.fogbowcloud.manager.core.plugins.accounting;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
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
		new File(DATASTORE_PATH).mkdir();
		properties = new Properties();
		properties.put("accounting_datastore_url", "jdbc:h2:mem:"
				+ new File(DATASTORE_PATH).getAbsolutePath() + "usage");

		db = new DataStore(properties);
	}
	
	@After
	public void tearDown() throws IOException{
		FileUtils.cleanDirectory(new File (DATASTORE_PATH));
		db.dispose();
	}
	
	@Test
	public void testUpdateMembersInsertCase() throws SQLException{
		
		String memberIdM3 = "m3";
		ResourceUsage resourceUsageM3 = new ResourceUsage(memberIdM3);
		resourceUsageM3.addConsumption(10);
		resourceUsageM3.addDonation(10);
		
		Map<String, ResourceUsage> members = new HashMap<String, ResourceUsage>();
		members.put(memberIdM3, resourceUsageM3);
		
		Assert.assertTrue(db.update(members, new HashMap<String, Double>()));
		
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
		Assert.assertTrue(db.update(members, new HashMap<String, Double>()));
		
		resourceUsageM5.addConsumption(5);
		resourceUsageM5.addDonation(5);
		members.put(memberIdM5, resourceUsageM5);		
		Assert.assertTrue(db.update(members, new HashMap<String, Double>()));
		
		String sql = "select * from " + DataStore.MEMBER_TABLE_NAME + " where " + DataStore.MEMBER_ID + "='" + memberIdM5 + "'";
		ResultSet rs = db.getConnection().createStatement().executeQuery(sql);
		rs.next();
		Assert.assertEquals(5, rs.getDouble(DataStore.CONSUMED), ACCEPTABLE_ERROR);	
		Assert.assertEquals(5, rs.getDouble(DataStore.DONATED), ACCEPTABLE_ERROR); 
	}
	
	@Test
	public void testUpdateUsersInsertCase() throws SQLException{		
		Map<String, Double> users = new HashMap<String, Double>();
		String userId = "userId";		
		users.put(userId, 5.0);		
		Assert.assertTrue(db.update(new HashMap<String, ResourceUsage>(), users));

		// checking if consumed is 5
		String sql = "select * from " + DataStore.USER_TABLE_NAME + " where " + DataStore.USER_ID + "='" + userId + "'";
		ResultSet rs = db.getConnection().createStatement().executeQuery(sql);

		Map<String, Double> userIdToConsumed = new HashMap<String, Double>();
		while (rs.next()) {
			userIdToConsumed.put(rs.getString(DataStore.USER_ID),rs.getDouble(DataStore.CONSUMED));
		}

		Assert.assertEquals(5, userIdToConsumed.get(userId), ACCEPTABLE_ERROR);
		Assert.assertNull(userIdToConsumed.get("userId2"));
		
		// updating user consumed 
		users.put(userId, 10.0);
		users.put("userId2", 20.0);
		Assert.assertTrue(db.update(new HashMap<String, ResourceUsage>(), users));
		
		// checking if consumed was updated and userId2 was added
		sql = "select * from " + DataStore.USER_TABLE_NAME + " where " + DataStore.USER_ID + "='" + userId + "' or " + DataStore.USER_ID + "='userId2'";
		rs = db.getConnection().createStatement().executeQuery(sql);
		
		userIdToConsumed = new HashMap<String, Double>();

		while (rs.next()) {
			userIdToConsumed.put(rs.getString(DataStore.USER_ID),rs.getDouble(DataStore.CONSUMED));
		}

		Assert.assertEquals(2, userIdToConsumed.size());
		Assert.assertEquals(15, userIdToConsumed.get(userId), ACCEPTABLE_ERROR);
		Assert.assertEquals(20, userIdToConsumed.get("userId2"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testUpdateUsersNormalCase() throws SQLException{		
		Map<String, Double> users = new HashMap<String, Double>();
		String userId = "user";		
		users.put(userId, 5.0);		
		Assert.assertTrue(db.update(new HashMap<String, ResourceUsage>(), users));

		// checking if consumed is 5
		String sql = "select * from " + DataStore.USER_TABLE_NAME + " where " + DataStore.USER_ID + "='" + userId + "'";
		ResultSet rs = db.getConnection().createStatement().executeQuery(sql);
		rs.next();
		
		Assert.assertEquals(5, rs.getDouble(DataStore.CONSUMED), ACCEPTABLE_ERROR);	
		
		// updating user consumed 
		users.put(userId, 10.0);		
		Assert.assertTrue(db.update(new HashMap<String, ResourceUsage>(), users));
		
		// checking if consumed is 15 (value must be previous + current = 5 + 10)
		sql = "select * from " + DataStore.USER_TABLE_NAME + " where " + DataStore.USER_ID + "='" + userId + "'";
		rs = db.getConnection().createStatement().executeQuery(sql);
		rs.next();
		Assert.assertEquals(15, rs.getDouble(DataStore.CONSUMED), ACCEPTABLE_ERROR); 
	}
	
	@Test
	public void testGetMemberUsageEmptyList() throws SQLException{
		Assert.assertTrue(db.getMembersUsage().isEmpty());
	}
	
	@Test
	public void testGetUserUsageEmptyList() throws SQLException{
		Assert.assertTrue(db.getUsersUsage().isEmpty());
	}
	
	@Test
	public void testGetMemberUsage() throws SQLException{
		
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
		
		Assert.assertTrue(db.update(members, new HashMap<String, Double>()));
		
		members = db.getMembersUsage();
		Assert.assertEquals(5, members.get(memberIdM6).getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(5, members.get(memberIdM6).getDonated(), ACCEPTABLE_ERROR);
		Assert.assertEquals(10, members.get(memberIdM7).getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(10, members.get(memberIdM7).getDonated(), ACCEPTABLE_ERROR);
	}

}