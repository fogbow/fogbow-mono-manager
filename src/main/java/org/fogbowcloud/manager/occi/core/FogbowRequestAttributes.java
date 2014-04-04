package org.fogbowcloud.manager.occi.core;

public enum FogbowRequestAttributes {
	ATRIBUTE_INSTANCE_FOGBOW_REQUEST("org.fogbowcloud.request.instance"), 
	ATRIBUTE_TYPE_FOGBOW_REQUEST("org.fogbowcloud.request.type"),
	ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST("org.fogbowcloud.request.valid-until"), 
	ATRIBUTE_VALID_FROM_FOGBOW_REQUEST("org.fogbowcloud.request.valid-from");
	
	private String value;
	
	private FogbowRequestAttributes(String value) {
		this.value = value;
	}
	
	public String getValue(){
		return this.value;
	}	
}
