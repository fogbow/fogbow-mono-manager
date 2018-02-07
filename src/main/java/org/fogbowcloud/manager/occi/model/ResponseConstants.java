package org.fogbowcloud.manager.occi.model;

public class ResponseConstants {

	public static final String INVALID_USER_OR_PASSWORD = "Invalid user or password.";
	public static final String UNAUTHORIZED = "Authentication required.";
	public static final String UNAUTHORIZED_USER = "Unauthorized user.";
	public static final String NOT_FOUND = "Resource not found.";
	public static final String IRREGULAR_SYNTAX = "Irregular Syntax.";
	public static final String UNSUPPORTED_ATTRIBUTES = "There are unsupported attributes in the request.";
	public static final String STATIC_FLAVORS_NOT_SPECIFIED = "There is not a static flavor specified in configuration file.";
	public static final String OK = "Ok";
	public static final String CLOUD_NOT_SUPPORT_CATEGORY = "The cloud does not support category: ";
	public static final String COULD_NOT_CREATE_TUNNEL = "SSH tunnel could not be created.";
	public static final String INVALID_OS_TEMPLATE = "Please provide a valid OS Template.";
	public static final String QUOTA_EXCEEDED_FOR_INSTANCES = "Quota exceeded";
	public static final String INVALID_DATE_ATTRIBUTES = "Invalid valid from and valid until attributes.";
	public static final String IMAGES_NOT_SPECIFIED = "There is not a image specified in configuration file.";
	public static final String TEMPLATES_NOT_SPECIFIED = "There are not templates specified in configuration file.";
	public static final String METHOD_NOT_SUPPORTED = "Method not supported.";
	public static final String CLOUD_NOT_SUPPORT_OCCI_INTERFACE = "The cloud does not support OCCI interface or OCCI endpoint was not informed for fogbow manager.";
	public static final String NETWORK_NOT_SPECIFIED = "There is not a network specified in configuration file.";
	public static final String TEMPLATE_TYPE_INVALID = "Template type invalid.";
	public static final String FLAVORS_NOT_SPECIFIED = "There is not a flavor specified in configuration file.";
	public static final String INVALID_FLAVOR_SPECIFIED = "Invalid flavor specified.";
	public static final String INVALID_X509_CERTIFICATE_PATH = "Invalid x509 certificate file path.";
	public static final String INVALID_CA_DIR = "Invalid Certificate Authority directory path.";
	public static final String INVALID_PRIVATE_KEY_PATH = "Invalid private key path.";
	public static final String UNKNOWN_HOST = "Unknown host.";
	public static final String ACCEPT_NOT_ACCEPTABLE = "Accept not acceptable.";
	public static final String CATEGORY_IS_NOT_REGISTERED = "The following category is not registered.";
	public static final String NO_VALID_HOST_FOUND = "No valid host was found.";
	public static final String INVALID_TOKEN = "Token type is not valid for this cloud manager";
	public static final String INVALID_STATE = "Current state is not valid.";
	public static final String PROPERTY_NOT_SPECIFIED_FOR_EXTRA_OCCI_RESOURCE = "There is not any property specified at manager conf file for OCCI resource: ";
	public static final String FORBIDDEN = "You don't have necessary permissions for the resource.";
	public static final String NOT_FOUND_RESOURCE_KIND = "Resource kind not found.";
	public static final String NOT_FOUND_STORAGE_SIZE_ATTRIBUTE = "Storage size not found.";
	public static final String NOT_FOUND_STORAGE_LINK_ATTRIBUTE = "Target or source not found.";
	public static final String NOT_FOUND_INSTANCE = "Instance(compute/storage) not found..";
	public static final String INVALID_STORAGE_LINK_DIFERENT_LOCATION = "The same size is required in the post of storage link.";
	public static final String INVALID_INSTANCE_OWN = "The same own of instance is required.";
	public static final String XMPP_RESPONSE_NULL = "Without response.";
	public static final String EXISTING_ATTACHMENT = "Remove operation aborted. There are attachments in use.";
	public static final String STORAGE_NOT_READY = "Storage not Ready yet.";
	public static final String INTERNAL_ERROR = "Internal Server Error";
	public static final String NETWORK_ADDRESS_INVALID_VALUE = "Network address with invalid value";
	public static final String NETWORK_GATEWAY_INVALID_VALUE = "Network gateway with invalid value";
	public static final String NETWORK_ALLOCATION_INVALID_VALUE = "Network allocation with invalid value";
	public static final String ORDER_NOT_CREATED = "Order not created";
	
	public static final String INVALID_CIDR = "Invalid CIDR, null or more than one occurrence";
	public static final String INVALID_LABEL = "Invalid Label, null or more than one occurrence";
	public static final String INVALID_MEMBER = "Invalid Member, null";
	public static final String INVALID_FEDERATED_NETWORK_ID = "Invalid Federated Network ID, null or empty";
}
