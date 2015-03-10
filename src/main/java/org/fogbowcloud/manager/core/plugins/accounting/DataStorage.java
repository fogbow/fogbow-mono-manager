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

import org.h2.jdbcx.JdbcConnectionPool;

public class DataStorage {

	private static final String DATA_STORAGE_TABLE_NAME = "reputationTable";
	
	private static final String DATA_STORAGE_PATH = "data_storage_path";	
	private static final String DATA_STORAGE_FIELD_MEMBER_ID = "member_id";
	private static final String DATA_STORAGE_FIELD_CONSUMED = "consumed";
	private static final String DATA_STORAGE_FIELD_DONATED = "donated";

	private String dataStoragePath;
	private String dataStorageTableName;
	private Connection connection;

	public DataStorage(Properties properties) {
		this.dataStoragePath = properties.getProperty(DATA_STORAGE_PATH);
		this.dataStorageTableName = DataStorage.DATA_STORAGE_TABLE_NAME;

		try {
			
			Class.forName("org.h2.Driver");
			JdbcConnectionPool cp = JdbcConnectionPool.create("jdbc:h2:"+ this.dataStoragePath, "sa", "");
			this.connection = cp.getConnection();

			Statement stat = this.connection.createStatement();
			stat.execute("create table if not exists " + this.dataStorageTableName
					+ "(" + DataStorage.DATA_STORAGE_FIELD_MEMBER_ID
					+ " varchar(255) primary key, "
					+ DataStorage.DATA_STORAGE_FIELD_CONSUMED + " double, "
					+ DataStorage.DATA_STORAGE_FIELD_DONATED + " double)");
			stat.close();

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void insertNewMember(String memberId, double consumed, double donated) throws SQLException{
		
		Statement stat = this.connection.createStatement();
		stat.execute("insert into " + this.dataStorageTableName + " values('"
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
	        
	        String sql = "update " + this.dataStorageTableName + " set "
					+ DataStorage.DATA_STORAGE_FIELD_CONSUMED + "="
					+ DataStorage.DATA_STORAGE_FIELD_CONSUMED + "+'" + resourceUsage.getConsumed()
					+ "', " + DataStorage.DATA_STORAGE_FIELD_DONATED + "="
					+ DataStorage.DATA_STORAGE_FIELD_DONATED + "+'" + resourceUsage.getDonated()
					+ "' where " + DataStorage.DATA_STORAGE_FIELD_MEMBER_ID + "='"
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
		
		String sql = "select * from " + this.dataStorageTableName + " where ";
		
		for(int i = 0; i < memberIds.size(); i++){
			sql += DataStorage.DATA_STORAGE_FIELD_MEMBER_ID
					+ "='" + memberIds.get(i) + "'";
			if(i < memberIds.size()-1)
				sql += " or ";
		}
		
		statement.execute(sql);
		ResultSet rs = statement.getResultSet();
		
		HashMap<String, ResourceUsage> map = new HashMap<String, ResourceUsage>();
		while(rs.next()){
			double donated = rs.getDouble(DataStorage.DATA_STORAGE_FIELD_DONATED);
			double consumed = rs.getDouble(DataStorage.DATA_STORAGE_FIELD_CONSUMED);
			String memberId = rs.getString(DataStorage.DATA_STORAGE_FIELD_MEMBER_ID);
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
	 * @return the dataStorageFieldMemberId
	 */
	public static String getDatabaseFieldMemberId() {
		return DATA_STORAGE_FIELD_MEMBER_ID;
	}

	/**
	 * @return the dataStorageFieldConsumed
	 */
	public static String getDatabaseFieldConsumed() {
		return DATA_STORAGE_FIELD_CONSUMED;
	}

	/**
	 * @return the dataStorageFieldDonated
	 */
	public static String getDatabaseFieldDonated() {
		return DATA_STORAGE_FIELD_DONATED;
	}

	/**
	 * @return the dataStorageTableName
	 */
	public String getDatabaseTableName() {
		return dataStorageTableName;
	}

	/**
	 * @return the connection
	 */
	public Connection getConnection() {
		return connection;
	}
	


}
