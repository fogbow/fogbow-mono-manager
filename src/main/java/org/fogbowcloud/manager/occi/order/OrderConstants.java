package org.fogbowcloud.manager.occi.order;

public class OrderConstants {

	// order
	// to continue working in the other manager
	public static final String TERM = "fogbow_request";
	public static final String SCHEME = "http://schemas.fogbowcloud.org/request#";
	public static final String KIND_CLASS = "kind";
	public static final Integer DEFAULT_INSTANCE_COUNT = 1;
	public static final String DEFAULT_TYPE = OrderType.ONE_TIME.getValue();

	// storage link
	public static final String STORAGELINK_TERM = "storagelink";
	
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
	
	// OCCI constants
	public static final String COMPUTE_TERM = "compute";
	public static final String STORAGE_TERM = "storage";
	public static final String STORAGE_LINK_TERM = "link";
	public static final String INFRASTRUCTURE_OCCI_SCHEME = "http://schemas.ogf.org/occi/infrastructure#";
	public static final String RESOURCE_TPL_OCCI_SCHEME = "http://schemas.ogf.org/occi/infrastructure#resource_tpl";
	public static final String RESOURCE_OCCI_SCHEME = "http://schemas.ogf.org/occi/core#resource";
	public static final String OS_TPL_OCCI_SCHEME = "http://schemas.ogf.org/occi/infrastructure#os_tpl";
	public static final String RESOURCE_TPL_TERM = "resource_tpl";
	public static final String NETWORK_INTERFACE_TERM = "networkinterface";
	public static final String LINK_TERM = "link";
	public static final String OS_TPL_TERM = "os_tpl";
}
