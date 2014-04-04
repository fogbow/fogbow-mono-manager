package org.fogbowcloud.manager.occi.model;

import org.fogbowcloud.manager.occi.request.RequestType;

public class FogbowResourceConstants {

	//fogbow-request category
	public static final String TERM = "fogbow-request";
	public static final String SCHEME = "http://schemas.fogbowcloud.org/request#";
	public static final String CLASS = "kind";
	public static final int DEFAULT_INSTANCE_COUNT = 1;
	public static final String DEFAULT_TYPE = RequestType.ONE_TIME.getValue();	
}
