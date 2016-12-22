package org.fogbowcloud.manager.occi;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.storage.StorageLink;

public class ManagerDataStoreController {

	private static final Logger LOGGER = Logger.getLogger(ManagerDataStoreController.class);
	
	private ManagerDataStore managerDatabase;

	public ManagerDataStoreController(Properties properties) {
		this.managerDatabase = new ManagerDataStore(properties);
	}
	
	public ManagerDataStore getManagerDatabase() {
		return managerDatabase;
	}
	
	/*
	 * The get and remove operation in OrderRepository consider only local
	 * orders to allow these operations only from manager where the order
	 * was created.
	 */
	
	public void updateOrder(Order order) {
		try {
			this.managerDatabase.updateOrder(order);
		} catch (Exception e) {
			String errorMsg = "Error while try to update order.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
	}
	
	public List<Order> getOrdersByUser(String userId) {
		List<Order> userOrdersFound = new ArrayList<Order>();
		try {
			List<Order> ordersDB = this.managerDatabase.getOrders();
			for (Order order : ordersDB) {
				boolean hasTheSameUserId = order != null && order.getFederationToken() != null 
						&& order.getFederationToken().getUser() != null 
						&& order.getFederationToken().getUser().getId().equals(userId);
				
				if (hasTheSameUserId) {
					userOrdersFound.add(order);
				}
			}
		} catch (Exception e) {
			String errorMsg = "Error while try to get orders by user.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
		return userOrdersFound;
	}
	
	public void addOrder(Order order) {
		try {
			this.managerDatabase.addOrder(order);
		} catch (Exception e) {
			String errorMsg = "Error while try to add order.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}		
	}

	public List<Order> getOrdersIn(OrderState... states) {
		return getOrdersIn(null, states);
	}
	
	public List<Order> getOrdersIn(String resourceKind, OrderState... states) {
		List<Order> ordersInStateFound = new LinkedList<Order>();
		try {
			List<Order> ordersDB = this.managerDatabase.getOrders();
			for (Order order : ordersDB) {
				if (order.getState().in(states)) {
					if (resourceKind != null && !resourceKind.equals(order.getResourceKing())) {
						continue;
					}
					
					ordersInStateFound.add(order);
				}
			}
		} catch (Exception e) {
			String errorMsg = "Error while try to get orders by states and resource king.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
		return ordersInStateFound;
	}

	public Order getOrder(String orderId) {
		return getOrder(orderId, true);
	}
	
	public Order getOrder(String orderId, boolean lookingForLocalOrder) {
		try {
			Order order = this.managerDatabase.getOrder(orderId);
			if (lookingForLocalOrder && order.isLocal() 
					|| !lookingForLocalOrder && !order.isLocal()) {
				LOGGER.debug("Getting order id " + order);
				return order;						
			}
		} catch (Exception e) {
			String errorMsg = "Error while try to get order.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
		LOGGER.debug("Order id " + orderId + " was not found.");
		return null;
	}

	public Order getOrder(String userId, String orderId) {
		return getOrder(userId, orderId, true);
	}
	
	public Order getOrder(String userId, String orderId, boolean lookingForLocalOrder) {
		List<Order> userOrders = getOrdersByUser(userId);
		if (userOrders == null) {
			LOGGER.debug("User id " + userId + " does not have orders.");
			return null;
		}
		for (Order order : userOrders) {
			if (order.getId().equals(orderId)) {
				if (lookingForLocalOrder && order.isLocal() 
						|| !lookingForLocalOrder && !order.isLocal()) {
					LOGGER.debug("Getting order " + order + " owner by user id " + userId);
					return order;					
				}
			}
		}
		LOGGER.debug("Order " + orderId + " owner by user id " + userId + " was not found.");
		return null;
	}

	public List<Order> getOrdersByUserId(String userId) {
		return getOrdersByUserId(userId, true);
	}
	
	public List<Order> getOrdersByUserId(String userId, boolean lookingForLocalOrder) {
		LOGGER.debug("Getting local orders by user id " + userId);
		List<Order> userOrders = getOrdersByUser(userId);
		if (userOrders == null) {
			return new LinkedList<Order>();
		}		
		LinkedList<Order> userLocalOrders = new LinkedList<Order>();
		for (Order order : userOrders) {
			if (lookingForLocalOrder && order.isLocal() 
					|| !lookingForLocalOrder && !order.isLocal()) {
				userLocalOrders.add(order);
			}
		}
		return userLocalOrders;
	}

	public void removeOrderByUserId(String userId) {
		List<Order> ordersByUser = getOrdersByUser(userId);
		for (Order order : ordersByUser != null ? ordersByUser: new ArrayList<Order>()) {
			boolean onlyLocal = order.isLocal();
			if (onlyLocal) {
				removeOrder(order.getId());
			}
		}
	}

	public void removeOrder(String orderId) {
		LOGGER.debug("Removing orderId " + orderId);
		try {
			List<Order> orders  = this.managerDatabase.getOrders();
			for (Order order : orders) {					
				if (order.getId().equals(orderId) && order.isLocal()) {
					if (order.getState().equals(OrderState.CLOSED)) { 
						LOGGER.debug("Order " + orderId + " does not have an instance. Excluding order.");
						this.managerDatabase.removeOrder(order);
					} else {
						order.setState(OrderState.DELETED);
						this.managerDatabase.updateOrder(order);
					}
					return;
				}
			}
		} catch (Exception e) {
			String errorMsg = "Error while try to remove order (" + orderId + ").";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
	}

	public void excludeOrder(String orderId) {
		LOGGER.debug("Excluding orderId " + orderId);
		try {
			List<Order> ordersDB = this.managerDatabase.getOrders();
			for (Order order : ordersDB) {			
				if (order.getId().equals(orderId)) {
					this.managerDatabase.removeOrder(order);
					return;
				}
			}
		} catch (Exception e) {
			String errorMsg = "Error while try to exclude order (" + orderId + ").";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
	}

	public List<Order> getAllOrders() {
		try {
			return this.managerDatabase.getOrders();			
		} catch (Exception e) {
			String errorMsg = "Error while try to get all order.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
	}
	
	public List<Order> getAllLocalOrders() {
		List<Order> localOrdersFound = new LinkedList<Order>();
		List<Order> orders = getAllOrders();
		for (Order order : orders) {
			if (order.isLocal()){
				localOrdersFound.add(order);
			}
		}
		return localOrdersFound;
	}
	
	public List<Order> getAllServedOrders() {
		List<Order> remoteOrdersFound = new LinkedList<Order>();
		List<Order> orders = getAllOrders();
		for (Order order : orders) {
			if (!order.isLocal()){
				remoteOrdersFound.add(order);
			}
		}
		return remoteOrdersFound;
	}
	
	public Order getOrderByInstance(String instanceId) {
		List<Order> orders = getAllOrders();
		for (Order order : orders) {
			if (order.getState().in(OrderState.FULFILLED, OrderState.SPAWNING, OrderState.DELETED)
					&& instanceId.equals(order.getInstanceId())) {
				return order;
			}
		}
		return null;
	}
	
	public List<StorageLink> getStorageLinksByUser(String userId) {
		List<StorageLink> userStorageLinkFound = new ArrayList<StorageLink>();
		try {
			List<StorageLink> allStorageLink = this.managerDatabase.getStorageLinks();
			for (StorageLink storageLink : allStorageLink) {
				if (storageLink != null && storageLink.getFederationToken() != null 
						&& storageLink.getFederationToken().getUser() != null 
						&& storageLink.getFederationToken().getUser().getId().equals(userId)) {
					userStorageLinkFound.add(storageLink);
				}
			}
		} catch (Exception e) {
			String errorMsg = "Error while try to get all storage links by user.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
		return userStorageLinkFound;
	}	
	
	public boolean addStorageLink(StorageLink storageLink) {
		String userId = storageLink != null && storageLink.getFederationToken() != null 
				&& storageLink.getFederationToken().getUser() != null 
				? storageLink.getFederationToken().getUser().getId() : null;
		LOGGER.debug("Adding storage link " + storageLink.getId() 
				+ " to user id " + userId);
		try {
			this.managerDatabase.addStorageLink(storageLink);
			return true;			
		} catch (Exception e) {
			String errorMsg = "Error while try to add storage link.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
	}
		
	public List<StorageLink> getAllStorageLinks() {
		List<StorageLink> storageLinks = new LinkedList<StorageLink>();
		try {
			storageLinks = this.managerDatabase.getStorageLinks();			
		} catch (Exception e) {
			String errorMsg = "Error while try to get all storage links.";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
		}
		return storageLinks;
	}
	
	public StorageLink getStorageLink(String storageLinkId) {
		return getStorageLink(null, storageLinkId);
	}
	
	public StorageLink getStorageLink(String userId, String storageLinkId) {
		List<StorageLink> userStorageLinks = null;
		if (userId == null) {
			userStorageLinks = getAllStorageLinks();
		} else {
			userStorageLinks = getStorageLinksByUser(userId);			
		}
		for (StorageLink storageLink : userStorageLinks) {
			if (storageLink.getId().equals(storageLinkId)) {
				LOGGER.debug("Getting storage link id " + storageLink);
				return storageLink;
			}
		}
		return null;		
	}
	
	public List<StorageLink> getAllStorageLinkByInstance(String instanceId, String type) {
		List<StorageLink> storageLinksFound = new ArrayList<StorageLink>();
		List<StorageLink> allStorageLinks = this.getAllStorageLinks();
		for (StorageLink storageLink : allStorageLinks) {
			if (type.equals(OrderConstants.COMPUTE_TERM) && instanceId.equals(storageLink.getSource()) 
					|| type.equals(OrderConstants.STORAGE_TERM) && instanceId.equals(storageLink.getTarget())) {
				LOGGER.debug("Getting storage link id " + storageLink);
				storageLinksFound.add(storageLink);					
			} 
		}
		LOGGER.debug("Storage link id, by instance id : (" + instanceId + "), was not found.");
		return storageLinksFound;
	}	
	
	public void removeAllStorageLinksByInstance(String instanceId, String type) {
		List<StorageLink> userStorageLinks = getAllStorageLinks();
			for (StorageLink storageLink : new ArrayList<StorageLink>(
					userStorageLinks)) {
				if (type.equals(OrderConstants.COMPUTE_TERM) && instanceId.equals(storageLink.getSource()) 
						|| type.equals(OrderConstants.STORAGE_TERM) && instanceId.equals(storageLink.getTarget())) {				
					try {
						this.managerDatabase.removeStorageLink(storageLink);
					} catch (SQLException e) {
						String errorMsg = "Error while try to remove storage link.";
						LOGGER.error(errorMsg, e);
						throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
					}
				}
			}
		LOGGER.debug("Removing all storage link with id "
				+ "(" + instanceId + ") and type (" + type + ").");
	}
	
	public void removeStorageLink(String storageLinkId) {
		LOGGER.debug("Removing storageLinkId " + storageLinkId);
		
		List<StorageLink> allStorageLinks = getAllStorageLinks();
		for (StorageLink storageLink : allStorageLinks) {
			if (storageLink.getId().equals(storageLinkId)) {
				try {
					this.managerDatabase.removeStorageLink(storageLink);
					return;
				} catch (SQLException e) {
					String errorMsg = "Error while try to remove storage link(" + storageLinkId + ").";
					LOGGER.error(errorMsg, e);
					throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);
				}
			}			
		}		
	}

	public List<Order> getOrdersByState(OrderState orderState) {
		try {
			return this.managerDatabase.getOrders(OrderState.PENDING);
		} catch (Exception e) {
			String errorMsg = "Error while try to get orders by status(" + orderState + ").";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);			
		}
	}

	public List<String> getFederationMembersServeredBy(String orderId) {
		try {
			return this.managerDatabase.getFederationMembersServeredBy(orderId);
		} catch (Exception e) {
			String errorMsg = "Error while try to get federation members of order id(" + orderId + ").";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);			
		}
	}

	public void addOrderSyncronous(String orderId, long syncronousTime, String federationMemberServered) {
		try {
			this.managerDatabase.updateOrderAsyncronous(orderId, syncronousTime, true);
			this.managerDatabase.addFederationMemberServered(orderId, federationMemberServered);
		} catch (Exception e) {
			String errorMsg = "Error while try to update order syncronous(" + orderId + ").";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);			
		}
	}
	
	public void removeOrderSyncronous(String orderId) {
		int zero = 0;
		boolean syncronousStatus = false;
		updateOrderSyncronous(orderId, zero, syncronousStatus);
	}

	public void updateOrderSyncronous(String orderId, long currentTimeMillis) {
		boolean syncronousStatus = true;
		updateOrderSyncronous(orderId, currentTimeMillis, syncronousStatus);
	}
	
	public void updateOrderSyncronous(String orderId, long currentTimeMillis, boolean syncronousStatus) {
		try {
			this.managerDatabase.updateOrderAsyncronous(orderId, currentTimeMillis, syncronousStatus);
		} catch (Exception e) {
			String errorMsg = "Error while try to remove/update order syncronous(" + orderId + ").";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);	
		} 		
	}

	public boolean isOrderSyncronous(String orderId) {
		try {
			boolean isOrderSyncronous = true;
			Order order = this.managerDatabase.getOrder(orderId, isOrderSyncronous);
			if (order != null) {
				return true;
			}
			return false;
		} catch (Exception e) {
			String errorMsg = "Error while try to check order syncronous(" + orderId + ").";
			LOGGER.error(errorMsg, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMsg);	
		} 	
	}		
}