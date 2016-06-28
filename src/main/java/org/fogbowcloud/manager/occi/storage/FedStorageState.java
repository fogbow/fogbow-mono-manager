package org.fogbowcloud.manager.occi.storage;

public class FedStorageState {

	private String fedStorageId;
	private String orderId;
	private String globalStorageId;
	private String user;
	
	public FedStorageState(String fedStorageId, String orderId, String globalStorageId, String user) {
		this.fedStorageId = fedStorageId;
		this.orderId = orderId;
		this.globalStorageId = globalStorageId;
		this.user = user;
	}

	protected String getFedStorageId() {
		return fedStorageId;
	}

	protected void setFedStorageId(String fedStorageId) {
		this.fedStorageId = fedStorageId;
	}

	protected String getOrderId() {
		return orderId;
	}

	protected void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	protected String getGlobalStorageId() {
		return globalStorageId;
	}

	protected void setGlobalStorageId(String globalStorageId) {
		this.globalStorageId = globalStorageId;
	}

	protected String getUser() {
		return user;
	}

	protected void setUser(String user) {
		this.user = user;
	}
	
	
}
