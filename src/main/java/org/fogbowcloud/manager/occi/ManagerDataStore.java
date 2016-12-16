package org.fogbowcloud.manager.occi;

import java.sql.Connection;
import java.sql.DriverManager;
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
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.storage.StorageLink;
import org.json.JSONException;
import org.json.JSONObject;
import org.sqlite.SQLiteConfig;

public class ManagerDataStore {

	public static final String ERROR_WHILE_INITIALIZING_THE_DATA_STORE = "Error while initializing the Manager DataStore.";
	private static final Logger LOGGER = Logger.getLogger(ManagerDataStore.class);
	public static final String MANAGER_DATASTORE_URL = "manager_datastore_url";
	private static final String DEFAULT_DATASTORE_NAME = "datastore_manager.slite";
	protected static final String MANAGER_DATASTORE_SQLITE_DRIVER = "org.sqlite.JDBC";
	protected static final String ORDER_TABLE_NAME = "t_order";
	protected static final String ORDER_ID = "order_id";
	protected static final String PROVIDING_MEMBER_ID = "providing_member_id";
	protected static final String INSTANCE_ID = "instance_id";
	protected static final String REQUESTING_MEMBER_ID = "requesting_member_id";
	protected static final String FEDERATION_TOKEN = "federation_token";
	protected static final String FULFILLED_TIME = "fulfilled_time";
	protected static final String IS_LOCAL = "is_local";
	protected static final String STATE = "state";
	protected static final String RESOURCE_KIND = "resource_kind";
	protected static final String CATEGORIES = "categories";
	protected static final String XOCCI_ATTRIBUTES = "xocci_attributes";
	protected static final String UPDATED = "updated";
	protected static final String SYNCRONOUS_STATUS = "asyncronous_status";
	protected static final String SYNCRONOUS_TIMESTAMP = "asyncronous_timestamp";
	
	protected static final String STORAGELINK_TABLE_NAME = "t_storagelink";
	protected static final String STORAGELINK_ID = "storage_link_id";
	protected static final String TARGET = "target";
	protected static final String DEVICE_ID = "device_id";
	protected static final String SOURCE = "source";
	protected static final String FEDERATION_MEMBER_SERVERED_TABLE_NAME = "t_federation_member_servered";
	protected static final String FEDERTION_MEMBER_ID = "federation_member_id";
	
	private String dataStoreURL;

	public ManagerDataStore(Properties properties) {
		String dataStoreURLProperties = properties.getProperty(MANAGER_DATASTORE_URL);
		this.dataStoreURL = DataStoreHelper.getDataStoreUrl(dataStoreURLProperties,
				DEFAULT_DATASTORE_NAME);
		
		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("DatastoreURL: " + dataStoreURLProperties);
			LOGGER.debug("DatastoreDriver: " + MANAGER_DATASTORE_SQLITE_DRIVER);

			Class.forName(MANAGER_DATASTORE_SQLITE_DRIVER);

			connection = getConnection();
			statement = connection.createStatement();
			statement.execute("PRAGMA foreign_keys = ON;");
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
							+ SYNCRONOUS_STATUS + " BOOLEAN, "
							+ SYNCRONOUS_TIMESTAMP + " TIMESTAMP, "
							+ XOCCI_ATTRIBUTES + " TEXT)");
			statement.execute("CREATE TABLE IF NOT EXISTS " + STORAGELINK_TABLE_NAME + "(" 
							+ STORAGELINK_ID + " VARCHAR(255) PRIMARY KEY, "
							+ SOURCE + " VARCHAR(255), "
							+ TARGET + " VARCHAR(255), "
							+ DEVICE_ID + " VARCHAR(255), "
							+ FEDERATION_TOKEN + " TEXT, "
							+ PROVIDING_MEMBER_ID + " VARCHAR(255), "
							+ IS_LOCAL + " BOOLEAN)");			
			statement.execute("CREATE TABLE IF NOT EXISTS " + FEDERATION_MEMBER_SERVERED_TABLE_NAME + "(" 
							+ FEDERTION_MEMBER_ID + " VARCHAR(255) PRIMARY KEY, "
							+ ORDER_ID + " VARCHAR(255) NOT NULL, "
							+ "FOREIGN KEY (" + ORDER_ID + ") REFERENCES " 
							+ ORDER_TABLE_NAME + "(" + ORDER_ID + ") ON DELETE CASCADE)");			
			statement.close();
		} catch (Exception e) {
			LOGGER.error(ERROR_WHILE_INITIALIZING_THE_DATA_STORE, e);
			throw new Error(ERROR_WHILE_INITIALIZING_THE_DATA_STORE, e);
		} finally {
			close(statement, connection);
		}
	}
	
	private static final String INSERT_ORDER_SQL = "INSERT INTO " + ORDER_TABLE_NAME
			+ " (" + ORDER_ID + "," + INSTANCE_ID + "," + PROVIDING_MEMBER_ID + "," + REQUESTING_MEMBER_ID + "," 
			+ FEDERATION_TOKEN + "," + FULFILLED_TIME + "," + IS_LOCAL + "," + STATE + "," + CATEGORIES + ","
			+ UPDATED + "," + XOCCI_ATTRIBUTES + ")"			
			+ " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
	
	public boolean addOrder(Order order) throws SQLException, JSONException {
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
			JSONObject xOCCIAtt = JSONHelper.mountXOCCIAttrJSON(order.getxOCCIAtt());
			orderStmt.setString(11, xOCCIAtt != null ? xOCCIAtt.toString() : null);
			orderStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
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
			+ IS_LOCAL + ", " + STATE + ", " + CATEGORIES + ", " + XOCCI_ATTRIBUTES + ", " + SYNCRONOUS_TIMESTAMP
			+ ", " + SYNCRONOUS_STATUS + " FROM " + ORDER_TABLE_NAME;
	
	public List<Order> getOrders() throws SQLException, JSONException {
		return getOrders(null);
	}
	
	public List<Order> getOrders(OrderState orderState) throws SQLException, JSONException {
		PreparedStatement ordersStmt = null;
		Connection connection = null;
		List<Order> orders = new ArrayList<Order>();
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			String ordersStmtStr = GET_ORDERS_SQL;
			if (orderState != null) {
				ordersStmtStr += " WHERE " + STATE + "=?";
			}
			
			ordersStmt = connection.prepareStatement(ordersStmtStr);
			if (orderState != null) {
				ordersStmt.setString(1, orderState.toString());		
			}
			ResultSet resultSet = ordersStmt.executeQuery();
			while (resultSet.next()) {
				resultSet.getString(1);
				
				Order order = new Order(resultSet.getString(1), Token.fromJSON(resultSet
						.getString(5)), resultSet.getString(2), resultSet.getString(3), resultSet
						.getString(4), resultSet.getLong(6), resultSet.getBoolean(7), OrderState
						.getState(resultSet.getString(8)), JSONHelper.getCategoriesFromJSON(resultSet
						.getString(9)), JSONHelper.getXOCCIAttrFromJSON(resultSet.getString(10)));
				order.setSyncronousTime(resultSet.getLong(11));
				order.setSyncronousStatus(resultSet.getBoolean(12));
				
				orders.add(order);
			}
					
			connection.commit();
			
			return orders;
		} catch (SQLException e) {
			LOGGER.error("Couldn't retrieve orders.", e);
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
	
	private static final String GET_SPECIFIC_ORDER_SQL = GET_ORDERS_SQL + " WHERE " + ORDER_ID + "=?";
			
	public Order getOrder(String orderId) throws SQLException, JSONException  {
		return getOrder(orderId, false);
	}
	
	public Order getOrder(String orderId, boolean isOrderSyncronous) throws SQLException, JSONException  {
		PreparedStatement ordersStmt = null;
		Connection connection = null;
		Order order = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			String specificOrderStmtStr = GET_SPECIFIC_ORDER_SQL;
			
			if (isOrderSyncronous) {
				specificOrderStmtStr += " AND " + SYNCRONOUS_STATUS + "=?";
			}
			
			ordersStmt = connection.prepareStatement(specificOrderStmtStr);
			ordersStmt.setString(1, orderId);
			if (isOrderSyncronous) {
				ordersStmt.setBoolean(2, isOrderSyncronous);
			}
			ResultSet resultSet = ordersStmt.executeQuery();
			if (resultSet.next()) {
				resultSet.getString(1);
				
				order = new Order(resultSet.getString(1), Token.fromJSON(resultSet
						.getString(5)), resultSet.getString(2), resultSet.getString(3), resultSet
						.getString(4), resultSet.getLong(6), resultSet.getBoolean(7), OrderState
						.getState(resultSet.getString(8)), JSONHelper.getCategoriesFromJSON(resultSet
						.getString(9)), JSONHelper.getXOCCIAttrFromJSON(resultSet.getString(10)));
				order.setSyncronousTime(resultSet.getLong(11));
				order.setSyncronousStatus(resultSet.getBoolean(12));
			}
					
			connection.commit();
			
			return order;
		} catch (SQLException e) {
			LOGGER.error("Couldn't retrieve order.", e);
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
		return null;
	}	

	private static final String REMOVE_ORDER_SQL = "DELETE"
			+ " FROM " + ORDER_TABLE_NAME 
			+ " WHERE " + ORDER_ID + " = ?";
	
	public boolean removeOrder(Order order) throws SQLException {
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
	
	public boolean updateOrder(Order order) throws SQLException, JSONException {
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
	
	private static final String UPDATE_ORDER_ASYNCRONOUS_SQL = "UPDATE " + ORDER_TABLE_NAME + " SET "
			+ SYNCRONOUS_TIMESTAMP + "=? , " + SYNCRONOUS_STATUS + "=?" + " WHERE " + ORDER_ID + "=?";
	
	public boolean updateOrderAsyncronous(String orderId, long syncronousTime, boolean syncronousStatus) throws SQLException, JSONException {
		PreparedStatement updateOrderStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			updateOrderStmt = connection.prepareStatement(UPDATE_ORDER_ASYNCRONOUS_SQL);
			updateOrderStmt.setTimestamp(1, new Timestamp(syncronousTime));
			updateOrderStmt.setBoolean(2, syncronousStatus);
			updateOrderStmt.setString(3, orderId);
			updateOrderStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't update order asyncronous.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(updateOrderStmt, connection);
		}
		return false;
	}
		
	private static final String COUNT_ORDER_SQL = "SELECT COUNT(*) FROM " + ORDER_TABLE_NAME;	

	public int countOrder(List<OrderState> orderStates) throws SQLException, JSONException {
		PreparedStatement countOrderStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			StringBuilder stringBuilder = new StringBuilder(COUNT_ORDER_SQL);
			
			int auxCount = 0;
			for (OrderState orderState : orderStates) {
				if (auxCount++ == 0) {
					stringBuilder.append(" ");
					stringBuilder.append("WHERE " + STATE + "=\'" + orderState.toString() + "\'");
					continue;
				}
				stringBuilder.append(" OR " + STATE + "=\'" + orderState.toString() + "\'");
			}
			countOrderStmt = connection.prepareStatement(stringBuilder.toString());
			ResultSet resultSet = countOrderStmt.executeQuery();
			if (resultSet.next()) {
				return resultSet.getInt(1);
			}
			
			connection.commit();
		} catch (SQLException e) {
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
	 * Storage Link
	 */
	
	private static final String INSERT_STORAGELINK_SQL = "INSERT INTO " + STORAGELINK_TABLE_NAME
			+ " (" + STORAGELINK_ID + "," + PROVIDING_MEMBER_ID + "," + SOURCE + "," + TARGET + ","
			+ FEDERATION_TOKEN + "," + DEVICE_ID + "," + IS_LOCAL + ")" 
			+ " VALUES (?,?,?,?,?,?,?)";
	
	public boolean addStorageLink(StorageLink storageLink) throws SQLException, JSONException {
		PreparedStatement storageLinkStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			storageLinkStmt = connection.prepareStatement(INSERT_STORAGELINK_SQL);
			storageLinkStmt.setString(1, storageLink.getId());
			storageLinkStmt.setString(2, storageLink.getProvidingMemberId());
			storageLinkStmt.setString(3, storageLink.getSource());
			storageLinkStmt.setString(4, storageLink.getTarget());
			Token federationToken = storageLink.getFederationToken();
			storageLinkStmt.setString(5, federationToken != null ? federationToken.toJSON()
							.toString() : null);
			storageLinkStmt.setString(6, storageLink.getDeviceId());
			storageLinkStmt.setBoolean(7, storageLink.isLocal());
			storageLinkStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't create storage link.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(storageLinkStmt, connection);
		}
		return false;
	}
	
	private static final String GET_STORAGELINK_SQL = "SELECT " + STORAGELINK_ID + ", " 
			+ PROVIDING_MEMBER_ID + ", " + SOURCE + ", " + TARGET + ", " + FEDERATION_TOKEN + ", " 
			+ IS_LOCAL + ", " + DEVICE_ID  
			+ " FROM " + STORAGELINK_TABLE_NAME;
	
	public List<StorageLink> getStorageLinks() throws SQLException, JSONException {
		PreparedStatement storageLinksStmt = null;
		Connection connection = null;
		List<StorageLink> storageLinks = new ArrayList<StorageLink>();
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			String storageLinksStmtStr = GET_STORAGELINK_SQL;
			
			storageLinksStmt = connection.prepareStatement(storageLinksStmtStr);
			ResultSet resultSet = storageLinksStmt.executeQuery();
			while (resultSet.next()) {
				String tokenJsonStr = resultSet.getString(FEDERATION_TOKEN);
				storageLinks.add(new StorageLink(resultSet.getString(STORAGELINK_ID),
						resultSet.getString(SOURCE), resultSet.getString(TARGET),
						resultSet.getString(DEVICE_ID), resultSet.getString(PROVIDING_MEMBER_ID), 
						tokenJsonStr != null ? Token.fromJSON(tokenJsonStr) : null, resultSet.getBoolean(IS_LOCAL)));
			}
					
			connection.commit();
			
			return storageLinks;
		} catch (SQLException e) {
			LOGGER.error("Couldn't retrieve storage links.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(storageLinksStmt, connection);
		}
		return storageLinks;
	}	
	
	private static final String REMOVE_STORAGELINK_SQL = "DELETE"
			+ " FROM " + STORAGELINK_TABLE_NAME + " WHERE " + STORAGELINK_ID + " = ?";
	
	public boolean removeStorageLink(StorageLink storageLink) throws SQLException {
		PreparedStatement removeStorageLinkStmt = null;
		Connection connection = null;
		try {	
			connection = getConnection();
			connection.setAutoCommit(false);
			
			removeStorageLinkStmt = connection.prepareStatement(REMOVE_STORAGELINK_SQL);
			removeStorageLinkStmt.setString(1, storageLink.getId());
			removeStorageLinkStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't remove storage link.", e);
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
	
	private static final String UPDATE_STORAGELINK_SQL = "UPDATE " + STORAGELINK_TABLE_NAME + " SET "
			+ PROVIDING_MEMBER_ID + "=?," + SOURCE + "=?," + FEDERATION_TOKEN + "=? ," + TARGET
			+ "=? ," + IS_LOCAL + "=?," + DEVICE_ID + "=?" + " WHERE " + STORAGELINK_ID + "=?";
	
	public boolean updateStorageLink(StorageLink storageLink) throws SQLException, JSONException {
		PreparedStatement updateStorageLinkStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			updateStorageLinkStmt = connection.prepareStatement(UPDATE_STORAGELINK_SQL);
			updateStorageLinkStmt.setString(1, storageLink.getProvidingMemberId());
			updateStorageLinkStmt.setString(2, storageLink.getSource());
			Token federationToken = storageLink.getFederationToken();
			updateStorageLinkStmt.setString(3, federationToken != null ? 
					federationToken.toJSON().toString() : null);
			updateStorageLinkStmt.setString(4, storageLink.getTarget());
			updateStorageLinkStmt.setBoolean(5, storageLink.isLocal());
			updateStorageLinkStmt.setString(6, storageLink.getDeviceId());
			updateStorageLinkStmt.setString(7, storageLink.getId());
			updateStorageLinkStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't update storage link.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(updateStorageLinkStmt, connection);
		}
		return false;
	}	
	
	/**
	 * Federation member servered
	 */	
	
	private static final String INSERT_FEDERATION_MEMBER_SERVERED_SQL = "INSERT INTO " + 
			FEDERATION_MEMBER_SERVERED_TABLE_NAME + " (" + FEDERTION_MEMBER_ID + "," + ORDER_ID + ")"			
			+ " VALUES (?,?)";
	
	public boolean addFederationMemberServered(String orderId, String federationMemberServerd) throws SQLException, JSONException {
		PreparedStatement federationMemberStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			federationMemberStmt = connection.prepareStatement(INSERT_FEDERATION_MEMBER_SERVERED_SQL);
			federationMemberStmt.setString(1, federationMemberServerd);
			federationMemberStmt.setString(2, orderId);
			federationMemberStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't create federation member.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(federationMemberStmt, connection);
		}
		return false;
	}
	
	private static final String GET_FEDERATION_MEMBERS_SQL = "SELECT " + FEDERTION_MEMBER_ID
			+ " FROM " + FEDERATION_MEMBER_SERVERED_TABLE_NAME 
			+ " WHERE " + ORDER_ID + "=?";
	
	public List<String> getFederationMembersServeredBy(String orderId) throws SQLException, JSONException {
		PreparedStatement federationMemStmt = null;
		Connection connection = null;
		List<String> federationMembersServered = new ArrayList<String>();
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			String ordersStmtStr = GET_FEDERATION_MEMBERS_SQL;
			
			federationMemStmt = connection.prepareStatement(ordersStmtStr);
			federationMemStmt.setString(1, orderId);
			ResultSet resultSet = federationMemStmt.executeQuery();
			while (resultSet.next()) {
				resultSet.getString(1);
				
				federationMembersServered.add(resultSet.getString(1));
			}
					
			connection.commit();
			
			return federationMembersServered;
		} catch (SQLException e) {
			LOGGER.error("Couldn't retrieve federation members serverd.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(federationMemStmt, connection);
		}
		return federationMembersServered;
	}	
	
	private static final String REMOVE_FEDERATION_MEMBER_SERVERED_SQL = "DELETE"
			+ " FROM " + FEDERATION_MEMBER_SERVERED_TABLE_NAME 
			+ " WHERE " + FEDERTION_MEMBER_ID + " = ?";
	
	public boolean removeFederationMemberServed(String federationMemberServered) throws SQLException {
		PreparedStatement removeFederationMemberServeredStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			removeFederationMemberServeredStmt = connection
					.prepareStatement(REMOVE_FEDERATION_MEMBER_SERVERED_SQL);
			removeFederationMemberServeredStmt.setString(1, federationMemberServered);
			removeFederationMemberServeredStmt.executeUpdate();
			
			connection.commit();
			return true;
		} catch (SQLException e) {
			LOGGER.error("Couldn't remove federation member servered.", e);
			try {
				if (connection != null) {
					connection.rollback();
				}
			} catch (SQLException e1) {
				LOGGER.error("Couldn't rollback transaction.", e1);
			}
		} finally {
			close(removeFederationMemberServeredStmt, connection);
		}
		return false;
	}
	
	public Connection getConnection() throws SQLException {
		try {
			SQLiteConfig config = new SQLiteConfig();
			config.enforceForeignKeys(true);  
			return DriverManager.getConnection(this.dataStoreURL, config.toProperties());
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
