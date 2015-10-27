package org.fogbowcloud.manager.occi.instance;

public class FedInstanceState {

	private String fedInstanceId;
	private String orderId;
	private String globalInstanceId;
	private String user;
	
	public FedInstanceState(String fedInstanceId, String orderId, String globalInstanceId, String user) {
		this.fedInstanceId = fedInstanceId;
		this.orderId = orderId;
		this.globalInstanceId = globalInstanceId;
		this.user = user;
	}
	
	public String getFedInstanceId() {
		return fedInstanceId;
	}
	public void setFedInstanceId(String fedInstanceId) {
		this.fedInstanceId = fedInstanceId;
	}
	public String getOrderId() {
		return orderId;
	}
	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}
	public String getGlobalInstanceId() {
		return globalInstanceId;
	}
	public void setGlobalInstanceId(String globalInstanceId) {
		this.globalInstanceId = globalInstanceId;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}

	
	
}
