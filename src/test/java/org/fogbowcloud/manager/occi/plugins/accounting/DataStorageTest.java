package org.fogbowcloud.manager.occi.plugins.accounting;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.fogbowcloud.manager.core.plugins.accounting.DataStorage;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

public class DataStorageTest {
	
	Properties properties = null;
	DataStorage db = null; 
	
	@Before
	public void initialize() {
		
		System.out.println("creating table");
		
		properties = new Properties();
		properties.put("data_storage_path", System.getProperty("user.dir")+"/reputations");	
		
		db = new DataStorage(properties);
	}
	
	@AfterClass
	public static void tearDown(){
		File file = new File(System.getProperty("user.dir")+"/reputations.mv.db");
		
		if(file.delete()){
			System.out.println(file.getName() + " is deleted!");
		}
	}
	

	@Test
	public void testInsert() throws SQLException {
		
		String memberId = "m1";
		double consumed = 0, donated = 0;
		db.insertNewMember(memberId, consumed, donated);
		
		
		String sql = "select * from " + db.getDatabaseTableName()+ " where " + DataStorage.getDatabaseFieldMemberId()+ "='" + memberId + "'";
		Statement statement = db.getConnection().createStatement();
		ResultSet rs = db.query(statement, sql);
		rs.next();
		Assert.assertTrue(consumed == rs.getDouble(DataStorage.getDatabaseFieldConsumed()));
				
		statement.close();
	}
	
	@Test(expected=SQLException.class)
	public void testInsertSQLException() throws SQLException {
		
		String memberId = "m2";
		db.insertNewMember(memberId, 0, 0);
		
		db.insertNewMember(memberId, 10, 10);
				
	}	
	
	@Test
	public void testUpdateMembers() throws SQLException{
		
		String memberId = "m3";
		double consumed = 2, donated = 2;
		db.insertNewMember(memberId, consumed, donated);
		
		Map<String, ResourceUsage> members = new HashMap<String, ResourceUsage>();
		ResourceUsage resourceUsage = new ResourceUsage(memberId);
		resourceUsage.addConsumption(10);
		resourceUsage.addDonation(10);
		members.put(memberId, resourceUsage);
		
		db.updateMembers(members);
		
		String sql = "select * from " + db.getDatabaseTableName()+ " where " + DataStorage.getDatabaseFieldMemberId()+ "='" + memberId + "'";
		Statement statement = db.getConnection().createStatement();
		ResultSet rs = db.query(statement, sql);
		rs.next();
		Assert.assertTrue(12== rs.getDouble(DataStorage.getDatabaseFieldConsumed()));
		Assert.assertTrue(12== rs.getDouble(DataStorage.getDatabaseFieldDonated()));
		
		statement.close();
		
	}
	
	
	@Test(expected=SQLException.class)
	public void testUpdateMembersSQLException() throws SQLException{		
		
		Map<String, ResourceUsage> members = new HashMap<String, ResourceUsage>();
		ResourceUsage resourceUsage = new ResourceUsage("m5");
		resourceUsage.addConsumption(0);
		resourceUsage.addDonation(0);
		members.put("m5", resourceUsage);
		
		db.updateMembers(members);
	}
	
	
	@Test
	public void testGetUsage() throws SQLException{
		
		List <String> memberIds = new ArrayList<String>();
		memberIds.add("m6");
		memberIds.add("m7");
				
		String memberId1 = "m6";
		double consumed1 = 2, donated1 = 2;
		db.insertNewMember(memberId1, consumed1, donated1);
		
		String memberId2 = "m7";
		double consumed2 = 4, donated2 = 4;
		db.insertNewMember(memberId2, consumed2, donated2);
		
		Map<String, ResourceUsage> map = db.getUsage(memberIds);
		
		Assert.assertTrue(map.get(memberId1).getConsumed() == consumed1);
		Assert.assertTrue(map.get(memberId2).getConsumed() == consumed2);
		Assert.assertTrue(map.get(memberId1).getDonated() == donated1);
		Assert.assertTrue(map.get(memberId2).getDonated() == donated2);
		
		
		memberIds = new ArrayList<String>();
		memberIds.add("m8");
		memberIds.add("m9");
		
		map = db.getUsage(memberIds);
		Assert.assertTrue(map.isEmpty());
		
	}

}