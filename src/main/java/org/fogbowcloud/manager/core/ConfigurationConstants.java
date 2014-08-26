package org.fogbowcloud.manager.core;

public class ConfigurationConstants {

	//xmpp
	public static final String XMPP_JID_KEY = "xmpp_jid";
	public static final String XMPP_PASS_KEY = "xmpp_password";
	public static final String XMPP_HOST_KEY = "xmpp_host";
	public static final String XMPP_PORT_KEY = "xmpp_port";

	public static final String RENDEZVOUS_JID_KEY = "rendezvous_jid";

	public static final String LOCAL_PREFIX = "local_";
	public static final String FEDERATION_PREFIX = "federation_";
	public static final String COMPUTE_CLASS_KEY = "compute_class";
	public static final String AUTHORIZATION_CLASS_KEY = "federation_authorization_class";
	public static final String IDENTITY_CLASS_KEY = "identity_class";
	public static final String IDENTITY_URL = "identity_url";
	public static final String MEMBER_VALIDATOR_KEY = "member_validator";
	public static final String HTTP_PORT_KEY = "http_port";

	
	//schemes
	public static final String COMPUTE_OCCI_OS_SCHEME_KEY = "compute_occi_os_scheme";
	public static final String COMPUTE_OCCI_INSTANCE_SCHEME_KEY = "compute_occi_instance_scheme";
	public static final String COMPUTE_OCCI_RESOURCE_SCHEME_KEY = "compute_occi_resource_scheme";

	//images prefix
	public static final String COMPUTE_OCCI_IMAGE_PREFIX = "compute_occi_image_";
	
	//network
	public static final String COMPUTE_OCCI_NETWORK_KEY = "compute_occi_network_id";
	
	//flavors
	public static final String COMPUTE_OCCI_FLAVOR_SMALL_KEY = "compute_occi_flavor_small";
	public static final String COMPUTE_OCCI_FLAVOR_MEDIUM_KEY = "compute_occi_flavor_medium";
	public static final String COMPUTE_OCCI_FLAVOR_LARGE_KEY = "compute_occi_flavor_large";

	public static final String COMPUTE_OCCI_URL_KEY = "compute_occi_url";
	
	//federation user
	public static final String FEDERATION_USER_NAME_KEY = "local_proxy_account_user_name";
	public static final String FEDERATION_USER_PASS_KEY = "local_proxy_account_password";
	public static final String FEDERATION_USER_TENANT_NAME_KEY = "local_proxy_account_tenant_name";
	
	public static final String FEDERATION_USER_PASS_VOMS = "local_proxy_account_pass_voms";
	public static final String FEDERATION_USER_SERVER_VOMS = "local_proxy_account_server_voms";
	
	public static final String FEDERATION_USER_X509_CERTIFICATE_PATH_KEY = "local_proxy_account_x509_certificate_path";
		
	//periods
	public static final String SCHEDULER_PERIOD_KEY = "scheduler_period";
	public static final String INSTANCE_MONITORING_PERIOD_KEY = "instance_monitoring_period";
	public static final String TOKEN_UPDATE_PERIOD_KEY = "token_update_period";
	
	//ssh properties
	public static final String SSH_PRIVATE_HOST_KEY = "ssh_tunnel_private_host";
	public static final String SSH_PUBLIC_HOST_KEY = "ssh_tunnel_public_host";
	public static final String SSH_HOST_PORT_KEY = "ssh_tunnel_host_port";
	public static final String SSH_USER_KEY = "ssh_tunnel_user";
	public static final String SSH_PORT_RANGE_KEY = "ssh_tunnel_port_range";

	//voms
	public static final String VOMS_PATH_VOMSES = "path_vomses";
	public static final String VOMS_PATH_TRUST_ANCHORS = "path_trust_anchors";
	public static final String VOMS_PATH_VOMSDIR = "path_vomsdir";
	
	//x509
	public static final String X509_CERTIFICATE_PATH_KEY = "x509_certificate_path";
	public static final String X509_CA_DIR_PATH_KEY = "x509_ca_dir_path";	
}
