package org.fogbowcloud.manager.occi.instance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.h2.jdbcx.JdbcConnectionPool;

public class InstanceDataStore {

	public static final String INSTANCE_ORDER_TABLE_NAME = "instance_order";
	public static final String INSTANCE_ID = "intance_id";
	public static final String ORDER_ID = "order_id";
	public static final String GLOBAL_INSTANCE_ID = "global_intance_id";
	public static final String USER = "user";

	private final String CREATE_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS " + INSTANCE_ORDER_TABLE_NAME + "("
			+ INSTANCE_ID + " VARCHAR(255) PRIMARY KEY, " + ORDER_ID + " VARCHAR (255), " + GLOBAL_INSTANCE_ID
			+ " VARCHAR (255), " + USER + " VARCHAR (255) )";

	private static final String INSERT_INSTANCE_TABLE_SQL = "INSERT INTO " + INSTANCE_ORDER_TABLE_NAME
			+ " VALUES(?, ?, ?, ?)";

	private static final String UPDATE_INSTANCE_TABLE_SQL = "UPDATE " + INSTANCE_ORDER_TABLE_NAME + " SET " + ORDER_ID
			+ " = ?, " + GLOBAL_INSTANCE_ID + " = ? WHERE " + INSTANCE_ID + " = ? AND " + USER + " = ?";

	private static final String GET_ALL_INSTANCE = "SELECT " + INSTANCE_ID + ", " + ORDER_ID + ", " + GLOBAL_INSTANCE_ID
			+ ", " + USER + "  FROM " + INSTANCE_ORDER_TABLE_NAME;

	private static final String GET_INSTANCE_BY_USER = GET_ALL_INSTANCE + " WHERE " + USER + " = ? ";
	private static final String GET_INSTANCE_BY_INSTANCE_ID = GET_ALL_INSTANCE + " WHERE " + INSTANCE_ID + " = ?";
	private static final String GET_INSTANCE_BY_ORDER_ID = GET_ALL_INSTANCE + " WHERE " + ORDER_ID + " = ?";

	private static final String DELETE_ALL_INSTANCE_TABLE_SQL = "DELETE FROM " + INSTANCE_ORDER_TABLE_NAME;
	private static final String DELETE_BY_USER = "DELETE FROM " + INSTANCE_ORDER_TABLE_NAME + " WHERE " + USER
			+ " = ? ";
	private static final String DELETE_BY_INSTANCE_ID_SQL = "DELETE FROM " + INSTANCE_ORDER_TABLE_NAME + " WHERE "
			+ INSTANCE_ID + " = ?";

	private static final Logger LOGGER = Logger.getLogger(InstanceDataStore.class);

	private String instanceDataStoreURL;
	private JdbcConnectionPool cp;

	public InstanceDataStore(String instanceDataStoreURL) {

		this.instanceDataStoreURL = instanceDataStoreURL;

		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("instanceDataStoreURL: " + this.instanceDataStoreURL);

			Class.forName("org.h2.Driver");
			this.cp = JdbcConnectionPool.create(this.instanceDataStoreURL, "sa", "");

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

	public boolean insert(FedInstanceState fedInstanceState) {

		LOGGER.debug("Inserting instance [" + fedInstanceState.getFedInstanceId() + "] with relate order ["
				+ fedInstanceState.getOrderId() + "]" + " - User [" + fedInstanceState.getUser() + "]");

		if (fedInstanceState.getFedInstanceId() == null || fedInstanceState.getFedInstanceId().isEmpty()
				|| fedInstanceState.getOrderId() == null || fedInstanceState.getOrderId().isEmpty()
				|| fedInstanceState.getUser() == null || fedInstanceState.getUser().isEmpty()) {
			LOGGER.warn("Intance Id, Order Id and User must not be null.");
			return false;
		}

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {

			connection = getConnection();
			preparedStatement = connection.prepareStatement(INSERT_INSTANCE_TABLE_SQL);
			preparedStatement.setString(1, fedInstanceState.getFedInstanceId());
			preparedStatement.setString(2, fedInstanceState.getOrderId());
			preparedStatement.setString(3, fedInstanceState.getGlobalInstanceId());
			preparedStatement.setString(4, fedInstanceState.getUser());

			preparedStatement.execute();
			connection.commit();
			return true;

		} catch (SQLException e) {
			LOGGER.error("Couldn't execute statement : " + INSERT_INSTANCE_TABLE_SQL, e);
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

	public boolean insert(List<FedInstanceState> fedInstanceStateList) {

		LOGGER.debug("Inserting instances id with related orders.");

		if (fedInstanceStateList == null || fedInstanceStateList.size() < 1) {
			LOGGER.warn("InstanceOrder Map must not be null.");
			return false;
		}

		return executeBatchStatement(fedInstanceStateList, INSERT_INSTANCE_TABLE_SQL);
	}

	public boolean update(FedInstanceState fedInstanceState) {

		LOGGER.debug("Inserting instance [" + fedInstanceState.getFedInstanceId() + "] with order ["
				+ fedInstanceState.getOrderId() + "]" + " Global Id [" + fedInstanceState.getGlobalInstanceId()
				+ "] - User [" + fedInstanceState.getUser() + "]");

		if (fedInstanceState.getFedInstanceId() == null || fedInstanceState.getFedInstanceId().isEmpty()
				|| fedInstanceState.getOrderId() == null || fedInstanceState.getOrderId().isEmpty()
				|| fedInstanceState.getUser() == null || fedInstanceState.getUser().isEmpty()) {
			LOGGER.warn("Intance Id, Order Id and User must not be null.");
			return false;
		}

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {

			connection = getConnection();
			connection.setAutoCommit(false);
			preparedStatement = connection.prepareStatement(UPDATE_INSTANCE_TABLE_SQL);
			preparedStatement.setString(1, fedInstanceState.getOrderId());
			preparedStatement.setString(2, fedInstanceState.getGlobalInstanceId());
			preparedStatement.setString(3, fedInstanceState.getFedInstanceId());
			preparedStatement.setString(4, fedInstanceState.getUser());

			preparedStatement.execute();
			connection.commit();
			return true;

		} catch (SQLException e) {
			LOGGER.error("Couldn't execute statement : " + UPDATE_INSTANCE_TABLE_SQL, e);
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

	public List<FedInstanceState> getAll() {

		LOGGER.debug("Getting all instances id with related orders.");

		String queryStatement = GET_ALL_INSTANCE;

		return executeQueryStatement(queryStatement);
	}

	public List<FedInstanceState> getAllByUser(String user) {

		LOGGER.debug("Getting all instances id with related orders to user [" + user + "]");

		String queryStatement = GET_INSTANCE_BY_USER;

		return executeQueryStatement(queryStatement, user);
	}

	public FedInstanceState getByInstanceId(String instanceId) {

		LOGGER.debug("Getting instances id with related orders by Instance ID [" + instanceId + "]");

		String queryStatement = GET_INSTANCE_BY_INSTANCE_ID;
		List<FedInstanceState> fedInstanceStateList = executeQueryStatement(queryStatement, instanceId);
		if (fedInstanceStateList != null && !fedInstanceStateList.isEmpty()) {
			return fedInstanceStateList.get(0);
		}
		return null;

	}

	public FedInstanceState getByOrderId(String orderId) {

		LOGGER.debug("Getting instances id with related orders by Order ID [" + orderId + "]");

		String queryStatement = GET_INSTANCE_BY_ORDER_ID;

		List<FedInstanceState> fedInstanceStateList = executeQueryStatement(queryStatement, orderId);

		if (fedInstanceStateList != null && !fedInstanceStateList.isEmpty()) {
			return fedInstanceStateList.get(0);
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

			boolean result = statement.execute(DELETE_ALL_INSTANCE_TABLE_SQL);
			conn.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't delete all registres on " + INSERT_INSTANCE_TABLE_SQL, e);
			return false;
		} finally {
			close(statement, conn);
		}
	}

	public boolean deleteAllFromUser(String user) {

		LOGGER.debug("Deleting all instances id with related orders.");

		PreparedStatement statement = null;
		Connection conn = null;
		try {

			conn = getConnection();
			statement = conn.prepareStatement(DELETE_BY_USER);
			statement.setString(1, user);
			boolean result = statement.execute();
			conn.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't delete all registres on " + INSERT_INSTANCE_TABLE_SQL, e);
			return false;
		} finally {
			close(statement, conn);
		}
	}

	public boolean deleteByIntanceId(String instanceId) {

		LOGGER.debug("Deleting all instances id with related orders with id");

		PreparedStatement statement = null;
		Connection conn = null;
		try {

			conn = getConnection();
			statement = conn.prepareStatement(DELETE_BY_INSTANCE_ID_SQL);
			statement.setString(1, instanceId);
			boolean result = statement.execute();
			conn.commit();
			return result;

		} catch (SQLException e) {
			LOGGER.error("Couldn't delete registres on " + INSERT_INSTANCE_TABLE_SQL + " with Instance id ["
					+ instanceId + "]", e);
			return false;
		} finally {
			close(statement, conn);
		}
	}

	private boolean executeBatchStatement(List<FedInstanceState> fedInstanceStateList, String sqlStatement) {

		PreparedStatement preparedStatement = null;
		Connection connection = null;
		try {

			connection = getConnection();
			connection.setAutoCommit(false);
			preparedStatement = connection.prepareStatement(sqlStatement);

			for (FedInstanceState fedInstanceState : fedInstanceStateList) {

				preparedStatement.setString(1, fedInstanceState.getFedInstanceId());
				preparedStatement.setString(2, fedInstanceState.getOrderId());
				preparedStatement.setString(3, fedInstanceState.getGlobalInstanceId());
				preparedStatement.setString(4, fedInstanceState.getUser());
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

	private List<FedInstanceState> executeQueryStatement(String queryStatement, String... params) {

		PreparedStatement preparedStatement = null;
		Connection conn = null;
		List<FedInstanceState> fedInstanceStateList = new ArrayList<FedInstanceState>();

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
						FedInstanceState fedInstanceState = new FedInstanceState(rs.getString(INSTANCE_ID),
								rs.getString(ORDER_ID), rs.getString(GLOBAL_INSTANCE_ID), rs.getString(USER));
						fedInstanceStateList.add(fedInstanceState);
					}
				} catch (SQLException e) {
					LOGGER.error("Couldn't get InstancesOrders Map.", e);
					new HashMap<String, String>();
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Couldn't get Intances and Orders ID.", e);
			return new ArrayList<FedInstanceState>();
		} finally {
			close(preparedStatement, conn);
		}

		return fedInstanceStateList;
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
			return cp.getConnection();
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

	public void dispose() {
		cp.dispose();
	}

}
