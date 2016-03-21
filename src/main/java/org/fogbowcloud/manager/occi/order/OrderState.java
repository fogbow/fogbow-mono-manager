package org.fogbowcloud.manager.occi.order;

public enum OrderState {

	/**
	 * Open: The order is not fulfilled.
	 * 
	 * Failed: The order failed because bad parameters were specified.
	 * 
	 * Spawning: The order received an instance but it is not ready to be used yet.
	 * 
	 * Fulfilled: The order is currently active (fulfilled) and has an
	 * associated Instance.
	 * 
	 * Deleted: The order was deleted, but it still has a instance related to it.
	 * 
	 * Closed: The order either completed (a Instance was launched and
	 * subsequently was interrupted or terminated), or was not fulfilled within
	 * the period specified.
	 */
	OPEN("open"), FAILED("failed"), SPAWNING("spawning"), FULFILLED("fulfilled"), 
			DELETED("deleted"), CLOSED("closed"), PENDING("pending");

	private String value;

	private OrderState(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}
	
	public static OrderState getState(String stateStr) {
		for (OrderState state : OrderState.values()) {
			if (state.toString().equals(stateStr)) {
				return state;
			}
		}
		return null;
	}
	
	public boolean in(OrderState... orderStates) {
		for (OrderState orderState : orderStates) {
			if (orderState.equals(this)){
				return true;
			}
		}
		return false;
	}
	
	public boolean notIn(OrderState... orderStates) {
		for (OrderState orderState : orderStates) {
			if (orderState.equals(this)){
				return false;
			}
		}
		return true;
	}
}
