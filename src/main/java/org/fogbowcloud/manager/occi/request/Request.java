package org.fogbowcloud.manager.occi.request;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;

public class Request {

	private String id;
	private String instanceId;
	private RequestState state;
	private List<Category> categories;
	private Map<String, String> xOCCIAtt;
	
	public Request(String id, String instanceId, RequestState state,
			List<Category> categories, Map<String, String> xOCCIAtt) {
		this.id = id;
		setInstanceId(instanceId);
		setState(state);
		this.categories = categories;
		this.xOCCIAtt = xOCCIAtt;
	}
	
	public List<Category> getCategories() {
		return categories;
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
}
