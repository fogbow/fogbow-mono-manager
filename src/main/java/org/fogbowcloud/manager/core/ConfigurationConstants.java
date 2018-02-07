package org.fogbowcloud.manager.core;

public class ConfigurationConstants {

	public static final String INFRA_CONF_FILE = "infrastructure_conf_file";  
	public static final String FEDERATION_CONF_FILE = "federation_conf_file";
	
	// xmpp
	public static final String XMPP_JID_KEY = "xmpp_jid";
	public static final String XMPP_TIMEOUT = "xmpp_timeout";
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
	public static final String COMPUTE_ACCOUNTING_PLUGIN_CLASS_KEY = "compute_accounting_class";
	public static final String STORAGE_ACCOUNTING_PLUGIN_CLASS_KEY = "storage_accounting_class";
	public static final String MEMBER_PICKER_PLUGIN_CLASS_KEY = "member_picker_class";
	public static final String IDENTITY_CLASS_KEY = "identity_class";
	public static final String MEMBER_VALIDATOR_CLASS_KEY = "member_validator_class";
	public static final String LOCAL_CREDENTIALS_CLASS_KEY = "federation_user_credentail_class";
	public static final String STORAGE_CLASS_KEY = "storage_class";
	public static final String CAPACITY_CONTROLLER_PLUGIN_CLASS = "capacity_controller_class";
	public static final String NETWORK_CLASS_KEY = "network_class";

	// prefixs
	public static final String LOCAL_PREFIX = "local_";
	public static final String FEDERATION_PREFIX = "federation_";
	public static final String PREFIX_FLAVORS = "flavor_";
	public static final String OCCI_EXTRA_RESOURCES_PREFIX = "occi_extra_resource_";

	// periods
	public static final String SCHEDULER_PERIOD_KEY = "scheduler_period";
	public static final String INSTANCE_MONITORING_PERIOD_KEY = "instance_monitoring_period";
	public static final String TOKEN_UPDATE_PERIOD_KEY = "token_update_period";
	public static final String SERVED_ORDER_MONITORING_PERIOD_KEY = "served_order_monitoring_period";
	public static final String GARBAGE_COLLECTOR_PERIOD_KEY = "garbage_collector_period";
	public static final String ACCOUNTING_UPDATE_PERIOD_KEY = "accounting_update_period";
	public static final String CAPACITY_CONTROLLER_UPDATE_PERIOD_KEY = "capacity_controller_update_period";
	public static final String ASYNC_ORDER_WAITING_INTERVAL_KEY = "async_request_waiting_interval";
	public static final String MAXIMUM_ORDER_ATTEMPTS_PROPERTIES = ManagerControllerHelper.MonitoringHelper.MAXIMUM_ORDER_ATTEMPTS_PROPERTIES;
	public static final String TIMEOUT_HTTP_REQUEST = "timeout_http_request";

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
	public static final String HTTPS_PORT_KEY = "https_port";
	public static final String HTTPS_ENABLED = "https_enabled";
	public static final String HTTPS_KEYSTORE_PATH = "https_keystore_path";
	public static final String HTTPS_KEYSTORE_PASSWORD = "https_keystore_password";
	public static final String HTTPS_KEY_PASSWORD = "https_key_password";
	public static final String HTTPS_KEYSTORE_TYPE = "https_keystore_type";
	
	public static final String HTTP_REQUEST_HEADER_SIZE_KEY = "http_request_header_size";
	public static final String HTTP_RESPONSE_HEADER_SIZE_KEY = "http_response_header_size";
	
	public static final String ADMIN_USERS = "admin_users";
	public static final String PROP_MAX_WHOISALIVE_MANAGER_COUNT = "max_whoisalive_manager_count";
	
	// OCCI extra resource
	public static final String OCCI_EXTRA_RESOURCES_KEY_PATH = "occi_extra_resource_file_path";
	
	// Instance Data Store
	public static final String INSTANCE_DATA_STORE_URL = "instance_datastore_url";
	
	// Storage Data Store
	public static final String STORAGE_DATA_STORE_URL = "storage_datastore_url";

	//Network Data Store
	public static final String NETWORK_DATA_STORE_URL = "network_datastore_url";

	//Federated Network Properties
	public static final String FEDERATED_NETWORK_AGENT_PERMISSION_FILE_PATH = "federated_network_agent_permission_file_path";
	public static final String FEDERATED_NETWORK_AGENT_USER = "federated_network_agent_user";
	public static final String FEDERATED_NETWORK_AGENT_PRIVATE_ADDRESS = "federated_network_agent_private_address";
	public static final String FEDERATED_NETWORK_AGENT_ADDRESS = "federated_network_agent_address";
	
}
