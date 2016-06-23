package org.fogbowcloud.manager.occi.network;

public class FedNetworkState {

	private String fedNetworkId;
	private String orderId;
	private String globalInstanceId;
	private String user;
	private String address;
	private String allocation;
	private String gateway;

	public FedNetworkState(String fedInstanceId, String orderId, String globalInstanceId, String user, 
			String address, String allocation, String gateway) {
		this.fedNetworkId = fedInstanceId;
		this.orderId = orderId;
		this.globalInstanceId = globalInstanceId;
		this.user = user;
		this.address = address;
		this.allocation = allocation;
		this.gateway = gateway;
	}
	
	public String getFedInstanceId() {
		return fedNetworkId;
	}
	
	public void setFedInstanceId(String fedInstanceId) {
		this.fedNetworkId = fedInstanceId;
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

	protected String getAddress() {
		return address;
	}

	protected void setAddress(String address) {
		this.address = address;
	}

	protected String getAllocation() {
		return allocation;
	}

	protected void setAllocation(String allocation) {
		this.allocation = allocation;
	}

	protected String getGateway() {
		return gateway;
	}

	protected void setGateway(String gateway) {
		this.gateway = gateway;
	}

	
}
