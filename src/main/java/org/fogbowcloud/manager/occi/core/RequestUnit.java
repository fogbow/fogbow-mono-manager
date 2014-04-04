package org.fogbowcloud.manager.occi.core;

import java.util.Date;

import org.restlet.engine.adapter.HttpRequest;

public class RequestUnit {

	private String id;
	private String instanceId;
	private RequestState state;
	private Date validFromDate;
	private Date validUntil;
	private String type;
	private HttpRequest httpRequest;

	public RequestUnit(String id, String instanceId, RequestState state, Date validFrom,
			Date validUntil, String type, HttpRequest httpRequest) {
		this.id = id;
		setInstanceId(instanceId);
		setState(state);
		this.validFromDate = validFrom;
		this.validUntil = validUntil;
		this.type = type;
		this.httpRequest = httpRequest;
	}

	public String getInstanceId() {
		return instanceId;
	}

	public HttpRequest getHttpRequest() {
		return httpRequest;
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

	public Date getValidFromDate() {
		return validFromDate;
	}

	public Date getValidUntil() {
		return validUntil;
	}

	public String getType() {
		return type;
	}
}
