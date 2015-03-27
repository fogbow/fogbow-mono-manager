package org.fogbowcloud.manager.occi.instance;

public enum InstanceState {

	PENDING("inactive"), RUNNING("active"), SUSPENDED("suspended"), FAILED("inactive");
	
	private String occiState;
	private InstanceState(String occiState) {
		this.occiState = occiState;
	}
	
	public String getOcciState() {
		return occiState;
	}
	
	public static InstanceState fromOCCIState(String occiState) {
		if ("active".equals(occiState)) {
			return RUNNING;
		}
		if ("suspended".equals(occiState)) {
			return SUSPENDED;
		}
		return PENDING;
	}
	
}
