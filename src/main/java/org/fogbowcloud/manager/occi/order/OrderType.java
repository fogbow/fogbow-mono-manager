package org.fogbowcloud.manager.occi.order;

public enum OrderType {
	
	ONE_TIME("one-time"), PERSISTENT("persistent");
	
	private String value;
	
	OrderType(String value) {
		this.value = value;
	}
	
	public String getValue() {
		return value;
	}
}