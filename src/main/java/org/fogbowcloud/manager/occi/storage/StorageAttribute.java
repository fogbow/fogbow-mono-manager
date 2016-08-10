package org.fogbowcloud.manager.occi.storage;

import java.util.ArrayList;
import java.util.List;

public enum StorageAttribute {

	TARGET("occi.core.target"),
	SOURCE("occi.core.source"),
	DEVICE_ID("occi.storagelink.deviceid"),
	ATTACHMENT_ID("occi.storagelink.attachmentId"),
	PROVIDING_MEMBER_ID("occi.storagelink.provadingMemberId"),
	SIZE("occi.storage.size");
	
	private String value;
	
	private StorageAttribute(String value) {
		this.value = value;
	}
	
	public String getValue() {
		return this.value;
	}
	
	public static List<String> getValues() {	
		List<String> values = new ArrayList<String>();		
		StorageAttribute[] elements = values();
		for (StorageAttribute attribute : elements) {
			values.add(attribute.getValue());
		}
		return values;
	}
	
}
