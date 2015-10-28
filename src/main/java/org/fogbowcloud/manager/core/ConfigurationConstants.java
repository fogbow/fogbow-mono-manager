package org.fogbowcloud.manager.core;

public class ConfigurationConstants {

	// xmpp
	public static final String XMPP_JID_KEY = "xmpp_jid";
	public static final String XMPP_PASS_KEY = "xmpp_password";
	public static final String XMPP_HOST_KEY = "xmpp_host";
	public static final String XMPP_PORT_KEY = "xmpp_port";
	public static final String RENDEZVOUS_JID_KEY = "rendezvous_jid";	
	public static final String GREEN_SITTER_JID = "greensitter_jid";

	// class
	public static final String COMPUTE_CLASS_KEY = "compute_class";
	public static final String AUTHORIZATION_CLASS_KEY = "federation_authorization_class";
	public static final String IMAGE_STORAGE_PLUGIN_CLASS = "image_storage_class";
	public static final String BENCHMARKING_PLUGIN_CLASS_KEY = "benchmarking_class";
	public static final String ACCOUNTING_PLUGIN_CLASS_KEY = "accounting_class";
	public static final String MEMBER_PICKER_PLUGIN_CLASS_KEY = "member_picker_class";
	public static final String IDENTITY_CLASS_KEY = "identity_class";
	public static final String MEMBER_VALIDATOR_CLASS_KEY = "member_validator_class";
	public static final String LOCAL_CREDENTIALS_CLASS_KEY = "federation_user_credentail_class";

	// prefixs
	public static final String LOCAL_PREFIX = "local_";
	public static final String FEDERATION_PREFIX = "federation_";
	public static final String PREFIX_FLAVORS = "flavor_";

	// periods
	public static final String ORDER_BD_UPDATER_PERIOD_KEY = "order_bd_updater_period";
	public static final String SCHEDULER_PERIOD_KEY = "scheduler_period";
	public static final String INSTANCE_MONITORING_PERIOD_KEY = "instance_monitoring_period";
	public static final String TOKEN_UPDATE_PERIOD_KEY = "token_update_period";
	public static final String SERVED_REQUEST_MONITORING_PERIOD_KEY = "served_request_monitoring_period";
	public static final String GARBAGE_COLLECTOR_PERIOD_KEY = "garbage_collector_period";
	public static final String ACCOUNTING_UPDATE_PERIOD_KEY = "accounting_update_period";
	public static final String ASYNC_REQUEST_WAITING_INTERVAL_KEY = "async_request_waiting_interval";

	// token host
	public static final String TOKEN_HOST_PRIVATE_ADDRESS_KEY = "token_host_private_address";
	public static final String TOKEN_HOST_PUBLIC_ADDRESS_KEY = "token_host_public_address";
	public static final String TOKEN_HOST_PORT_KEY = "token_host_port";
	public static final String TOKEN_HOST_HTTP_PORT_KEY = "token_host_http_port";

	// ssh properties
	public static final String SSH_PUBLIC_KEY_PATH = "ssh_public_key";
	public static final String SSH_PRIVATE_KEY_PATH = "ssh_private_key";
	public static final String SSH_COMMON_USER = "ssh_common_user";

	public static final String HTTP_PORT_KEY = "http_port";
	
	public static final String PROP_MAX_WHOISALIVE_MANAGER_COUNT = "max_whoisalive_manager_count";
}
