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

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FedInstanceState other = (FedInstanceState) obj;
		if (fedInstanceId == null) {
			if (other.fedInstanceId != null)
				return false;
		} else if (!fedInstanceId.equals(other.fedInstanceId))
			return false;
		if (globalInstanceId == null) {
			if (other.globalInstanceId != null)
				return false;
		} else if (!globalInstanceId.equals(other.globalInstanceId))
			return false;
		if (orderId == null) {
			if (other.orderId != null)
				return false;
		} else if (!orderId.equals(other.orderId))
			return false;
		if (user == null) {
			if (other.user != null)
				return false;
		} else if (!user.equals(other.user))
			return false;
		return true;
	}

	
	
}
