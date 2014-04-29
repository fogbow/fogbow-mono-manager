package org.fogbowcloud.manager.occi.request;

public class RequestConstants {

	// request
	public static final String TERM = "fogbow-request";
	public static final String SCHEME = "http://schemas.fogbowcloud.org/request#";
	public static final String CLASS = "kind";
	public static final Integer DEFAULT_INSTANCE_COUNT = 1;
	public static final String DEFAULT_TYPE = RequestType.ONE_TIME.getValue();

	// size flavors
	public static final String TEMPLATE_RESOURCE_SCHEME = "http://schemas.fogbowcloud.org/template/resource#";
	public static final String SMALL_TERM = "fogbow-small";
	public static final String MEDIUM_TERM = "fogbow-medium";
	public static final String LARGE_TERM = "fogbow-large";

	// image flavors
	public static final String TEMPLATE_OS_SCHEME = "http://schemas.fogbowcloud.org/template/os#";
	public static final String LINUX_X86_TERM = "fogbow-linux-x86";

	// general
	public static final String MIXIN_CLASS = "mixin";
	
	// ssh
	public static final String USER_DATA_TERM = "fogbow-userdata";
}