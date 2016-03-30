package org.fogbowcloud.manager.occi;

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
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.json.JSONException;

public class StorageLinkDataStore {

	private static final Logger LOGGER = Logger.getLogger(StorageLinkDataStore.class);
	protected static final String STORAGELINK_DATASTORE_URL = "storagelink_datastore_url";
	protected static final String STORAGELINK_DATASTORE_URL_DEFAULT = "jdbc:sqlite:/tmp/dbStorageLinkSQLite.db";
	protected static final String STORAGELINK_DATASTORE_SQLITE_DRIVER = "org.sqlite.JDBC";
	protected static final String STORAGELINK_TABLE_NAME = "t_storagelink";
	protected static final String STORAGELINK_ID = "id";
	protected static final String TARGER = "target";
	protected static final String DEVICE_ID = "device_id";
	protected static final String FEDERATION_TOKEN = "federation_token";
	protected static final String PROVIDING_MEMBER_ID = "providing_member_id";
	protected static final String IS_LOCAL = "is_local";
	protected static final String SOURCE = "source";
	protected static final String UPDATED = "update";
	
	private String dataStoreURL;
	
	public StorageLinkDataStore(Properties properties) {
		this.dataStoreURL = properties.getProperty(STORAGELINK_DATASTORE_URL, STORAGELINK_DATASTORE_URL_DEFAULT);
		
		Statement statement = null;
		Connection connection = null;
		try {
			LOGGER.debug("DatastoreURL: " + dataStoreURL);
			LOGGER.debug("DatastoreDriver: " + STORAGELINK_DATASTORE_SQLITE_DRIVER);

			Class.forName(STORAGELINK_DATASTORE_SQLITE_DRIVER);

			connection = getConnection();
			statement = connection.createStatement();
			statement.execute("CREATE TABLE IF NOT EXISTS " + STORAGELINK_TABLE_NAME + "(" 
							+ STORAGELINK_ID + " VARCHAR(255) PRIMARY KEY, "
							+ SOURCE + " VARCHAR(255), "
							+ TARGER + " VARCHAR(255), "
							+ DEVICE_ID + " VARCHAR(255), "
							+ FEDERATION_TOKEN + " TEXT, "
							+ PROVIDING_MEMBER_ID + " VARCHAR(255), "
							+ IS_LOCAL + " BOOLEAN)");
			statement.close();
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.error("Error while initializing the DataStore.", e);
		} finally {
			close(statement, connection);
		}
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
	
	private static final String INSERT_STORAGELINK_SQL = "INSERT INTO " + STORAGELINK_TABLE_NAME
			+ " (" + STORAGELINK_ID + "," + PROVIDING_MEMBER_ID + "," + SOURCE + "," + TARGER + ","
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
			storageLinkStmt.setString(2, storageLink.getProvadingMemberId());
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
			+ PROVIDING_MEMBER_ID + ", " + SOURCE + ", " + TARGER + ", " + FEDERATION_TOKEN + ", " 
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
						resultSet.getString(SOURCE), resultSet.getString(TARGER),
						resultSet.getString(DEVICE_ID), resultSet.getString(PROVIDING_MEMBER_ID), 
						tokenJsonStr != null ? Token.fromJSON(tokenJsonStr) : null, resultSet.getBoolean(IS_LOCAL)));
			}
					
			connection.commit();
			
			return storageLinks;
		} catch (SQLException e) {
			LOGGER.error("Couldn't retrive storage links.", e);
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
			+ PROVIDING_MEMBER_ID + "=?," + SOURCE + "=?," + FEDERATION_TOKEN + "=? ," + TARGER
			+ "=? ," + IS_LOCAL + "=?," + DEVICE_ID + "=?" + " WHERE " + STORAGELINK_ID + "=?";
	
	public boolean updateStorageLink(StorageLink storageLink) throws SQLException, JSONException {
		PreparedStatement updateStorageLinkStmt = null;
		Connection connection = null;
		try {
			connection = getConnection();
			connection.setAutoCommit(false);
			
			updateStorageLinkStmt = connection.prepareStatement(UPDATE_STORAGELINK_SQL);
			updateStorageLinkStmt.setString(1, storageLink.getProvadingMemberId());
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
	
}
