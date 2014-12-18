package org.fogbowcloud.manager.core.model;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;

public class RemoteRequest {

	private final String id;
	private final String memberId; 
	private final List<Category> categories;
	private final Map<String, String> xOCCIAtt;
	
	public RemoteRequest(String id, String memberId, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		this.id = id;
		this.memberId = memberId;
		this.categories = categories;
		this.xOCCIAtt = xOCCIAtt;
	}
	
	public String getId() {
		return id;
	}
	
	public String getMemberId() {
		return memberId;
	}
	
	public List<Category> getCategories() {
		return categories;
	}
	
	public Map<String, String> getxOCCIAtt() {
		return xOCCIAtt;
	}
}
