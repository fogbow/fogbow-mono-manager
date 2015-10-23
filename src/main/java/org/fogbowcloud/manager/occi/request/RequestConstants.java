package org.fogbowcloud.manager.occi.request;

public class RequestConstants {

	// request
	public static final String TERM = "fogbow_request";
	public static final String SCHEME = "http://schemas.fogbowcloud.org/request#";
	public static final String KIND_CLASS = "kind";
	public static final Integer DEFAULT_INSTANCE_COUNT = 1;
	public static final String DEFAULT_TYPE = RequestType.ONE_TIME.getValue();

	// size flavors
	public static final String TEMPLATE_RESOURCE_SCHEME = "http://schemas.fogbowcloud.org/template/resource#";
	public static final String SMALL_TERM = "fogbow_small";
	public static final String MEDIUM_TERM = "fogbow_medium";
	public static final String LARGE_TERM = "fogbow_large";

	// image flavors
	public static final String TEMPLATE_OS_SCHEME = "http://schemas.fogbowcloud.org/template/os#";

	// general
	public static final String MIXIN_CLASS = "mixin";
	
	// ssh
	public static final String USER_DATA_TERM = "fogbow_userdata";
	public static final String PUBLIC_KEY_TERM = "fogbow_public_key";
	public static final String CREDENTIALS_RESOURCE_SCHEME = "http://schemas.fogbowcloud/credentials#";	
	
	// compute endpoint
	public static final String COMPUTE_TERM = "compute";
}
