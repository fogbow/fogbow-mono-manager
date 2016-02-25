package org.fogbowcloud.manager.core.plugins.accounting;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

public class AccountingDataStore {

	public static final String ACCOUNTING_DATASTORE_URL = "accounting_datastore_url";
	public static final String ACCOUNTING_DATASTORE_URL_DEFAULT = "jdbc:sqlite:/tmp/usage";
	public static final String ACCOUNTING_DATASTORE_SQLITE_DRIVER = "org.sqlite.JDBC";
	
	protected static final String USAGE_TABLE_NAME = "usage";
	protected static final String USER_COL = "user";
	protected static final String REQUESTING_MEMBER_COL = "requesting_member";
	protected static final String PROVIDING_MEMBER_COL = "providing_member";
	protected static final String USAGE_COL = "usage";

	private String dataStoreURL;

	private static final Logger LOGGER = Logger.getLogger(AccountingDataStore.class);
	
	public AccountingDataStore(Properties properties) {		
		this.dataStoreURL = properties.getProperty(ACCOUNTING_DATASTORE_URL, ACCOUNTING_DATASTORE_URL_DEFAULT);

		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("DatastoreURL: " + dataStoreURL);

			Class.forName(ACCOUNTING_DATASTORE_SQLITE_DRIVER);

			connection = getConnection();
			statement = connection.createStatement();
			statement
					.execute("CREATE TABLE IF NOT EXISTS usage("
							+ "user VARCHAR(255) NOT NULL, "
							+ "requesting_member VARCHAR(255) NOT NULL, "
							+ "providing_member VARCHAR(255) NOT NULL, "
							+ "usage DOUBLE,"
							+ "PRIMARY KEY (user, requesting_member, providing_member)"
							+ ")");
			statement.close();
		} catch (Exception e) {
			LOGGER.error("Error while initializing the DataStore.", e);
		} finally {
			close(statement, connection);
		}
	}
	
	private static final String UPDATE_MEMBER_USAGE_SQL = "UPDATE " + USAGE_TABLE_NAME
			+ " SET usage = usage + ? WHERE user = ? AND requesting_member = ? AND providing_member = ?";
	
	private static final String INSERT_MEMBER_USAGE_SQL = "INSERT INTO " + USAGE_TABLE_NAME
			+ " VALUES(?, ?, ?, ?)";
	
	public boolean update(List<AccountingInfo> usage) {
		LOGGER.debug("Updating usage into database.");
		LOGGER.debug("Usage=" + usage);

		if (usage == null) {
			LOGGER.warn("Members and users must not be null.");
			return false;
		}
		
		PreparedStatement updateMemberStatement = null;
		PreparedStatement insertMemberStatement = null;
		
		Connection connection = null;

		try {
			connection = getConnection();
			connection.setAutoCommit(false);

			insertMemberStatement = connection.prepareStatement(INSERT_MEMBER_USAGE_SQL);
			updateMemberStatement = connection.prepareStatement(UPDATE_MEMBER_USAGE_SQL);
		
			addMemberStatements(usage, updateMemberStatement, insertMemberStatement);

			if (hasBatchExecutionError(insertMemberStatement.executeBatch())
					| hasBatchExecutionError(updateMemberStatement.executeBatch())) {
				connection.rollback();
				return false;
			}

			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't account usage.", e);
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
	
	private void addMemberStatements(List<AccountingInfo> usage,
			PreparedStatement updateMemberStatement, PreparedStatement insertMemberStatement)
			throws SQLException {
		
		List<AccountingEntryKey> entryKeys = getEntryKeys();

		LOGGER.debug("Databa entry keys=" + entryKeys);
		for (AccountingInfo accountingInfo : usage) {
			// inserting new usage entry
			if (!entryKeys.contains(new AccountingEntryKey(accountingInfo.getUser(), accountingInfo
					.getRequestingMember(), accountingInfo.getProvidingMember()))) {

				insertMemberStatement.setString(1, accountingInfo.getUser());
				insertMemberStatement.setString(2, accountingInfo.getRequestingMember());
				insertMemberStatement.setString(3, accountingInfo.getProvidingMember());
				insertMemberStatement.setDouble(4, accountingInfo.getUsage());
				insertMemberStatement.addBatch();
				
			} else { // updating an existing entry
				updateMemberStatement.setDouble(1, accountingInfo.getUsage());
				updateMemberStatement.setString(2, accountingInfo.getUser());
				updateMemberStatement.setString(3, accountingInfo.getRequestingMember());
				updateMemberStatement.setString(4, accountingInfo.getProvidingMember());
				updateMemberStatement.addBatch();
			}
		}
	}

	private static final String SELECT_ALL_USAGE_SQL = "SELECT * FROM " + USAGE_TABLE_NAME;
		
	private List<AccountingEntryKey> getEntryKeys() {
		LOGGER.debug("Getting database keys.");

		Statement statement = null;
		Connection conn = null;
		try {
			conn = getConnection();
			statement = conn.createStatement();

			statement.execute(SELECT_ALL_USAGE_SQL);
			ResultSet rs = statement.getResultSet();

			List<AccountingEntryKey> dbKeys = new ArrayList<AccountingEntryKey>();
			while (rs.next()) {
				dbKeys.add(new AccountingEntryKey(rs.getString(USER_COL), rs
						.getString(REQUESTING_MEMBER_COL), rs.getString(PROVIDING_MEMBER_COL)));
			}
			return dbKeys;
		} catch (SQLException e) {
			LOGGER.error("Couldn't get keys from DB.", e);
			return null;
		}
	}

	public List<AccountingInfo> getAccountingInfo() {
		LOGGER.debug("Getting AccounintgInfo...");
		Statement statement = null;
		Connection conn = null;
		try {
			conn = getConnection();
			statement = conn.createStatement();

			statement.execute(SELECT_ALL_USAGE_SQL);
			return createAccounting(statement.getResultSet());
		} catch (SQLException e) {
			LOGGER.error("Couldn't get keys from DB.", e);
			return null;
		}	
	}

	private static final String SELECT_SPECIFIC_USAGE_SQL = "SELECT * FROM " + USAGE_TABLE_NAME
			+ " WHERE user = ? AND requesting_member = ? AND providing_member = ?";
	
	private AccountingInfo getAccountingInfo(AccountingEntryKey key) {
		LOGGER.debug("Getting accountingInfo to " + key);

		AccountingEntryKey entryKey = (AccountingEntryKey) key;

		PreparedStatement statement = null;
		Connection conn = null;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(SELECT_SPECIFIC_USAGE_SQL);
			statement.setString(1, entryKey.getUser());
			statement.setString(2, entryKey.getRequestingMember());
			statement.setString(3, entryKey.getProvidingMember());
			statement.execute();

			ResultSet rs = statement.getResultSet();

			if (rs.next()) {
				String user = rs.getString(USER_COL);
				String requestingMember = rs.getString(REQUESTING_MEMBER_COL);
				String providingMember = rs.getString(PROVIDING_MEMBER_COL);
				double usage = rs.getDouble(USAGE_COL);

				AccountingInfo accountingInfo = new AccountingInfo(user, requestingMember,
						providingMember);
				accountingInfo.addConsuption(usage);
				return accountingInfo;
			}
		} catch (SQLException e) {
			LOGGER.error("Couldn't get keys from DB.", e);
			return null;
		} finally {
			close(statement, conn);
		}

		return null;
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

	protected List<AccountingInfo> createAccounting(ResultSet rs) {
		List<AccountingInfo> accounting = new ArrayList<AccountingInfo>();
		try {
			while (rs.next()) {
				String user = rs.getString(USER_COL);
				String requestingMember = rs.getString(REQUESTING_MEMBER_COL);
				String providingMember = rs.getString(PROVIDING_MEMBER_COL);
				AccountingInfo userAccounting = new AccountingInfo(user, requestingMember,
						providingMember);
				userAccounting.addConsuption(rs.getDouble(USAGE_COL));
				accounting.add(userAccounting);
			}
		} catch (SQLException e) {
			LOGGER.error("Error while creating accounting from ResultSet.", e);
			return null;
		}
		return accounting;
	}

	public AccountingInfo getAccountingInfo(String user, String requestingMember,
			String providingMember) {
		return getAccountingInfo(new AccountingEntryKey(user, requestingMember, providingMember));
	}
}

class AccountingEntryKey {	
	private String user;
	private String requestingMember;
	private String providingMember;
	
	public AccountingEntryKey(String user, String requestingMember, String providingMember) {
		this.user = user;
		this.requestingMember = requestingMember;
		this.providingMember = providingMember;
	}
	
	public String getUser() {
		return user;
	}

	public String getRequestingMember() {
		return requestingMember;
	}

	public String getProvidingMember() {
		return providingMember;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AccountingEntryKey) {
			AccountingEntryKey other = (AccountingEntryKey) obj;
			return getUser().equals(other.getUser())
					&& getProvidingMember().equals(other.getProvidingMember())
					&& getRequestingMember().equals(other.getRequestingMember());
		}
		return false;
	}
	
	@Override
	public String toString() {
		return "user=" + user + "; requestigMember=" + requestingMember + "; providingMember="
				+ providingMember;
	}
}
