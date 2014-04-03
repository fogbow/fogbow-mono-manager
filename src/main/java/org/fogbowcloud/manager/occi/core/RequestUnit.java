package org.fogbowcloud.manager.occi.core;

public class RequestUnit {

	private String id;
	private String instanceId;
	private RequestState state;

	public RequestUnit(String id, String instanceId, RequestState state) {
		this.id = id;
		setInstanceId(instanceId);
		setState(state);
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

	public String toHttMessageFormat() {
		return "RequestId=" + id + "; State=" + state.getValue() + "; InstanceId= " + instanceId;
	}

}
