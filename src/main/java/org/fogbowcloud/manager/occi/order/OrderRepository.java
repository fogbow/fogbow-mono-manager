package org.fogbowcloud.manager.occi.order;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class OrderRepository {

	private static final Logger LOGGER = Logger.getLogger(OrderRepository.class);

	private Map<String, List<Order>> orders = new HashMap<String, List<Order>>();

	/*
	 * The get and remove operation in OrderRepository consider only local
	 * orders to allow these operations only from manager where the order
	 * was created.
	 */
	
	protected boolean orderExists(List<Order> orders, Order userOrder) {
		for (Order order : orders) {
			if (order.getId().equals(userOrder.getId())) {
				return true;
			}
		}
		return false;
	}
	
	public void addOrder(String userId, Order order) {
		LOGGER.debug("Adding order " + order.getId() + " to user id " + userId);
		List<Order> userOrders = orders.get(userId);
		if (userOrders == null) {
			userOrders = new LinkedList<Order>();
			orders.put(userId, userOrders);
		}
		if (orderExists(userOrders, order)) {
			return;
		}
		userOrders.add(order);
	}

	public List<Order> getOrdersIn(OrderState... states) {
		return getOrdersIn(null, states);
	}
	
	public List<Order> getOrdersIn(String resourceKind, OrderState... states) {
		List<Order> allOrdersInState = new LinkedList<Order>();
		for (List<Order> userOrders : orders.values()) {
			for (Order order : userOrders) {
				if (order.getState().in(states)) {
					if (resourceKind != null && !resourceKind.equals(order.getResourceKing())) {
						continue;
					}
					allOrdersInState.add(order);
				}
			}
		}
		return allOrdersInState;
	}

	public Order get(String orderId) {
		return get(orderId, true);
	}
	
	public Order get(String orderId, boolean lookingForLocalOrder) {
		for (List<Order> userOrders : orders.values()) {
			for (Order order : userOrders) {
				if (order.getId().equals(orderId)) {
					if (lookingForLocalOrder && order.isLocal() 
							|| !lookingForLocalOrder && !order.isLocal()) {
						LOGGER.debug("Getting order id " + order);
						return order;						
					}
				}
			}
		}
		LOGGER.debug("Order id " + orderId + " was not found.");
		return null;
	}

	public Order get(String userId, String orderId) {
		return get(userId, orderId, true);
	}
	
	public Order get(String userId, String orderId, boolean lookingForLocalOrder) {
		List<Order> userOrders = orders.get(userId);
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

	public List<Order> getByUserId(String userId) {
		return getByUserId(userId, true);
	}
	
	public List<Order> getByUserId(String userId, boolean lookingForLocalOrder) {
		LOGGER.debug("Getting local orders by user id " + userId);
		List<Order> userOrders = orders.get(userId);
		if (userOrders == null) {
			return new LinkedList<Order>();
		}		
		LinkedList<Order> userLocalOrders = new LinkedList<Order>();
		for (Order order : userOrders) {
			if (lookingForLocalOrder && order.isLocal()) {
				userLocalOrders.add(order);
			} else if (!lookingForLocalOrder && !order.isLocal()) {
				userLocalOrders.add(order);
			}
		}
		return userLocalOrders;
	}

	public void removeByUserId(String userId) {
		List<Order> ordersByUser = orders.get(userId);
		if (ordersByUser != null) {
			for (Order order : ordersByUser) {
				if (order.isLocal()) {
					remove(order.getId());
				}
			}
		}
	}

	public void remove(String orderId) {
		LOGGER.debug("Removing orderId " + orderId);

		for (List<Order> userOrders : orders.values()) {
			Iterator<Order> iterator = userOrders.iterator();
			while (iterator.hasNext()) {
				Order order = (Order) iterator.next();
				if (order.getId().equals(orderId) && order.isLocal()) {
					if (order.getState().equals(OrderState.CLOSED)) { 
						LOGGER.debug("Order " + orderId + " does not have an instance. Excluding order.");
						iterator.remove();
					} else {
						order.setState(OrderState.DELETED);
					}
					return;
				}
			}
		}
	}

	public void exclude(String orderId) {
		LOGGER.debug("Excluing orderId " + orderId);

		for (List<Order> userOrders : orders.values()) {
			Iterator<Order> iterator = userOrders.iterator();
			while (iterator.hasNext()) {
				Order order = (Order) iterator.next();
				if (order.getId().equals(orderId)) {
					iterator.remove();
					return;
				}
			}
		}
	}

	public List<Order> getAllOrders() {
		List<Order> allOrders = new LinkedList<Order>();
		for (List<Order> userOrders : orders.values()) {
			for (Order order : userOrders) {
				allOrders.add(order);
			}
		}
		return allOrders;
	}
	
	public List<Order> getAllLocalOrders() {
		List<Order> allLocalOrders = new LinkedList<Order>();
		for (List<Order> userOrders : orders.values()) {
			for (Order order : userOrders) {
				if (order.isLocal()){
					allLocalOrders.add(order);
				}
			}
		}
		return allLocalOrders;
	}
	
	public List<Order> getAllServedOrders() {
		List<Order> allRemoteOrders = new LinkedList<Order>();
		for (List<Order> userOrders : orders.values()) {
			for (Order order : userOrders) {
				if (!order.isLocal()){
					allRemoteOrders.add(order);
				}
			}
		}
		return allRemoteOrders;
	}
	
	public Order getOrderByInstance(String instanceId) {
		for (List<Order> userOrders : orders.values()) {
			for (Order order : userOrders) {
				if (order.getState().in(OrderState.FULFILLED, 
						OrderState.SPAWNING, OrderState.DELETED)
						&& instanceId.equals(order.getInstanceId())) {
					return order;
				}
			}
		}
		return null;
	}
}
