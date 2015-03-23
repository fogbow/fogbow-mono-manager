package org.fogbowcloud.manager.core.plugins.opennebula;

public class OneConfigurationConstants {

	public static final String COMPUTE_ONE_URL = "compute_one_url";
	
	// flavors
	public static final String COMPUTE_ONE_SMALL_KEY = "compute_one_flavor_small";
	public static final String COMPUTE_ONE_MEDIUM_KEY = "compute_one_flavor_medium";
	public static final String COMPUTE_ONE_LARGE_KEY = "compute_one_flavor_large";
	
	// images prefix
	public static final String COMPUTE_ONE_IMAGE_PREFIX_KEY = "compute_one_image_";
	
	// network	
	public static final String COMPUTE_ONE_NETWORK_KEY = "compute_one_network_id";

	//datastore and images configuration
	public static final String COMPUTE_ONE_DATASTORE_ID = "compute_one_datastore_id";
	public static final String COMPUTE_ONE_SSH_HOST = "compute_one_ssh_host";
	public static final String COMPUTE_ONE_SSH_PORT = "compute_one_ssh_port";
	public static final String COMPUTE_ONE_SSH_USERNAME = "compute_one_ssh_username";
	public static final String COMPUTE_ONE_SSH_KEY_FILE = "compute_one_ssh_key_file";
	public static final String COMPUTE_ONE_SSH_TARGET_TEMP_FOLDER = "computer_one_ssh_target_temp_folder";
	
	public static final String OPENNEBULA_TEMPLATES = "opennebula_templates";
	public static final String OPENNEBULA_TEMPLATES_TYPE_ALL = "all";	
	
	// occi
	public static final String PREFIX_OCCI_OPENNEBULA_FLAVORS_PROVIDED = "occi_opennebula_flavor_";
}
