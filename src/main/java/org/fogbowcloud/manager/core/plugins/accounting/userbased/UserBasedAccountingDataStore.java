package org.fogbowcloud.manager.core.plugins.accounting.userbased;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingDataSatore;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.plugins.accounting.DataStore;

public class UserBasedAccountingDataStore implements AccountingDataSatore {

	public static final String ACCOUNTING_DATASTORE_URL = "accounting_consume_datastore_url";
	public static final String ACCOUNTING_DATASTORE_URL_DEFAULT = "jdbc:sqlite:/tmp/usage";
	public static final String ACCOUNTING_DATASTORE_SQLITE_DRIVER = "org.sqlite.JDBC";
	
	private String dataStoreURL;

	private static final Logger LOGGER = Logger.getLogger(UserBasedAccountingDataStore.class);
	
	public UserBasedAccountingDataStore(Properties properties) {		
		this.dataStoreURL = properties.getProperty(ACCOUNTING_DATASTORE_URL, ACCOUNTING_DATASTORE_URL_DEFAULT);

		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("DatastoreURL: " + dataStoreURL);

			Class.forName(ACCOUNTING_DATASTORE_SQLITE_DRIVER);

			connection = getConnection();
//			statement = connection.createStatement();
//			statement
//					.execute("CREATE TABLE IF NOT EXISTS " + MEMBER_TABLE_NAME + "(" + MEMBER_ID
//							+ " VARCHAR(255) PRIMARY KEY, " + CONSUMED + " DOUBLE, " + DONATED
//							+ " DOUBLE)");
//			statement.execute("CREATE TABLE IF NOT EXISTS " + USER_TABLE_NAME + "(" + USER_ID
//					+ " VARCHAR(255) PRIMARY KEY, " + CONSUMED + " DOUBLE)");
			statement.close();

		} catch (Exception e) {
			LOGGER.error("Error while initializing the DataStore.", e);
		} finally {
			close(statement, connection);
		}
	}
	
	@Override
	public boolean update(List<AccountingInfo> usage) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<AccountingInfo> getAccountingInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AccountingInfo getAccountingInfo(Object key) {
		// TODO Auto-generated method stub
		if (key instanceof UserAccountingDBKey) {
			
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
}

class UserAccountingDBKey {
	
	private String user;
	private String requestingMember;
	private String providingMember;
	
	public UserAccountingDBKey(String user, String requestingMember, String providingMember) {
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
		if (obj instanceof UserAccountingDBKey) {
			UserAccountingDBKey other = (UserAccountingDBKey) obj;
			return getUser().equals(other.getUser())
					&& getProvidingMember().equals(other.getProvidingMember())
					&& getRequestingMember().equals(other.getRequestingMember());
		}
		return false;
	}
}
