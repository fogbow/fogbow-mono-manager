package org.fogbowcloud.manager.core.plugins.accounting;

import java.sql.Connection;
import java.sql.DriverManager;
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
import org.sqlite.SQLiteConfig;

public class DataStore {

	public static final String ACCOUNTING_DATASTORE_URL = "accounting_datastore_url";
	public static final String ACCOUNTING_DATASTORE_URL_DEFAULT = "jdbc:sqlite:/tmp/usage";
	public static final String ACCOUNTING_DATASTORE_SQLITE_DRIVER = "org.sqlite.JDBC";
	public static final String MEMBER_TABLE_NAME = "member_usage";
	public static final String USER_TABLE_NAME = "user_usage";
	public static final String MEMBER_ID = "member_id";
	public static final String USER_ID = "user_id";
	public static final String CONSUMED = "consumed";
	public static final String DONATED = "donated";

	private static final Logger LOGGER = Logger.getLogger(DataStore.class);

	private String dataStoreURL;

	public DataStore(Properties properties) {
		this.dataStoreURL = properties.getProperty(ACCOUNTING_DATASTORE_URL, ACCOUNTING_DATASTORE_URL_DEFAULT);

		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("DatastoreURL: " + dataStoreURL);

			Class.forName(ACCOUNTING_DATASTORE_SQLITE_DRIVER);

			connection = getConnection();
			statement = connection.createStatement();
			statement
					.execute("CREATE TABLE IF NOT EXISTS " + MEMBER_TABLE_NAME + "(" + MEMBER_ID
							+ " VARCHAR(255) PRIMARY KEY, " + CONSUMED + " DOUBLE, " + DONATED
							+ " DOUBLE)");
			statement.execute("CREATE TABLE IF NOT EXISTS " + USER_TABLE_NAME + "(" + USER_ID
					+ " VARCHAR(255) PRIMARY KEY, " + CONSUMED + " DOUBLE)");
			statement.close();

		} catch (Exception e) {
			LOGGER.error("Error while initializing the DataStore.", e);
		} finally {
			close(statement, connection);
		}
	}

	private static final String UPDATE_MEMBER_USAGE_SQL = "UPDATE " + MEMBER_TABLE_NAME
			+ " SET consumed = consumed + ?, donated = donated + ? WHERE member_id = ?";
	private static final String INSERT_MEMBER_USAGE_SQL = "INSERT INTO " + MEMBER_TABLE_NAME
			+ " VALUES(?, ?, ?)";

	private static final String INSERT_USER_USAGE_SQL = "INSERT INTO " + USER_TABLE_NAME
			+ " VALUES(?, ?)";
	private static final String UPDATE_USER_USAGE_SQL = "UPDATE " + USER_TABLE_NAME
			+ " SET consumed = consumed + ? WHERE user_id = ?";

	public boolean update(Map<String, ResourceUsage> members, Map<String, Double> users) {
		LOGGER.debug("Updating members usage into database.");
		LOGGER.debug("members=" + members + " users=" + users);

		if (members == null || users == null) {
			LOGGER.warn("Members and users must not be null.");
			return false;
		}

		PreparedStatement updateMemberStatement = null;
		PreparedStatement insertMemberStatement = null;
		PreparedStatement insertUserStatement = null;
		PreparedStatement updateUserStatement = null;
		Connection connection = null;

		try {
			connection = getConnection();
			connection.setAutoCommit(false);

			insertMemberStatement = connection.prepareStatement(INSERT_MEMBER_USAGE_SQL);
			updateMemberStatement = connection.prepareStatement(UPDATE_MEMBER_USAGE_SQL);
			insertUserStatement = connection.prepareStatement(INSERT_USER_USAGE_SQL);
			updateUserStatement = connection.prepareStatement(UPDATE_USER_USAGE_SQL);

			addMemberStatements(members, updateMemberStatement, insertMemberStatement);
			addUserStatements(users, insertUserStatement, updateUserStatement);

			if (hasBatchExecutionError(insertMemberStatement.executeBatch())
					| hasBatchExecutionError(updateMemberStatement.executeBatch())
					| hasBatchExecutionError(insertUserStatement.executeBatch())
					| hasBatchExecutionError(updateUserStatement.executeBatch())) {
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
			close(updateMemberStatement, connection);
			close(insertMemberStatement, connection);
			close(insertUserStatement, connection);
			close(updateUserStatement, connection);
		}
	}

	private void addUserStatements(Map<String, Double> users,
			PreparedStatement insertUserStatement, PreparedStatement updateUserStatement)
			throws SQLException {

		List<String> userIds = new ArrayList<String>();
		userIds.addAll(users.keySet());
		Map<String, Double> usersOnStore = getUsersUsage();

		for (String userId : userIds) {
			if (!usersOnStore.keySet().contains(userId)) {
				insertUserStatement.setString(1, userId);
				insertUserStatement.setDouble(2, users.get(userId));
				insertUserStatement.addBatch();
			} else {
				updateUserStatement.setDouble(1, users.get(userId));
				updateUserStatement.setString(2, userId);
				updateUserStatement.addBatch();
			}
		}
	}

	private void addMemberStatements(Map<String, ResourceUsage> members,
			PreparedStatement updateMemberStatement, PreparedStatement insertMemberStatement)
			throws SQLException {

		List<String> memberIds = new ArrayList<String>();
		memberIds.addAll(members.keySet());
		Map<String, ResourceUsage> membersOnStore = getMembersUsage();

		for (String memberId : memberIds) {
			if (!membersOnStore.keySet().contains(memberId)) {
				ResourceUsage resourceUsage = members.get(memberId);
				insertMemberStatement.setString(1, resourceUsage.getMemberId());
				insertMemberStatement.setDouble(2, resourceUsage.getConsumed());
				insertMemberStatement.setDouble(3, resourceUsage.getDonated());
				insertMemberStatement.addBatch();
			} else {
				ResourceUsage resourceUsage = members.get(memberId);
				updateMemberStatement.setDouble(1, resourceUsage.getConsumed());
				updateMemberStatement.setDouble(2, resourceUsage.getDonated());
				updateMemberStatement.setString(3, resourceUsage.getMemberId());
				updateMemberStatement.addBatch();
			}
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

	private static final String SELECT_MEMBERS_USAGE_SQL = "SELECT * FROM " + MEMBER_TABLE_NAME;

	public Map<String, ResourceUsage> getMembersUsage() {
		LOGGER.debug("Getting usage of members.");

		Statement statement = null;
		Connection conn = null;
		try {
			conn = getConnection();
			statement = conn.createStatement();

			statement.execute(SELECT_MEMBERS_USAGE_SQL);
			ResultSet rs = statement.getResultSet();
			HashMap<String, ResourceUsage> membersUsageOnStore = new HashMap<String, ResourceUsage>();
			while (rs.next()) {
				ResourceUsage resourceUsage = new ResourceUsage(rs.getString(MEMBER_ID));
				resourceUsage.addConsumption(rs.getDouble(CONSUMED));
				resourceUsage.addDonation(rs.getDouble(DONATED));
				membersUsageOnStore.put(rs.getString(MEMBER_ID), resourceUsage);
			}
			LOGGER.debug("Current members usage on data base: " + membersUsageOnStore);
			return membersUsageOnStore;
		} catch (SQLException e) {
			LOGGER.error("Couldn't get members' usage.", e);
			return null;
		} finally {
			close(statement, conn);
		}
	}

	private static final String SELECT_USERS_USAGE_SQL = "SELECT * FROM " + USER_TABLE_NAME;

	public Map<String, Double> getUsersUsage() {
		LOGGER.debug("Getting usage of users.");

		Statement statement = null;
		Connection conn = null;
		try {
			conn = getConnection();
			statement = conn.createStatement();

			statement.execute(SELECT_USERS_USAGE_SQL);
			ResultSet rs = statement.getResultSet();
			HashMap<String, Double> usersUsageOnStore = new HashMap<String, Double>();
			while (rs.next()) {
				usersUsageOnStore.put(rs.getString(USER_ID), rs.getDouble(CONSUMED));
			}
			LOGGER.debug("Current users usage on data base: " + usersUsageOnStore);
			return usersUsageOnStore;
		} catch (SQLException e) {
			LOGGER.error("Couldn't get users' usage.", e);
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
			return DriverManager.getConnection(this.dataStoreURL);
		} catch (SQLException e) {
			LOGGER.error("Error while getting a new connection from the connection pool.", e);
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

}
