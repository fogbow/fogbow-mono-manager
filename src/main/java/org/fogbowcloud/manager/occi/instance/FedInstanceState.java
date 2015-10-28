package org.fogbowcloud.manager.occi.instance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Resource;

public class FedInstanceState {

	private String fedInstanceId;
	private String orderId;
	private String globalInstanceId;
	private String user;
	private Map<String, String> attributesMapping = new HashMap<String, String>();
	
//	public void mapAtt(String instanceAttName, String requestAttName) {
//		attributesMapping.put(instanceAttName, requestAttName);
//	}
	
	public Map<String, String> getAttributesMapping() {
		return attributesMapping;
	}
	
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
	
	public List<Resource> getResources() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public List<Link> getLinks() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void setLinks(List<Link> links) {
		// TODO Auto-generated method stub
		
	}
	
	public void addResource(Object object) {
		// TODO Auto-generated method stub
		
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
