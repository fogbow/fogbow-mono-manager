package org.fogbowcloud.manager.occi.instance;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;

public class FedInstanceState {

	private String fedInstanceId;
	private String orderId;
	private String globalInstanceId;
	private String userId;
	private List<Category> categories = new ArrayList<Category>();
	private List<Link> links = new ArrayList<Link>();
	
	public FedInstanceState(String fedInstanceId, String orderId, List<Category> categories, List<Link> links, String globalInstanceId, String userId) {
		this.fedInstanceId = fedInstanceId;
		this.orderId = orderId;
		this.categories = categories;
		this.links = links;
		this.globalInstanceId = globalInstanceId;
		this.userId = userId;
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

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public List<Category> getCategories() {
		return this.categories;
	}

	public List<Link> getLinks() {
		return this.links;
	}
	
	public void setLinks(List<Link> links) {
		this.links = links;
	}
	
	public void addCategory(Category newCategory) {
		categories.add(newCategory);
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
		if (userId == null) {
			if (other.userId != null)
				return false;
		} else if (!userId.equals(other.userId))
			return false;
		if (categories == null) {
			if (other.categories != null)
				return false;
		} else if (!categories.equals(other.categories))
			return false;
		if (links == null) {
			if (other.links != null)
				return false;
		} else if (!links.equals(other.links))
			return false;
		return true;
	}
}
