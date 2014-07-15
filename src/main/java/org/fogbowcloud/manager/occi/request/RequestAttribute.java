package org.fogbowcloud.manager.occi.request;

import java.util.ArrayList;
import java.util.List;

public enum RequestAttribute {
	
	OCCI_CORE_TITLE("occi.core.title"),
	OCCI_CORE_ID("occi.core.id"),
	INSTANCE_COUNT("org.fogbowcloud.request.instance-count"), 
	TYPE("org.fogbowcloud.request.type"),
	VALID_UNTIL("org.fogbowcloud.request.valid-until"), 
	VALID_FROM("org.fogbowcloud.request.valid-from"),
	STATE("org.fogbowcloud.request.state"),
	INSTANCE_ID("org.fogbowcloud.request.instance-id");
	
	private String value;
	
	private RequestAttribute(String value) {
		this.value = value;
	}
	
	public String getValue() {
		return this.value;
	}
	
	public static List<String> getValues() {	
		List<String> values = new ArrayList<String>();		
		RequestAttribute[] elements = values();
		for (RequestAttribute attribute : elements) {
			values.add(attribute.getValue());
		}
		return values;
	}
}