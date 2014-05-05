package org.fogbowcloud.manager.occi.core;

import java.util.Map;

public class Token {
	
	private Map<String, String> attributes;	
	
	public Token(Map<String, String> attributes) {
		this.attributes = attributes;
	}
	
	public String get(String attributeName) {
		try {
			return attributes.get(attributeName);	
		} catch (Exception e) {
			return null;
		}
		
	}
	
	public Map<String, String> getAttributes() {
		return attributes;
	}
}
