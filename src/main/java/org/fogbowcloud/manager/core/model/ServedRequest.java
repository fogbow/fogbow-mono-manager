package org.fogbowcloud.manager.core.model;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;

public class ServedRequest {

	private final String instanceToken;
	private final String instanceId;
	private final String memberId; 
	private final List<Category> categories;
	private final Map<String, String> xOCCIAtt;
	private long creationTime;
			
	public ServedRequest(String instanceToken, String instanceId, String memberId, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		this.instanceToken = instanceToken;
		this.instanceId = instanceId;
		this.memberId = memberId;
		this.categories = categories;
		this.xOCCIAtt = xOCCIAtt;
		this.creationTime = System.currentTimeMillis();
	}
	
	public String getInstanceToken() {
		return instanceToken;
	}
	
	public String getMemberId() {
		return memberId;
	}
		
	public String getInstanceId() {
		return instanceId;
	}

	public long getCreationTime() {
		return creationTime;
	}

	public List<Category> getCategories() {
		return categories;
	}
	
	public Map<String, String> getxOCCIAtt() {
		return xOCCIAtt;
	}
}
