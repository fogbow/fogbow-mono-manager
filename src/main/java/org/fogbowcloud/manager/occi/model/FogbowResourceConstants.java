package org.fogbowcloud.manager.occi.model;

import org.fogbowcloud.manager.occi.core.RequestType;

public class FogbowResourceConstants {

	//fogbow-request category
	public static final String TERM_FOGBOW_REQUEST = "fogbow-request";
	public static final String SCHEME_FOGBOW_REQUEST = "http://schemas.fogbowcloud.org/request#";
	public static final String CLASS_FOGBOW_REQUEST = "kind";
	public static final String ATRIBUTE_INSTANCE_FOGBOW_REQUEST = "org.fogbowcloud.request.instance";
	public static final String ATRIBUTE_TYPE_FOGBOW_REQUEST = "org.fogbowcloud.request.type";
	public static final String ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST = "org.fogbowcloud.request.valid-until";
	public static final String ATRIBUTE_VALID_FROM_FOGBOW_REQUEST = "org.fogbowcloud.request.valid-from";
	public static final int DEFAULT_VALUE_INSTANCES_FOGBOW_REQUEST = 1;
	public static final String DEFAULT_VALUE_TYPE_FOGBOW_REQUEST = RequestType.ONE_TIME.getValue();
	
}
