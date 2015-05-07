package org.fogbowcloud.manager.core.plugins.compute.openstack;

public class OpenStackConfigurationConstants {

	/*
	 * OCCI configuration constants
	 */
	// url
	public static final String COMPUTE_OCCI_URL_KEY = "compute_occi_url";

	// schemes
	public static final String COMPUTE_OCCI_TEMPLATE_SCHEME_KEY = "compute_occi_template_scheme"; 
	public static final String COMPUTE_OCCI_OS_SCHEME_KEY = "compute_occi_os_scheme";
	public static final String COMPUTE_OCCI_INSTANCE_SCHEME_KEY = "compute_occi_instance_scheme";
	public static final String COMPUTE_OCCI_RESOURCE_SCHEME_KEY = "compute_occi_resource_scheme";

	// images prefix
	public static final String COMPUTE_OCCI_IMAGE_PREFIX = "compute_occi_image_";	
	// template prefix
	public final static String COMPUTE_OCCI_TEMPLATE_PREFIX = "compute_occi_template_name_";

	// network
	public static final String COMPUTE_OCCI_NETWORK_KEY = "compute_occi_network_id";

	// flavors
	public static final String COMPUTE_OCCI_FLAVOR_SMALL_KEY = "compute_occi_flavor_small";
	public static final String COMPUTE_OCCI_FLAVOR_MEDIUM_KEY = "compute_occi_flavor_medium";
	public static final String COMPUTE_OCCI_FLAVOR_LARGE_KEY = "compute_occi_flavor_large";

	/*
	 * OpenStack V2 configuration constants
	 */

	// url
	public static final String COMPUTE_NOVAV2_URL_KEY = "compute_novav2_url";
	public static final String COMPUTE_GLANCEV2_URL_KEY = "compute_glancev2_url";	

	// images prefix
	public static final String COMPUTE_NOVAV2_IMAGE_PREFIX_KEY = "compute_novav2_image_";

	// network id
	public static final String COMPUTE_NOVAV2_NETWORK_KEY = "compute_novav2_network_id";

	// flavors
	public static final String COMPUTE_NOVAV2_FLAVOR_SMALL_KEY = "compute_novav2_flavor_small";
	public static final String COMPUTE_NOVAV2_FLAVOR_MEDIUM_KEY = "compute_novav2_flavor_medium";
	public static final String COMPUTE_NOVAV2_FLAVOR_LARGE_KEY = "compute_novav2_flavor_large";

	/*
	 * JSON Constants
	 */
	public static final String MAX_TOTAL_CORES_ATT = "maxTotalCores";
	public static final String TOTAL_CORES_USED_ATT = "totalCoresUsed";
	public static final String MAX_TOTAL_RAM_SIZE_ATT = "maxTotalRAMSize";
	public static final String TOTAL_RAM_USED_ATT = "totalRAMUsed";
	public static final String MAX_TOTAL_INSTANCES_ATT = "maxTotalInstances";
	public static final String TOTAL_INSTANCES_USED_ATT = "totalInstancesUsed";

}
