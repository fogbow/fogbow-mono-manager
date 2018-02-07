package org.fogbowcloud.manager.occi.order;

import java.util.ArrayList;
import java.util.List;

public enum OrderAttribute {
	
	INSTANCE_COUNT("org.fogbowcloud.order.instance-count"), 
	TYPE("org.fogbowcloud.order.type"),
	VALID_UNTIL("org.fogbowcloud.order.valid-until"), 
	VALID_FROM("org.fogbowcloud.order.valid-from"),
	STATE("org.fogbowcloud.order.state"),
	INSTANCE_ID("org.fogbowcloud.order.instance-id"),
	DATA_PUBLIC_KEY("org.fogbowcloud.credentials.publickey.data"),
	USER_DATA_ATT("org.fogbowcloud.order.user-data"),
	EXTRA_USER_DATA_ATT("org.fogbowcloud.order.extra-user-data"),
	EXTRA_USER_DATA_CONTENT_TYPE_ATT("org.fogbowcloud.order.extra-user-data-content-type"),
	REQUIREMENTS("org.fogbowcloud.order.requirements"),
	BATCH_ID("org.fogbowcloud.order.batch-id"),
	REQUESTING_MEMBER("org.fogbowcloud.order.requesting-member"),
	PROVIDING_MEMBER("org.fogbowcloud.order.providing-member"),
	RESOURCE_KIND("org.fogbowcloud.order.resource-kind"),
	FEDERATED_NETWORK_LABEL("org.fogbowcloud.order.federated-network-label"),
	FEDERATED_NETWORK_CIDR_NOTATION_TERM("org.fogbowcloud.order.federated-network-cidr-notation"),
	FEDERATED_NETWORK_MEMBERS_TERM("org.fogbowcloud.order.federated-network-members"),
	STORAGE_SIZE("org.fogbowcloud.order.storage-size"),
	NETWORK_ID("org.fogbowcloud.order.network-id");
	
	private String value;
	
	private OrderAttribute(String value) {
		this.value = value;
	}
	
	public String getValue() {
		return this.value;
	}
	
	public static List<String> getValues() {	
		List<String> values = new ArrayList<String>();		
		OrderAttribute[] elements = values();
		for (OrderAttribute attribute : elements) {
			values.add(attribute.getValue());
		}
		return values;
	}
}