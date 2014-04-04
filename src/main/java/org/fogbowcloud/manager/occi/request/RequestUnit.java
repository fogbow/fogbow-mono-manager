package org.fogbowcloud.manager.occi.request;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.FogbowResource;

public class RequestUnit {

	private String id;
	private String instanceId;
	private RequestState state;
	List<FogbowResource> requestResources;
	Map<String, String> xOCCIAtt;
	
	public RequestUnit(String id, String instanceId, RequestState state,
			List<FogbowResource> requestResources, Map<String, String> xOCCIAtt) {
		this.id = id;
		setInstanceId(instanceId);
		setState(state);
		this.requestResources = requestResources;
		this.xOCCIAtt = xOCCIAtt;
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
	
	public String toHttMessageFormat() {
		return "RequestId=" + id + "; State=" + state.getValue() + "; InstanceId= " + instanceId;
	}
}
