package org.fogbowcloud.manager.core.plugins.benchmarking.ssh;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;

public class SHHBenchmarkingDataStore {

	private static final Logger LOGGER = Logger.getLogger(SHHBenchmarkingDataStore.class);
	
	private static final String ERROR_WHILE_INITIALIZING = "Error while initializing the SHH Benchmanking DataStore.";
	protected static final String SHH_BENCHMARKING_DATASTORE_URL =  "ssh_benchmarking_datastore_url";
	protected static final String MANAGER_DATASTORE_SQLITE_DRIVER = "org.sqlite.JDBC";
	
	protected static final String SSHBENCHMARKING_TABLE_NAME = "t_sshbenchmarking";
	protected static final String INSTANCE_ID =  "instance_id";
	protected static final String POWER = "power";
	
	private String dataStoreURL;

	public SHHBenchmarkingDataStore(Properties properties) {
		this.dataStoreURL = properties.getProperty(SHH_BENCHMARKING_DATASTORE_URL);
		if (dataStoreURL == null || dataStoreURL.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ERROR_WHILE_INITIALIZING 
					+ " " + SHH_BENCHMARKING_DATASTORE_URL + " is required."); 
		}
		
		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("DatastoreURL: " + dataStoreURL);
			LOGGER.debug("DatastoreDriver: " + MANAGER_DATASTORE_SQLITE_DRIVER);

			Class.forName(MANAGER_DATASTORE_SQLITE_DRIVER);

			connection = getConnection();
			statement = connection.createStatement();
			statement.execute("CREATE TABLE IF NOT EXISTS " + SSHBENCHMARKING_TABLE_NAME + "(" 
							+ INSTANCE_ID + " VARCHAR(255) PRIMARY KEY, "
							+ POWER + " REAL )");			
			statement.close();
		} catch (Exception e) {
			LOGGER.error(ERROR_WHILE_INITIALIZING, e);
		} finally {
			close(statement, connection);
		}
	}
	
	private static final String INSERT_SQL = "INSERT INTO " + SSHBENCHMARKING_TABLE_NAME
			+ " (" + INSTANCE_ID + "," + POWER + ") VALUES (?,?)";
	
	public boolean addInstancePower(String instanceId, Double power) {
		PreparedStatement sshBenchmarkingStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			sshBenchmarkingStmt = connection.prepareStatement(INSERT_SQL);
			sshBenchmarkingStmt.setString(1, instanceId);
			sshBenchmarkingStmt.setDouble(2, power);
			sshBenchmarkingStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't create instance power.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(sshBenchmarkingStmt, connection);
		}
		return false;
	}
	
	private static final String GET_POWER_SQL = "SELECT " + INSTANCE_ID + ", " + POWER  
			+ " FROM " + SSHBENCHMARKING_TABLE_NAME 
			+ " WHERE " + INSTANCE_ID + " = ?";
	
	public Double getPower(String instanceId) {
		PreparedStatement instancesPowerStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			String storageLinksStmtStr = GET_POWER_SQL;
			
			instancesPowerStmt = connection.prepareStatement(storageLinksStmtStr);
			instancesPowerStmt.setString(1, instanceId);		
			ResultSet resultSet = instancesPowerStmt.executeQuery();
						
			return resultSet.getDouble(POWER);
		} catch (SQLException e) {
			LOGGER.error("Couldn't retrieve instance power.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(instancesPowerStmt, connection);
		}
		return null;
	}	
	
	private static final String INSTANCE_POWER_SQL = "DELETE"
			+ " FROM " + SSHBENCHMARKING_TABLE_NAME + " WHERE " + INSTANCE_ID + " = ?";
	
	public boolean removeInstancePower(String instanceId) {
		PreparedStatement removeStorageLinkStmt = null;
		Connection connection = null;
		try {	
			connection = getConnection();
			connection.setAutoCommit(false);
			
			removeStorageLinkStmt = connection.prepareStatement(INSTANCE_POWER_SQL);
			removeStorageLinkStmt.setString(1, instanceId);
			removeStorageLinkStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't remove instance power.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(removeStorageLinkStmt, connection);
		}
		return false;
	}		
	
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
