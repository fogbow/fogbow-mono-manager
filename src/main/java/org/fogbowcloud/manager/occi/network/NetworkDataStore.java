package org.fogbowcloud.manager.occi.network;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.DataStoreHelper;

public class NetworkDataStore {

	public static final String NETWORK_DATASTORE_DRIVER = "org.sqlite.JDBC";
	public static final String NETWORK_ORDER_TABLE_NAME = "network_order";
	public static final String INSTANCE_ID = "intance_id";
	public static final String ORDER_ID = "order_id";
	public static final String GLOBAL_INSTANCE_ID = "global_intance_id";
	public static final String USER_ID = "user_id";
	public static final String ADDRESS = "address";
	public static final String GATEWAY = "gateway";
	public static final String ALLOCATION = "allocation";

	private final String CREATE_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS " + NETWORK_ORDER_TABLE_NAME + "("
					+ INSTANCE_ID + " VARCHAR(255) PRIMARY KEY, " 
					+ ORDER_ID + " VARCHAR (255), " 
					+ GLOBAL_INSTANCE_ID + " VARCHAR (255), " 
					+ USER_ID + " VARCHAR (255),"
					+ ADDRESS + " VARCHAR (255),"
					+ GATEWAY + " VARCHAR (255),"
					+ ALLOCATION + " VARCHAR (255))";

	private static final String INSERT_NETWORK_TABLE_SQL = "INSERT INTO " + NETWORK_ORDER_TABLE_NAME
			+ " VALUES(?, ?, ?, ?, ?, ?, ?)";

	private static final String UPDATE_NETWORK_TABLE_SQL = "UPDATE " + NETWORK_ORDER_TABLE_NAME
			+ " SET " + ORDER_ID + " = ?, " + GLOBAL_INSTANCE_ID + " = ? WHERE " + INSTANCE_ID + " = ? AND " + USER_ID + " = ?";

	private static final String GET_ALL_INSTANCE = "SELECT " + INSTANCE_ID + ", " + ORDER_ID + ", " + GLOBAL_INSTANCE_ID
			+ ", " + USER_ID + ", "+ADDRESS+ ", "+ GATEWAY+", "+ALLOCATION+"  FROM " + NETWORK_ORDER_TABLE_NAME;

	private static final String GET_NETWORK_BY_USER = GET_ALL_INSTANCE + " WHERE " + USER_ID + " = ? ";
	private static final String GET_NETWORK_BY_INSTANCE_ID = GET_ALL_INSTANCE + " WHERE " + INSTANCE_ID + " = ? AND " + USER_ID + " = ?";
	private static final String GET_NETWORK_BY_ORDER_ID = GET_ALL_INSTANCE + " WHERE " + ORDER_ID + " = ? AND " + USER_ID + " = ?";

	private static final String DELETE_ALL_NETWORK_TABLE_SQL = "DELETE FROM " + NETWORK_ORDER_TABLE_NAME;
	private static final String DELETE_BY_USER = "DELETE FROM " + NETWORK_ORDER_TABLE_NAME + " WHERE " + USER_ID
			+ " = ? ";
	private static final String DELETE_BY_NETWORK_ID_SQL = "DELETE FROM " + NETWORK_ORDER_TABLE_NAME + " WHERE "
			+ INSTANCE_ID + " = ? AND " + USER_ID + " = ?";

	private static final Logger LOGGER = Logger.getLogger(NetworkDataStore.class);
	private static final String DEFAULT_DATASTORE_NAME = "datastore_network.slite";
	public static final String ERROR_WHILE_INITIALIZING_THE_DATA_STORE = "Error while initializing the Network DataStore.";

	private String networkDataStoreURL;

	public NetworkDataStore(String networkDataStoreURL) {
		this.networkDataStoreURL = DataStoreHelper.getDataStoreUrl(networkDataStoreURL,
				DEFAULT_DATASTORE_NAME);

		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("networkDataStoreURL: " + this.networkDataStoreURL);

			Class.forName(NETWORK_DATASTORE_DRIVER);

			connection = getConnection();
			statement = connection.createStatement();
			statement.execute(CREATE_TABLE_STATEMENT);
			statement.close();

		} catch (Exception e) {
			LOGGER.error(ERROR_WHILE_INITIALIZING_THE_DATA_STORE, e);
			throw new Error(ERROR_WHILE_INITIALIZING_THE_DATA_STORE, e);
		} finally {
			close(statement, connection);
		}
	}

	public boolean insert(FedNetworkState fedNetworkState) {

		LOGGER.debug("Inserting network [" + fedNetworkState.getFedInstanceId() + "] with relate order ["
				+ fedNetworkState.getOrderId() + "]" + " - User id [" + fedNetworkState.getUserId() + "]");

		if (fedNetworkState.getFedInstanceId() == null || fedNetworkState.getFedInstanceId().isEmpty()
				|| fedNetworkState.getOrderId() == null || fedNetworkState.getOrderId().isEmpty()
				|| fedNetworkState.getUserId() == null || fedNetworkState.getUserId().isEmpty()) {
			LOGGER.warn("Network fed Id, Order Id and User id must not be null.");
			return false;
		}

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			preparedStatement = connection.prepareStatement(INSERT_NETWORK_TABLE_SQL);
			preparedStatement.setString(1, fedNetworkState.getFedInstanceId());
			preparedStatement.setString(2, fedNetworkState.getOrderId());
			preparedStatement.setString(3, fedNetworkState.getGlobalInstanceId());
			preparedStatement.setString(4, fedNetworkState.getUserId());
			preparedStatement.setString(5, fedNetworkState.getAddress());
			preparedStatement.setString(6, fedNetworkState.getGateway());
			preparedStatement.setString(7, fedNetworkState.getAllocation());

			preparedStatement.execute();
			connection.commit();
			return true;

		} catch (SQLException e) {
			LOGGER.error("Couldn't execute statement : " + INSERT_NETWORK_TABLE_SQL, e);
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

	public boolean insert(List<FedNetworkState> fedNetworkStateList) {

		LOGGER.debug("Inserting networks id with related orders.");

		if (fedNetworkStateList == null || fedNetworkStateList.size() < 1) {
			LOGGER.warn("InstanceOrder Map must not be null.");
			return false;
		}

		return executeBatchStatement(fedNetworkStateList, INSERT_NETWORK_TABLE_SQL);
	}

	public boolean update(FedNetworkState fedInstanceState) {

		LOGGER.debug("Inserting network [" + fedInstanceState.getFedInstanceId() + "] with order ["
				+ fedInstanceState.getOrderId() + "]" + " Global Id [" + fedInstanceState.getGlobalInstanceId()
				+ "] - User id [" + fedInstanceState.getUserId() + "]");

		if (fedInstanceState.getFedInstanceId() == null || fedInstanceState.getFedInstanceId().isEmpty()
				|| fedInstanceState.getOrderId() == null || fedInstanceState.getOrderId().isEmpty()
				|| fedInstanceState.getUserId() == null || fedInstanceState.getUserId().isEmpty()) {
			LOGGER.warn("Intance Id, Order Id and User id must not be null.");
			return false;
		}

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {

			connection = getConnection();
			connection.setAutoCommit(false);
			preparedStatement = connection.prepareStatement(UPDATE_NETWORK_TABLE_SQL);
			preparedStatement.setString(1, fedInstanceState.getOrderId());
			preparedStatement.setString(2, fedInstanceState.getGlobalInstanceId());
			preparedStatement.setString(3, fedInstanceState.getFedInstanceId());
			preparedStatement.setString(4, fedInstanceState.getUserId());
			
			preparedStatement.execute();
			connection.commit();
			return true;

		} catch (SQLException e) {
			LOGGER.error("Couldn't execute statement : " + UPDATE_NETWORK_TABLE_SQL, e);
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

	public List<FedNetworkState> getAll() {

		LOGGER.debug("Getting all networks id with related orders.");

		String queryStatement = GET_ALL_INSTANCE;

		return executeQueryStatement(queryStatement);
	}

	public List<FedNetworkState> getAllByUser(String userId) {

		LOGGER.debug("Getting all networks id with related orders to user id [" + userId + "]");

		String queryStatement = GET_NETWORK_BY_USER;

		return executeQueryStatement(queryStatement, userId);
	}

	public FedNetworkState getByFedNetworkId(String fedNetworkId, String userId) {

		LOGGER.debug("Getting instances id with related orders by Instance ID [" + fedNetworkId + "]");

		String queryStatement = GET_NETWORK_BY_INSTANCE_ID;
		List<FedNetworkState> fedInstanceStateList = executeQueryStatement(queryStatement, fedNetworkId, userId);
		if (fedInstanceStateList != null && !fedInstanceStateList.isEmpty()) {
			return fedInstanceStateList.get(0);
		}
		return null;

	}

	public FedNetworkState getByOrderId(String orderId, String userId) {

		LOGGER.debug("Getting instances id with related orders by Order ID [" + orderId + "]");

		String queryStatement = GET_NETWORK_BY_ORDER_ID;

		List<FedNetworkState> fedNetworkStateList = executeQueryStatement(queryStatement, orderId, userId);

		if (fedNetworkStateList != null && !fedNetworkStateList.isEmpty()) {
			return fedNetworkStateList.get(0);
		}
		return null;
	}

	public boolean deleteAll() {

		LOGGER.debug("Deleting all instances id with related orders.");

		Statement statement = null;
		Connection conn = null;
		try {

			conn = getConnection();
			statement = conn.createStatement();

			boolean result = statement.execute(DELETE_ALL_NETWORK_TABLE_SQL);
			conn.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't delete all registres on " + INSERT_NETWORK_TABLE_SQL, e);
			return false;
		} finally {
			close(statement, conn);
		}
	}

	public boolean deleteAllFromUser(String userId) {

		LOGGER.debug("Deleting all instances id with related orders.");

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
			LOGGER.error("Couldn't delete all registres on " + INSERT_NETWORK_TABLE_SQL, e);
			return false;
		} finally {
			close(statement, conn);
		}
	}

	public boolean deleteByIntanceId(String instanceId, String userId) {

		LOGGER.debug("Deleting all instances id with related orders with id");

		PreparedStatement statement = null;
		Connection conn = null;
		try {

			conn = getConnection();
			statement = conn.prepareStatement(DELETE_BY_NETWORK_ID_SQL);
			statement.setString(1, instanceId);
			statement.setString(2, userId);
			boolean result = statement.execute();
			conn.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't delete registres on " + INSERT_NETWORK_TABLE_SQL + " with Instance id ["
					+ instanceId + "]", e);
			return false;
		} finally {
			close(statement, conn);
		}
	}

	private boolean executeBatchStatement(List<FedNetworkState> fedNetworkStateList, String sqlStatement) {

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {

			connection = getConnection();
			connection.setAutoCommit(false);
			preparedStatement = connection.prepareStatement(sqlStatement);

			for (FedNetworkState fedNetworkState : fedNetworkStateList) {

				preparedStatement.setString(1, fedNetworkState.getFedInstanceId());
				preparedStatement.setString(2, fedNetworkState.getOrderId());
				preparedStatement.setString(3, fedNetworkState.getGlobalInstanceId());
				preparedStatement.setString(4, fedNetworkState.getUserId());
				preparedStatement.setString(5, fedNetworkState.getAddress());
				preparedStatement.setString(6, fedNetworkState.getGateway());
				preparedStatement.setString(7, fedNetworkState.getAllocation());
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

	private List<FedNetworkState> executeQueryStatement(String queryStatement, String... params) {

		PreparedStatement preparedStatement = null;
		Connection conn = null;
		List<FedNetworkState> fedNetworkStateList = new ArrayList<FedNetworkState>();

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
						FedNetworkState fedNetworkState = new FedNetworkState(
								rs.getString(INSTANCE_ID),
								rs.getString(ORDER_ID),
								rs.getString(GLOBAL_INSTANCE_ID),
								rs.getString(USER_ID),
								rs.getString(ADDRESS),
								rs.getString(ALLOCATION),
								rs.getString(GATEWAY));
						fedNetworkStateList.add(fedNetworkState);
					}
				} catch (Exception e) {
					LOGGER.error("Error while mounting instande from DB.", e);
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Couldn't get Intances and Orders ID.", e);
			return new ArrayList<FedNetworkState>();
		} finally {
			close(preparedStatement, conn);
		}
		LOGGER.debug("There are " + fedNetworkStateList.size() + " federated_network at DB to this query (" + preparedStatement.toString() + ").");
		return fedNetworkStateList;
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
			return DriverManager.getConnection(networkDataStoreURL);
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
