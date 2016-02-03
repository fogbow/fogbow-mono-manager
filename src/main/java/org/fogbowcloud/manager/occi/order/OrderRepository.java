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
	
	public void addOrder(String user, Order order) {
		LOGGER.debug("Adding order " + order.getId() + " to user " + user);
		List<Order> userOrders = orders.get(user);
		if (userOrders == null) {
			userOrders = new LinkedList<Order>();
			orders.put(user, userOrders);
		}
		if (orderExists(userOrders, order)) {
			return;
		}
		userOrders.add(order);
	}

	public List<Order> getOrdersIn(OrderState... states) {
		List<Order> allOrdersInState = new LinkedList<Order>();
		for (List<Order> userOrders : orders.values()) {
			for (Order order : userOrders) {
				if (order.getState().in(states)) {
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

	public Order get(String user, String orderId) {
		return get(user, orderId, true);
	}
	
	public Order get(String user, String orderId, boolean lookingForLocalOrder) {
		List<Order> userOrders = orders.get(user);
		if (userOrders == null) {
			LOGGER.debug("User " + user + " does not have orders.");
			return null;
		}
		for (Order order : userOrders) {
			if (order.getId().equals(orderId)) {
				if (lookingForLocalOrder && order.isLocal() 
						|| !lookingForLocalOrder && !order.isLocal()) {
					LOGGER.debug("Getting order " + order + " owner by user " + user);
					return order;					
				}
			}
		}
		LOGGER.debug("Order " + orderId + " owner by user " + user + " was not found.");
		return null;
	}

	public List<Order> getByUser(String user) {
		return getByUser(user, true);
	}
	
	public List<Order> getByUser(String user, boolean lookingForLocalOrder) {
		LOGGER.debug("Getting local orders by user " + user);
		List<Order> userOrders = orders.get(user);
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

	public void removeByUser(String user) {
		List<Order> ordersByUser = orders.get(user);
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
