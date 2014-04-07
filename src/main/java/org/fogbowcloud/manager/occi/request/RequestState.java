package org.fogbowcloud.manager.occi.request;

public enum RequestState {
	
	OPEN("open"), FAILED("failed"), FULLFIELD("fullfield"), CANCELED("canceled"), CLOSED("closed");
	
	private String value;
	private RequestState(String value) {
		this.value = value;
	}
	
	public String getValue(){
		return this.value;
	}
}
