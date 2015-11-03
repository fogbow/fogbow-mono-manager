package org.fogbowcloud.manager.occi.request;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.JSONHelper;
import org.fogbowcloud.manager.occi.model.Token;
import org.h2.jdbcx.JdbcConnectionPool;
import org.json.JSONException;

public class OrderDataStore {

	private static final Logger LOGGER = Logger.getLogger(OrderDataStore.class);
	protected static final String ORDER_DATASTORE_URL = "order_datastore_url";
	protected static final String ORDER_TABLE_NAME = "t_order";
	protected static final String ORDER_ID = "order_id";
	protected static final String PROVIDING_MEMBER_ID = "providing_member_id";
	protected static final String INSTANCE_ID = "instance_id";
	protected static final String REQUESTING_MEMBER_ID = "requesting_member_id";
	protected static final String FEDERATION_TOKEN = "federation_token";
	protected static final String FULFILLED_TIME = "fulfilled_time";
	protected static final String IS_LOCAL = "is_local";
	protected static final String STATE = "state";
	protected static final String CATEGORIES = "categories";
	protected static final String XOCCI_ATTRIBUTES = "xocci_attributes";
	protected static final String UPDATED = "updated";
	
	private String dataStoreURL;
	private JdbcConnectionPool cp;

	public OrderDataStore(Properties properties) {
		this.dataStoreURL = properties.getProperty(ORDER_DATASTORE_URL);
		
		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("DatastoreURL: " + dataStoreURL);

			Class.forName("org.h2.Driver");
			this.cp = JdbcConnectionPool.create(dataStoreURL, "sa", "");

			connection = getConnection();
			statement = connection.createStatement();
			statement.execute("CREATE TABLE IF NOT EXISTS " + ORDER_TABLE_NAME + "(" 
							+ ORDER_ID + " VARCHAR(255) PRIMARY KEY, "
							+ INSTANCE_ID + " VARCHAR(255), "
							+ PROVIDING_MEMBER_ID + " VARCHAR(255), "
							+ REQUESTING_MEMBER_ID + " VARCHAR(255), "
							+ FEDERATION_TOKEN + " TEXT, "
							+ FULFILLED_TIME + " BIGINT, "
							+ IS_LOCAL + " BOOLEAN, "
							+ STATE + " VARCHAR(255), "
							+ CATEGORIES + " TEXT, "
							+ UPDATED + " TIMESTAMP, "
							+ XOCCI_ATTRIBUTES + " TEXT)");
			statement.close();
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.error("Error while initializing the DataStore.", e);
		} finally {
			close(statement, connection);
		}
	}
	
	private static final String INSERT_ORDER_SQL = "INSERT INTO " + ORDER_TABLE_NAME
			+ " (" + ORDER_ID + "," + INSTANCE_ID + "," + PROVIDING_MEMBER_ID + "," + REQUESTING_MEMBER_ID + "," 
			+ FEDERATION_TOKEN + "," + FULFILLED_TIME + "," + IS_LOCAL + "," + STATE + "," + CATEGORIES + ","
			+ UPDATED + "," + XOCCI_ATTRIBUTES + ")"			
			+ " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
	
	public boolean addOrder(Request order) throws SQLException, JSONException {
		PreparedStatement orderStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			orderStmt = connection.prepareStatement(INSERT_ORDER_SQL);
			orderStmt.setString(1, order.getId());
			orderStmt.setString(2, order.getInstanceId());
			orderStmt.setString(3, order.getProvidingMemberId());
			orderStmt.setString(4, order.getRequestingMemberId());
			orderStmt.setString(5, order.getFederationToken().toJSON().toString());
			orderStmt.setLong(6, order.getFulfilledTime());
			orderStmt.setBoolean(7, order.isLocal());
			orderStmt.setString(8, order.getState() != null ? 
					order.getState().toString() : null);
			orderStmt.setString(9, JSONHelper.mountCategoriesJSON(order.getCategories()).toString());
			orderStmt.setTimestamp(10, new Timestamp(new Date().getTime()));
			orderStmt.setString(11, JSONHelper.mountXOCCIAttrJSON(order.getxOCCIAtt()).toString());
			orderStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			LOGGER.error("Couldn't create order.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(orderStmt, connection);
		}
		return false;
	}
	
	private static final String GET_ORDERS_SQL = "SELECT " + ORDER_ID + ", " + INSTANCE_ID + ", "
			+ PROVIDING_MEMBER_ID + ", " + REQUESTING_MEMBER_ID + ", " + FEDERATION_TOKEN + ", " + FULFILLED_TIME + ", " 
			+ IS_LOCAL + ", " + STATE + ", " + CATEGORIES + ", " + XOCCI_ATTRIBUTES
			+ " FROM " + ORDER_TABLE_NAME;
	
	public List<Request> getOrders() throws SQLException, JSONException {
		PreparedStatement ordersStmt = null;
		Connection connection = null;
		List<Request> orders = new ArrayList<Request>();
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			String ordersStmtStr = GET_ORDERS_SQL;
			
			ordersStmt = connection.prepareStatement(ordersStmtStr);
			ordersStmt.executeQuery();
			ResultSet resultSet = ordersStmt.getResultSet();
			while (resultSet.next()) {
				resultSet.getString(1);
				
				orders.add(new Request(resultSet.getString(1), Token.fromJSON(resultSet
						.getString(5)), resultSet.getString(2), resultSet.getString(3), resultSet
						.getString(4), resultSet.getLong(6), resultSet.getBoolean(7), RequestState
						.getState(resultSet.getString(8)), JSONHelper.getCategoriesFromJSON(resultSet
						.getString(9)), JSONHelper.getXOCCIAttrFromJSON(resultSet.getString(10))));
			}
					
			connection.commit();
			
			return orders;
		} catch (SQLException e) {
			e.printStackTrace();
			LOGGER.error("Couldn't retrive orders.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(ordersStmt, connection);
		}
		return orders;
	}	

	private static final String REMOVE_ORDER_SQL = "DELETE"
			+ " FROM " + ORDER_TABLE_NAME 
			+ " WHERE " + ORDER_ID + " = ?";
	
	public boolean removeOrder(Request order) throws SQLException {
		PreparedStatement removeOrderStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			removeOrderStmt = connection.prepareStatement(REMOVE_ORDER_SQL);
			removeOrderStmt.setString(1, order.getId());
			removeOrderStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			LOGGER.error("Couldn't remove order.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(removeOrderStmt, connection);
		}
		return false;
	}	
	
	private static final String REMOVE_ALL_ORDER_SQL = "DELETE"
			+ " FROM " + ORDER_TABLE_NAME;
	
	public boolean removeAllOrder() throws SQLException {
		PreparedStatement removeOrderStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			removeOrderStmt = connection.prepareStatement(REMOVE_ALL_ORDER_SQL);
			removeOrderStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			LOGGER.error("Couldn't remove all order.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(removeOrderStmt, connection);
		}
		return false;
	}		
	
	private static final String UPDATE_ORDER_SQL = "UPDATE " + ORDER_TABLE_NAME + " SET "
			+ INSTANCE_ID + "=?," + PROVIDING_MEMBER_ID + "=? ,"
			+ REQUESTING_MEMBER_ID + "=?," + FEDERATION_TOKEN + "=? ," + FULFILLED_TIME
			+ "=? ," + IS_LOCAL + "=? ," + STATE + "=? ," + CATEGORIES + "=?," + UPDATED
			+ "=?," + XOCCI_ATTRIBUTES + "=?" + " WHERE " + ORDER_ID + "=?";
	
	public boolean updateOrder(Request order) throws SQLException, JSONException {
		PreparedStatement updateOrderStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			updateOrderStmt = connection.prepareStatement(UPDATE_ORDER_SQL);
			updateOrderStmt.setString(1, order.getInstanceId());
			updateOrderStmt.setString(2, order.getProvidingMemberId());
			updateOrderStmt.setString(3, order.getRequestingMemberId());
			updateOrderStmt.setString(4, order.getFederationToken().toJSON().toString());
			updateOrderStmt.setLong(5, order.getFulfilledTime());
			updateOrderStmt.setBoolean(6, order.isLocal());
			updateOrderStmt.setString(7, order.getState() != null ? 
					order.getState().toString() : null);
			updateOrderStmt.setString(8, JSONHelper.mountCategoriesJSON(order.getCategories()).toString());
			updateOrderStmt.setTimestamp(9, new Timestamp(new Date().getTime()));			
			updateOrderStmt.setString(10, JSONHelper.mountXOCCIAttrJSON(order.getxOCCIAtt()).toString());
			updateOrderStmt.setString(11, order.getId());
			updateOrderStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			LOGGER.error("Couldn't update order.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(updateOrderStmt, connection);
		}
		return false;
	}	
	
	private static final String COUNT_ORDER_SQL = "SELECT COUNT(*) FROM " + ORDER_TABLE_NAME;	

	public int countOrder(List<RequestState> requestStates) throws SQLException, JSONException {
		PreparedStatement countOrderStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			StringBuilder stringBuilder = new StringBuilder(COUNT_ORDER_SQL);
			
			int auxCount = 0;
			for (RequestState requestState : requestStates) {
				if (auxCount++ == 0) {
					stringBuilder.append(" ");
					stringBuilder.append("WHERE " + STATE + "=\'" + requestState.toString() + "\'");
					continue;
				}
				stringBuilder.append(" OR " + STATE + "=\'" + requestState.toString() + "\'");
			}
			countOrderStmt = connection.prepareStatement(stringBuilder.toString());
			ResultSet resultSet = countOrderStmt.executeQuery();
			if (resultSet.next()) {
				return resultSet.getInt(1);
			}
			
			connection.commit();
		} catch (SQLException e) {
			e.printStackTrace();
			LOGGER.error("Couldn't count order.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(countOrderStmt, connection);
		}
		return 0;
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
