package org.fogbowcloud.manager.core.plugins.accounting;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class Database {

	private static final String DATABASE_TABLE_NAME = "reputationTable";
	
	private static final String DATABASE_PATH = "database_path";	
	private static final String DATABASE_FIELD_MEMBER_ID = "member_id";
	private static final String DATABASE_FIELD_CONSUMED = "consumed";
	private static final String DATABASE_FIELD_DONATED = "donated";

	private String databasePath;
	private String databaseTableName;
	private Connection connection;

	public Database(Properties properties) {
		this.databasePath = properties.getProperty(DATABASE_PATH);
		this.databaseTableName = Database.DATABASE_TABLE_NAME;

		try {
			Class.forName("org.h2.Driver");
			this.connection = DriverManager.getConnection("jdbc:h2:"
					+ this.databasePath, "sa", "");

			Statement stat = this.connection.createStatement();
			stat.execute("create table if not exists " + this.databaseTableName
					+ "(" + Database.DATABASE_FIELD_MEMBER_ID
					+ " varchar(255) primary key, "
					+ Database.DATABASE_FIELD_CONSUMED + " double, "
					+ Database.DATABASE_FIELD_DONATED + " double)");
			stat.close();

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void open() {
		try {
			this.connection = DriverManager.getConnection("jdbc:h2:"
					+ this.databasePath, "sa", "");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			this.connection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void insertNewMember(String memberId, double consumed, double donated) throws SQLException{
		
		Statement stat = this.connection.createStatement();
		stat.execute("insert into " + this.databaseTableName + " values('"
				+ memberId + "', " + consumed + ", " + donated + ")");
		stat.close();
		
	}
	
	public void updateMembers(Map<String, ResourceUsage> members) throws SQLException {
		
		
		Statement statement = this.connection.createStatement();
		
		Iterator<Entry<String, ResourceUsage>> it = members.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pair = (Map.Entry)it.next();
	        String memberId = (String) pair.getKey();
	        ResourceUsage resourceUsage = (ResourceUsage) pair.getValue();
	        
	        String sql = "update " + this.databaseTableName + " set "
					+ Database.DATABASE_FIELD_CONSUMED + "="
					+ Database.DATABASE_FIELD_CONSUMED + "+'" + resourceUsage.getConsumed()
					+ "', " + Database.DATABASE_FIELD_DONATED + "="
					+ Database.DATABASE_FIELD_DONATED + "+'" + resourceUsage.getDonated()
					+ "' where " + Database.DATABASE_FIELD_MEMBER_ID + "='"
					+ memberId + "'";
	        statement.addBatch(sql);
	        
	        it.remove(); // avoids a ConcurrentModificationException
	    }
	    
	    int [] statementSuccessful = statement.executeBatch();
		for (int i : statementSuccessful) {
			if(i <= 0)
				throw new SQLException("Some (or one) of updates executed successfully."); 
		}
	    statement.close();
	}
	
	public Map<String, ResourceUsage> getUsage(List <String> memberIds) throws SQLException{
		
		Statement statement = this.connection.createStatement();
		
		String sql = "select * from " + this.databaseTableName + " where ";
		
		for(int i = 0; i < memberIds.size(); i++){
			sql += Database.DATABASE_FIELD_MEMBER_ID
					+ "='" + memberIds.get(i) + "'";
			if(i < memberIds.size()-1)
				sql += " or ";
		}
		
		statement.execute(sql);
		ResultSet rs = statement.getResultSet();
		
		HashMap<String, ResourceUsage> map = new HashMap<String, ResourceUsage>();
		while(rs.next()){
			double donated = rs.getDouble(Database.DATABASE_FIELD_DONATED);
			double consumed = rs.getDouble(Database.DATABASE_FIELD_CONSUMED);
			String memberId = rs.getString(Database.DATABASE_FIELD_MEMBER_ID);
			ResourceUsage resourceUsage = new ResourceUsage(memberId);
			resourceUsage.addConsumption(consumed);
			resourceUsage.addDonation(donated);
			map.put(memberId, resourceUsage);
		}		
		statement.close();
		
		return map;
	}
	
	public ResultSet query(Statement statement, String sql){
		try {
			ResultSet rs = statement.executeQuery(sql);
			return rs;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @return the databaseFieldMemberId
	 */
	public static String getDatabaseFieldMemberId() {
		return DATABASE_FIELD_MEMBER_ID;
	}

	/**
	 * @return the databaseFieldConsumed
	 */
	public static String getDatabaseFieldConsumed() {
		return DATABASE_FIELD_CONSUMED;
	}

	/**
	 * @return the databaseFieldDonated
	 */
	public static String getDatabaseFieldDonated() {
		return DATABASE_FIELD_DONATED;
	}

	/**
	 * @return the databaseTableName
	 */
	public String getDatabaseTableName() {
		return databaseTableName;
	}

	/**
	 * @return the connection
	 */
	public Connection getConnection() {
		return connection;
	}
	


}
