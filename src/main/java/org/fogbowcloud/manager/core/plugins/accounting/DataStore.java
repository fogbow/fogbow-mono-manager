package org.fogbowcloud.manager.core.plugins.accounting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.h2.jdbcx.JdbcConnectionPool;

public class DataStore {

	protected static final String TABLE_NAME = "usage";
	protected static final String MEMBER_ID = "member_id";
	protected static final String CONSUMED = "consumed";
	protected static final String DONATED = "donated";

	private static final Logger LOGGER = Logger.getLogger(DataStore.class);

	private String dataStoreURL;
	private JdbcConnectionPool cp;

	public DataStore(Properties properties) {
		this.dataStoreURL = properties
				.getProperty(ConfigurationConstants.ACCOUNTING_DATASTORE_URL_KEY);
		
		Statement statement = null;
		Connection connection = null;
		try {			
			LOGGER.debug("DatastoreURL: " + dataStoreURL);
			
			Class.forName("org.h2.Driver");
			this.cp = JdbcConnectionPool.create(dataStoreURL, "sa", "");
			
			connection = getConnection();
			statement = connection.createStatement();
			statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + "("
					+ MEMBER_ID + " VARCHAR(255) PRIMARY KEY, " + CONSUMED
					+ " DOUBLE, " + DONATED + " DOUBLE)");
			statement.close();

		} catch (Exception e) {
			LOGGER.error("Error while initializing the DataStore.", e);
		} finally {
			close(statement, connection);
		}
	}

	private static final String UPDATE_USAGE_SQL = "UPDATE usage SET consumed = consumed + ?, donated = donated + ? WHERE member_id = ?";
	private static final String INSERT_USAGE_SQL = "INSERT INTO usage VALUES(?, ?, ?)";

	public boolean updateMembers(Map<String, ResourceUsage> members) {		
		LOGGER.debug("Updating members usage into database. members=" + members);
		
		PreparedStatement updateStatement = null;
		PreparedStatement insertStatement = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);

			List<String> memberIds = new ArrayList<String>();
			memberIds.addAll(members.keySet());
			Map<String, ResourceUsage> membersOnStore = getUsage(memberIds);

			insertStatement = connection.prepareStatement(INSERT_USAGE_SQL);
			updateStatement = connection.prepareStatement(UPDATE_USAGE_SQL);

			for (String memberId : memberIds) {
				if (!membersOnStore.keySet().contains(memberId)) {
					ResourceUsage resourceUsage = members.get(memberId);
					insertStatement.setString(1, resourceUsage.getMemberId());
					insertStatement.setDouble(2, resourceUsage.getConsumed());
					insertStatement.setDouble(3, resourceUsage.getDonated());
					insertStatement.addBatch();
				} else {
					ResourceUsage resourceUsage = members.get(memberId);
					updateStatement.setDouble(1, resourceUsage.getConsumed());
					updateStatement.setDouble(2, resourceUsage.getDonated());
					updateStatement.setString(3, resourceUsage.getMemberId());
					updateStatement.addBatch();
				}
			}

			if (hasBatchExecutionError(insertStatement.executeBatch())
					| hasBatchExecutionError(updateStatement.executeBatch())) {
				connection.rollback();
				return false;
			}

			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't account members' usage.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
			return false;
		} finally {
			close(updateStatement, connection);

		}
	}

	private boolean hasBatchExecutionError(int[] executeBatch) {
		for (int i : executeBatch) {
			if (i == PreparedStatement.EXECUTE_FAILED) {
				return true;
			}
		}
		return false;
	}

	public Map<String, ResourceUsage> getUsage(List<String> memberIds) {
		LOGGER.debug("Getting usage of members: " + memberIds);
		if (memberIds == null || memberIds.isEmpty()) {
			return new HashMap<String, ResourceUsage>();
		}

		Statement statement = null;
		Connection conn = null;
		try {
			conn = getConnection();
			statement = conn.createStatement();

			String sql = "select * from " + TABLE_NAME + " where ";

			for (int i = 0; i < memberIds.size(); i++) {
				sql += MEMBER_ID + "='" + memberIds.get(i) + "'";
				if (i < memberIds.size() - 1)
					sql += " or ";
			}
			statement.execute(sql);
			ResultSet rs = statement.getResultSet();
			HashMap<String, ResourceUsage> map = new HashMap<String, ResourceUsage>();
			while (rs.next()) {
				ResourceUsage resourceUsage = new ResourceUsage(
						rs.getString(MEMBER_ID));
				resourceUsage.addConsumption(rs.getDouble(CONSUMED));
				resourceUsage.addDonation(rs.getDouble(DONATED));
				map.put(rs.getString(MEMBER_ID), resourceUsage);
			}
			LOGGER.debug("Map toReturn: " + map);
			return map;
		} catch (SQLException e) {
			LOGGER.error("Couldn't get members' usage.", e);
			return null;
		} finally {
			close(statement, conn);
		}
	}

	/**
	 * @return the connection
	 * @throws SQLException
	 */
	public Connection getConnection() throws SQLException {
		try {
			return cp.getConnection();
		} catch (SQLException e) {
			LOGGER.error(
					"Error while getting a new connection from the connection pool.",
					e);
			throw e;
		}
	}

	private void close(Statement statement, Connection conn) {
		if (statement != null) {
			try {
				if (!statement.isClosed()) {
					statement.close();
				}
			} catch (SQLException e) {
				LOGGER.error("Couldn't close statement");
			}
		}

		if (conn != null) {
			try {
				if (!conn.isClosed()) {
					conn.close();
				}
			} catch (SQLException e) {
				LOGGER.error("Couldn't close connection");
			}
		}
	}

	public void dispose(){
		cp.dispose();
	}

}
