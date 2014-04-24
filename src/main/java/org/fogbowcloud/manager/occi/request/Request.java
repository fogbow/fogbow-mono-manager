package org.fogbowcloud.manager.occi.request;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;

public class Request {

	private String id;
	private String authToken;
	private String instanceId;
	private String memberId;
	private RequestState state;
	private List<Category> categories;
	private Map<String, String> xOCCIAtt;
	private String user;
	
	public Request(String id, String authToken, String user, 
			List<Category> categories, Map<String, String> xOCCIAtt) {
		this.id = id;
		this.authToken = authToken;
		this.user = user;
		this.categories = categories;
		this.xOCCIAtt = xOCCIAtt;
		setState(RequestState.OPEN);
	}
	
	public List<Category> getCategories() {
		return categories;
	}

	public String getUser() {
		return user;
	}
	
	public String getInstanceId() {
		return instanceId;
	}

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	public RequestState getState() {
		return state;
	}

	public void setState(RequestState state) {
		this.state = state;
	}

	public String getId() {
		return id;
	}

	public String getAttValue(String attributeName){
		return xOCCIAtt.get(attributeName);
	}
	
	public String toHttpMessageFormat() {
		return "RequestId=" + id + "; State=" + state.getValue() + "; InstanceId= " + instanceId;
	}


	public String getAuthToken() {
		return this.authToken;
	}


	public Map<String, String> getxOCCIAtt() {
		return this.xOCCIAtt;
	}

	public String getMemberId() {
		return memberId;
	}

	public void setMemberId(String memberId) {
		this.memberId = memberId;
	}
}