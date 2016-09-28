package org.fogbowcloud.manager.occi.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class StorageDataStore {

	private static final String STORAGE_DATASTORE_DRIVER = "org.sqlite.JDBC";
	private static final String STORAGE_ORDER_TABLE_NAME = "storage_order";
	private static final String STORAGE_ID = "storage_id";
	private static final String ORDER_ID = "order_id";
	private static final String GLOBAL_STORAGE_ID = "global_storage_id";
	private static final String USER_ID = "user_id";


	private final String CREATE_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS " + STORAGE_ORDER_TABLE_NAME + "("
					+ STORAGE_ID + " VARCHAR(255) PRIMARY KEY, " 
					+ ORDER_ID + " VARCHAR (255), " 
					+ GLOBAL_STORAGE_ID + " VARCHAR (255), "
					+ USER_ID + " VARCHAR (255) )";

	private static final String INSERT_STORAGE_TABLE_SQL = "INSERT INTO " + STORAGE_ORDER_TABLE_NAME
			+ " VALUES(?, ?, ?, ?)";

	private static final String UPDATE_STORAGE_TABLE_SQL = "UPDATE " + STORAGE_ORDER_TABLE_NAME
			+ " SET " + ORDER_ID + " = ?, " + GLOBAL_STORAGE_ID + " = ?  WHERE " + STORAGE_ID + " = ? AND " + USER_ID + " = ?";

	private static final String GET_ALL_STORAGE = "SELECT " + STORAGE_ID + ", " + ORDER_ID + ", " + GLOBAL_STORAGE_ID
			+ ", " + USER_ID + " FROM " + STORAGE_ORDER_TABLE_NAME;

	private static final String GET_STORAGE_BY_USER = GET_ALL_STORAGE + " WHERE " + USER_ID + " = ? ";
	private static final String GET_STORAGE_BY_STORAGE_ID = GET_ALL_STORAGE + " WHERE " + STORAGE_ID + " = ? AND " + USER_ID + " = ?";
	private static final String GET_STORAGE_BY_ORDER_ID = GET_ALL_STORAGE + " WHERE " + ORDER_ID + " = ? AND " + USER_ID + " = ?";
	private static final String GET_STORAGE_BY_GLOBAL_ID = GET_ALL_STORAGE + " WHERE " + GLOBAL_STORAGE_ID + " like ? AND " + USER_ID + " = ?";

	private static final String DELETE_ALL_STORAGE_TABLE_SQL = "DELETE FROM " + STORAGE_ORDER_TABLE_NAME;
	private static final String DELETE_BY_USER = "DELETE FROM " + STORAGE_ORDER_TABLE_NAME + " WHERE " + USER_ID
			+ " = ? ";
	private static final String DELETE_BY_STORAGE_ID_SQL = "DELETE FROM " + STORAGE_ORDER_TABLE_NAME + " WHERE "
			+ STORAGE_ID + " = ? AND " + USER_ID + " = ?";

	private static final Logger LOGGER = Logger.getLogger(StorageDataStore.class);

	private String storageDataStoreURL;

	public StorageDataStore(String storageDataStoreURL) {

		this.storageDataStoreURL = storageDataStoreURL;

		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("StorageDataStoreURL: " + this.storageDataStoreURL);

			Class.forName(STORAGE_DATASTORE_DRIVER);

			connection = getConnection();
			statement = connection.createStatement();
			statement.execute(CREATE_TABLE_STATEMENT);
			statement.close();

		} catch (Exception e) {
			LOGGER.error("Error while initializing the DataStore.", e);
		} finally {
			close(statement, connection);
		}
	}

	public boolean insert(FedStorageState fedStorageState) {

		LOGGER.debug("Inserting STORAGE [" + fedStorageState.getFedStorageId() + "] with relate order ["
				+ fedStorageState.getOrderId() + "]" + " - User id [" + fedStorageState.getUserId() + "]");

		if(!validateFedStorageState(fedStorageState)){
			return false;
		}

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			preparedStatement = connection.prepareStatement(INSERT_STORAGE_TABLE_SQL);
			preparedStatement.setString(1, fedStorageState.getFedStorageId());
			preparedStatement.setString(2, fedStorageState.getOrderId());
			preparedStatement.setString(3, fedStorageState.getGlobalStorageId());
			preparedStatement.setString(4, fedStorageState.getUserId());

			preparedStatement.execute();
			connection.commit();
			return true;

		} catch (SQLException e) {
			LOGGER.error("Couldn't execute statement : " + INSERT_STORAGE_TABLE_SQL, e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
			return false;
		} finally {
			close(preparedStatement, connection);
		}
	}

	public boolean insert(List<FedStorageState> fedStorageStateList) {

		LOGGER.debug("Inserting STORAGEs id with related orders.");

		if (fedStorageStateList == null || fedStorageStateList.isEmpty()) {
			LOGGER.warn("fedStorageStateList must not be null or empty.");
			return false;
		}

		return executeBatchStatement(fedStorageStateList, INSERT_STORAGE_TABLE_SQL);
	}

	public boolean update(FedStorageState fedStorageState) {

		LOGGER.debug("Inserting STORAGE [" + fedStorageState.getFedStorageId() + "] with order ["
				+ fedStorageState.getOrderId() + "]" + " Global Id [" + fedStorageState.getGlobalStorageId()
				+ "] - User id [" + fedStorageState.getUserId() + "]");

		if(!validateFedStorageState(fedStorageState)){
			return false;
		}

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {

			connection = getConnection();
			connection.setAutoCommit(false);
			preparedStatement = connection.prepareStatement(UPDATE_STORAGE_TABLE_SQL);
			preparedStatement.setString(1, fedStorageState.getOrderId());
			preparedStatement.setString(2, fedStorageState.getGlobalStorageId());
			preparedStatement.setString(3, fedStorageState.getFedStorageId());
			preparedStatement.setString(4, fedStorageState.getUserId());
			
			preparedStatement.execute();
			connection.commit();
			return true;

		} catch (SQLException e) {
			LOGGER.error("Couldn't execute statement : " + UPDATE_STORAGE_TABLE_SQL, e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(preparedStatement, connection);
		}
		return false;
	}

	private boolean validateFedStorageState(FedStorageState fedStorageState) {
		if (fedStorageState.getFedStorageId() == null || fedStorageState.getFedStorageId().isEmpty()) {
			LOGGER.warn("FedStorageId must not be null.");
			return false;
		}
		if (fedStorageState.getOrderId() == null || fedStorageState.getOrderId().isEmpty()) {
			LOGGER.warn("Order Id must not be null.");
			return false;
		}
		if (fedStorageState.getUserId() == null || fedStorageState.getUserId().isEmpty()) {
			LOGGER.warn("User id must not be null.");
			return false;
		}
		return true;
	}

	public List<FedStorageState> getAll() {

		LOGGER.debug("Getting all Storages id with related orders.");

		String queryStatement = GET_ALL_STORAGE;

		return executeQueryStatement(queryStatement);
	}

	public List<FedStorageState> getAllByUser(String userId) {

		LOGGER.debug("Getting all Storages id with related orders to user id [" + userId + "]");

		String queryStatement = GET_STORAGE_BY_USER;

		return executeQueryStatement(queryStatement, userId);
	}

	public FedStorageState getByStorageId(String StorageId, String userId) {

		LOGGER.debug("Getting Storages id with related orders by STORAGE ID [" + StorageId + "]");

		String queryStatement = GET_STORAGE_BY_STORAGE_ID;
		List<FedStorageState> fedStorageStateList = executeQueryStatement(queryStatement, StorageId, userId);
		if (fedStorageStateList != null && !fedStorageStateList.isEmpty()) {
			return fedStorageStateList.get(0);
		}
		return null;

	}
	
	public FedStorageState getByOrderId(String orderId, String user) {

		LOGGER.debug("Getting Storages id with related orders by ORDER ID [" + orderId + "]");

		String queryStatement = GET_STORAGE_BY_ORDER_ID;
		List<FedStorageState> fedStorageStateList = executeQueryStatement(queryStatement, orderId, user);
		if (fedStorageStateList != null && !fedStorageStateList.isEmpty()) {
			return fedStorageStateList.get(0);
		}
		return null;

	}
	
	public FedStorageState getByGlobalId(String globalId, String user) {

		LOGGER.debug("Getting Storages id with related orders by Global ID [" + globalId + "]");

		String queryStatement = GET_STORAGE_BY_GLOBAL_ID;

		List<FedStorageState> fedStorageStateList = executeQueryStatement(queryStatement, "%"+globalId+"%", user);

		if (fedStorageStateList != null && !fedStorageStateList.isEmpty()) {
			return fedStorageStateList.get(0);
		}
		return null;
	}

	public boolean deleteAllFromUser(String userId) {

		LOGGER.debug("Deleting all Storages id with related orders.");

		PreparedStatement statement = null;
		Connection conn = null;
		try {

			conn = getConnection();
			statement = conn.prepareStatement(DELETE_BY_USER);
			statement.setString(1, userId);
			boolean result = statement.execute();
			conn.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't delete all registres on " + INSERT_STORAGE_TABLE_SQL, e);
			return false;
		} finally {
			close(statement, conn);
		}
	}

	public boolean deleteByStorageId(String storageId, String userId) {

		LOGGER.debug("Deleting all Storages id with related orders with id");

		PreparedStatement statement = null;
		Connection conn = null;
		try {

			conn = getConnection();
			statement = conn.prepareStatement(DELETE_BY_STORAGE_ID_SQL);
			statement.setString(1, storageId);
			statement.setString(2, userId);
			boolean result = statement.execute();
			conn.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't delete registres on " + INSERT_STORAGE_TABLE_SQL + " with Storages id ["
					+ storageId + "]", e);
			return false;
		} finally {
			close(statement, conn);
		}
	}

	private boolean executeBatchStatement(List<FedStorageState> fedStorageStateList, String sqlStatement) {

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {

			connection = getConnection();
			connection.setAutoCommit(false);
			preparedStatement = connection.prepareStatement(sqlStatement);

			for (FedStorageState FedStorageState : fedStorageStateList) {

				preparedStatement.setString(1, FedStorageState.getFedStorageId());
				preparedStatement.setString(2, FedStorageState.getOrderId());
				preparedStatement.setString(3, FedStorageState.getGlobalStorageId());
				preparedStatement.setString(4, FedStorageState.getUserId());
				preparedStatement.addBatch();

			}

			if (hasBatchExecutionError(preparedStatement.executeBatch())) {
				connection.rollback();
				return false;
			}

			connection.commit();
			return true;

		} catch (SQLException e) {
			LOGGER.error("Couldn't execute statement : " + sqlStatement, e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
			return false;
		} finally {
			close(preparedStatement, connection);
		}
	}

	private List<FedStorageState> executeQueryStatement(String queryStatement, String... params) {

		PreparedStatement preparedStatement = null;
		Connection conn = null;
		List<FedStorageState> fedStorageStateList = new ArrayList<FedStorageState>();

		try {

			conn = getConnection();
			preparedStatement = conn.prepareStatement(queryStatement);

			if (params != null && params.length > 0) {
				for (int index = 0; index < params.length; index++) {
					preparedStatement.setString(index + 1, params[index]);
				}
			}

			ResultSet rs = preparedStatement.executeQuery();
			
			if (rs != null) {
				try {
					while (rs.next()) {
						FedStorageState FedStorageState = new FedStorageState(
								rs.getString(STORAGE_ID),
								rs.getString(ORDER_ID),
								rs.getString(GLOBAL_STORAGE_ID),
								rs.getString(USER_ID));
						fedStorageStateList.add(FedStorageState);
					}
				} catch (Exception e) {
					LOGGER.error("Error while mounting instande from DB.", e);
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Couldn't get Instances and Orders ID.", e);
			return new ArrayList<FedStorageState>();
		} finally {
			close(preparedStatement, conn);
		}
		LOGGER.debug("There are " + fedStorageStateList.size() + " federated_STORAGEs at DB to this query (" + preparedStatement.toString() + ").");
		return fedStorageStateList;
	}

	private boolean hasBatchExecutionError(int[] executeBatch) {
		for (int i : executeBatch) {
			if (i == PreparedStatement.EXECUTE_FAILED) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the connection
	 * @throws SQLException
	 */
	public Connection getConnection() throws SQLException {
		try {
			return DriverManager.getConnection(storageDataStoreURL);
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
