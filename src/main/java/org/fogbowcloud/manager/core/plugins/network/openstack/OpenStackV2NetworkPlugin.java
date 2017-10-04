package org.fogbowcloud.manager.core.plugins.network.openstack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.io.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenStackConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.storage.openstack.OpenStackV2StoragePlugin;
import org.fogbowcloud.manager.core.util.HttpRequestUtil;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenStackV2NetworkPlugin implements NetworkPlugin {	

	protected static final String STATUS_OPENSTACK_ACTIVE = "ACTIVE";

	private static final Logger LOGGER = Logger.getLogger(OpenStackV2StoragePlugin.class);
	private static final String MSG_LOG_ERROR_MANIPULATE_JSON = "An error occurred when manipulate json.";
	protected static final String MSG_LOG_THERE_IS_INSTANCE_ASSOCIATED = "There is instance associated to the network ";
	
	private static final String SUFIX_ENDPOINT_ADD_ROUTER_INTERFACE_ADD = "add_router_interface";
	private static final String SUFIX_ENDPOINT_REMOVE_ROUTER_INTERFACE_ADD = "remove_router_interface";
	protected static final String SUFFIX_ENDPOINT_NETWORK = "/networks";
	protected static final String SUFIX_ENDPOINT_SUBNET = "/subnets";
	protected static final String SUFIX_ENDPOINT_ROUTER = "/routers";
	protected static final String SUFIX_ENDPOINT_PORTS = "/ports";
	protected static final String V2_API_ENDPOINT = "/v2.0";

	protected static final String TENANT_ID = "tenantId";
	protected static final String KEY_PROVIDER_SEGMENTATION_ID = "provider:segmentation_id";
	protected static final String KEY_EXTERNAL_GATEWAY_INFO = "external_gateway_info";
	protected static final String KEY_DNS_NAMESERVERS = "dns_nameservers";
	protected static final String KEY_DEVICE_OWNER = "device_owner";
	protected static final String KEY_JSON_SUBNET_ID = "subnet_id";
	protected static final String KEY_ENABLE_DHCP = "enable_dhcp";
	protected static final String KEY_IP_VERSION = "ip_version";
	protected static final String KEY_GATEWAY_IP = "gateway_ip";
	protected static final String KEY_FIXES_IPS = "fixed_ips";
	protected static final String KEY_TENANT_ID = "tenant_id";
	protected static final String KEY_JSON_ROUTERS = "routers";
	protected static final String KEY_JSON_NETWORK = "network";
	protected static final String KEY_NETWORK_ID = "network_id";
	protected static final String KEY_JSON_SUBNET = "subnet";
	protected static final String KEY_SUBNETS = "subnets";
	protected static final String KEY_JSON_ROUTER = "router";
	protected static final String KEY_JSON_PORTS = "ports";
	protected static final String KEY_DEVICE_ID = "device_id";
	protected static final String KEY_STATUS = "status";
	protected static final String KEY_NAME = "name";
	protected static final String KEY_CIRD = "cidr";
	protected static final String KEY_ID = "id";
	
	protected static final String DEFAULT_IP_VERSION = "4";
	protected static final String DEFAULT_NETWORK_NAME = "network-fogbow";
	protected static final String DEFAULT_ROUTER_NAME = "router-fogbow";
	protected static final String DEFAULT_SUBNET_NAME = "subnet-fogbow";
	protected static final String[] DEFAULT_DNS_NAME_SERVERS = new String[] {"8.8.8.8",  "8.8.4.4"};
	protected static final String DEFAULT_NETWORK_ADDRESS = "192.168.0.1/24";
	protected static final String NETWORK_DHCP = "network:dhcp";
	protected static final String COMPUTE_NOVA = "compute:nova";
	protected static final String NETWORK_ROUTER = "network:ha_router_replicated_interface";

	private HttpClient client;
	private String networkV2APIEndpoint;
	private String externalNetworkId;
	private String[] dnsList;
		
	public OpenStackV2NetworkPlugin(Properties properties) {
		this.externalNetworkId = properties.getProperty(KEY_EXTERNAL_GATEWAY_INFO);
		this.networkV2APIEndpoint = properties.getProperty(
				OpenStackConfigurationConstants.NETWORK_NOVAV2_URL_KEY) + V2_API_ENDPOINT;
		
		setDNSList(properties);
		
		initClient();
	}

	private void setDNSList(Properties properties) {
		String dnsPropertie = properties.getProperty(KEY_DNS_NAMESERVERS);
		if (dnsPropertie != null) {
			this.dnsList = dnsPropertie.split(",");
		}
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {		
		JSONObject jsonRequest = null;			
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}						
		
		// Creating router
		try {
			jsonRequest = generateJsonEntityToCreateRouter();
		} catch (JSONException e) {
			LOGGER.error("An error occurred when generating json.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		String endpoint = this.networkV2APIEndpoint + SUFIX_ENDPOINT_ROUTER;
		String responseStr = doPostRequest(endpoint, token.getAccessId(), jsonRequest);
		String routerId = getRouterIdFromJson(responseStr);
		
		// Creating network
		try {
			jsonRequest = generateJsonEntityToCreateNetwork(tenantId);
		} catch (JSONException e) {
			LOGGER.error("An error occurred when generating json.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}		
		try {
			endpoint = this.networkV2APIEndpoint + SUFFIX_ENDPOINT_NETWORK;
			responseStr = doPostRequest(endpoint, token.getAccessId(), jsonRequest);			
		} catch (OCCIException e) {
			LOGGER.error("An error occurred when creating network.", e);
			removeRouter(token, routerId, false);
			throw e;
		}		
		String networkId = getNetworkIdFromJson(responseStr);
		
		// Creating subnet
		try {
			jsonRequest = generateJsonEntityToCreateSubnet(networkId, tenantId, xOCCIAtt);
		} catch (JSONException e) {
			LOGGER.error("An error occurred when generating json.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}				
		try {
			endpoint = this.networkV2APIEndpoint + SUFIX_ENDPOINT_SUBNET;
			responseStr = doPostRequest(endpoint, token.getAccessId(), jsonRequest);				
		} catch (OCCIException e) {
			LOGGER.error("An error occurred when creating subnet.", e);
			removeRouter(token, routerId, false);
			removeNetwork(token, networkId, false);			
			throw e;
		}
		String subnetId = getSubnetIdFromJson(responseStr);		
		try {
			jsonRequest = generateJsonEntitySubnetId(subnetId);
		} catch (JSONException e) {
			LOGGER.error("An error occurred when generating json.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}		
		
		// Adding router interface
		try {
			endpoint = this.networkV2APIEndpoint + SUFIX_ENDPOINT_ROUTER + 
					"/" + routerId + "/" + SUFIX_ENDPOINT_ADD_ROUTER_INTERFACE_ADD;
			doPutRequest(endpoint, token.getAccessId(), jsonRequest);			
		} catch (OCCIException e) {
			removeRouter(token, routerId, false);
			removeNetwork(token, networkId, false);			
			throw e;
		}
		
		return networkId;
	}

	protected void removeNetwork(Token token, String networkId, boolean throwException) {
		String endpoint;
		try {
			endpoint = this.networkV2APIEndpoint + SUFFIX_ENDPOINT_NETWORK + "/" + networkId;
			doDeleteRequest(endpoint, token.getAccessId());										
		} catch (OCCIException e) {
			LOGGER.error("Could not possible remove network.", e);
			if (throwException) 
				throw e;
		}
	}

	protected void removeRouter(Token token, String routerId, boolean throwException) {
		String endpoint;
		try {
			endpoint = this.networkV2APIEndpoint + SUFIX_ENDPOINT_ROUTER + "/" + routerId;
			doDeleteRequest(endpoint, token.getAccessId());				
		} catch (OCCIException e) {
			LOGGER.error("Could not possible remove router.", e);
			if (throwException) 
				throw e;					
		}
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		String endpoint = this.networkV2APIEndpoint + SUFFIX_ENDPOINT_NETWORK + "/" + instanceId ;
		String responseStr = doGetRequest(endpoint, token.getAccessId());				
		return getInstanceFromJson(responseStr, token);
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		String routerIdToRemove = null;
		String networkIdToRemove = instanceId;
		
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}		
		String endpoint = this.networkV2APIEndpoint + SUFIX_ENDPOINT_PORTS
				+ "?" + KEY_TENANT_ID + "=" + tenantId;
		String responseStr = doGetRequest(endpoint, token.getAccessId());
		
		try {
			List<String> subnets = new ArrayList<String>();
			JSONObject rootServer = new JSONObject(responseStr);
			JSONArray routerPortsJSONArray = rootServer.optJSONArray(KEY_JSON_PORTS);
			for (int i = 0; i < routerPortsJSONArray.length(); i++) {
				
				String networkId = routerPortsJSONArray.optJSONObject(i).optString(KEY_NETWORK_ID);
				
				if (networkId.equals(instanceId)) {

					String deviceOwner = routerPortsJSONArray.optJSONObject(i).optString(KEY_DEVICE_OWNER);				

					boolean thereIsInstance = deviceOwner.equals(COMPUTE_NOVA);
					if (thereIsInstance) {
						throw new OCCIException(ErrorType.BAD_REQUEST, 
								MSG_LOG_THERE_IS_INSTANCE_ASSOCIATED + "( " + instanceId + " ).");
					}
					
					if(NETWORK_ROUTER.equals(deviceOwner)){
						routerIdToRemove = routerPortsJSONArray.optJSONObject(i).optString(KEY_DEVICE_ID);
					}
					
					if (!deviceOwner.equals(NETWORK_DHCP)) {
						String subnetId = routerPortsJSONArray.optJSONObject(i).optJSONArray(KEY_FIXES_IPS)
								.optJSONObject(0).optString(KEY_JSON_SUBNET_ID);
						subnets.add(subnetId);						
					}
					
				}				
			}			
			
			for (String subnetId : subnets) {
				JSONObject jsonRequest = generateJsonEntitySubnetId(subnetId);		
				if(routerIdToRemove != null){
					endpoint = this.networkV2APIEndpoint + SUFIX_ENDPOINT_ROUTER + "/" 
							+ routerIdToRemove + "/" + SUFIX_ENDPOINT_REMOVE_ROUTER_INTERFACE_ADD;
					doPutRequest(endpoint, token.getAccessId(), jsonRequest);
				}
								
			}
		} catch (JSONException e) {
			LOGGER.error(MSG_LOG_ERROR_MANIPULATE_JSON, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} catch (NullPointerException e) {
			LOGGER.error(MSG_LOG_ERROR_MANIPULATE_JSON, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} catch (OCCIException e) {
			LOGGER.error("An error occurred when removing router interface.", e);
			throw e;
		}
		if(routerIdToRemove != null){
			removeRouter(token, routerIdToRemove, false);	
		}
		removeNetwork(token, networkIdToRemove, true);
	}

	protected Instance getInstanceFromJson(String json, Token token) {
		try {
			JSONObject rootServer = new JSONObject(json);
			JSONObject networkJSONObject = rootServer.optJSONObject(KEY_JSON_NETWORK);
			String id = networkJSONObject.optString(KEY_ID);
			
			List<Resource> resources = new ArrayList<Resource>();
			resources.add(ResourceRepository.getInstance().get(OrderConstants.NETWORK_TERM));
			
			Map<String, String> attributes = new HashMap<String, String>();
			String vlan = networkJSONObject.optString(KEY_PROVIDER_SEGMENTATION_ID);
			if (vlan != null && !vlan.isEmpty()) {
				attributes.put(OCCIConstants.NETWORK_VLAN, vlan);				
			}
			attributes.put(OCCIConstants.NETWORK_STATE, networkJSONObject.optString(KEY_STATUS).equals(STATUS_OPENSTACK_ACTIVE) ? 
					OCCIConstants.NetworkState.ACTIVE.getValue() : OCCIConstants.NetworkState.INACTIVE.getValue());
			attributes.put(OCCIConstants.TITLE, networkJSONObject.optString(KEY_NAME));
			attributes.put(OCCIConstants.ID, id);
			
			String subnetId = networkJSONObject.optJSONArray(KEY_SUBNETS).optString(0);
			
			attributes = addSubnetInformation(subnetId, attributes, token);
			
			return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), null);
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting instance network from json.", e);
			return null;
		}
	}

	private Map<String, String> addSubnetInformation(String subnetId,
			Map<String, String> attributes, Token token) {
		String endpoint = this.networkV2APIEndpoint + SUFIX_ENDPOINT_SUBNET + "/" + subnetId ;
		String responseStr = doGetRequest(endpoint, token.getAccessId());	
		
		try {
			JSONObject rootServer = new JSONObject(responseStr);
			JSONObject subnetJSONObject = rootServer.optJSONObject(KEY_JSON_SUBNET);
			
			attributes.put(OCCIConstants.NETWORK_GATEWAY, subnetJSONObject.optString(KEY_GATEWAY_IP));
			boolean enableDHCP = subnetJSONObject.optBoolean(KEY_ENABLE_DHCP);
			if (enableDHCP) {
				attributes.put(OCCIConstants.NETWORK_ALLOCATION, OCCIConstants.NetworkAllocation.DYNAMIC.getValue());				
			} else {
				attributes.put(OCCIConstants.NETWORK_ALLOCATION, OCCIConstants.NetworkAllocation.STATIC.getValue());
			}
			attributes.put(OCCIConstants.NETWORK_ADDRESS, subnetJSONObject.optString(KEY_CIRD));			
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting subnet information from json.", e);
		}
		
		return attributes;
	}		
	
	protected String[] getRoutersFromJson(String json) {
		try {
			JSONObject rootServer = new JSONObject(json);
			JSONArray routersJSONArray = rootServer.optJSONArray(KEY_JSON_ROUTERS);
			String[] routerIds = new String[routersJSONArray.length()];
			for (int i = 0; i < routersJSONArray.length(); i++) {
				routerIds[i] = routersJSONArray.optJSONObject(i).optString(KEY_ID);
			}
			return routerIds;
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting routers from json.", e);
		}
		return null;
	}		
	
	protected String getNetworkIdFromJson(String json) {
		try {
			JSONObject rootServer = new JSONObject(json);
			JSONObject networkJSONObject = rootServer.optJSONObject(KEY_JSON_NETWORK);
			return networkJSONObject.optString(KEY_ID);
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting network id from json.", e);
		}
		return null;
	}
	
	protected String getSubnetIdFromJson(String json) {
		try {
			JSONObject rootServer = new JSONObject(json);
			JSONObject networkJSONObject = rootServer.optJSONObject(KEY_JSON_SUBNET);
			return networkJSONObject.optString(KEY_ID);
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting subnet id from json.", e);
		}
		return null;
	}	
	
	protected String getRouterIdFromJson(String json) {
		try {
			JSONObject rootServer = new JSONObject(json);
			JSONObject networkJSONObject = rootServer.optJSONObject(KEY_JSON_ROUTER);
			return networkJSONObject.optString(KEY_ID);
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting router id from json.", e);
		}
		return null;
	}			

	protected JSONObject generateJsonEntityToCreateRouter() throws JSONException {		
		JSONObject networkId = new JSONObject();
		networkId.put(KEY_NETWORK_ID, this.externalNetworkId);
		
		JSONObject routerContent = new JSONObject();
		routerContent.put(KEY_EXTERNAL_GATEWAY_INFO, networkId);
		routerContent.put(KEY_NAME, DEFAULT_ROUTER_NAME + "-" + UUID.randomUUID());
		
		JSONObject router = new JSONObject();
		router.put(KEY_JSON_ROUTER, routerContent);
		
		return router;
	}		
	
	protected JSONObject generateJsonEntityToCreateNetwork(String tenantId) throws JSONException {		
		JSONObject networkContent = new JSONObject();
		networkContent.put(KEY_NAME, DEFAULT_NETWORK_NAME + "-" + UUID.randomUUID());
		networkContent.put(KEY_TENANT_ID, tenantId);

		JSONObject network = new JSONObject();
		network.put(KEY_JSON_NETWORK, networkContent);
		
		return network;
	}	
	
	protected JSONObject generateJsonEntitySubnetId(String subnetId) throws JSONException {		
		JSONObject subnet = new JSONObject();
		subnet.put(KEY_JSON_SUBNET_ID, subnetId);
		
		return subnet;
	}		
	
	protected JSONObject generateJsonEntityToCreateSubnet(String networkId, String tenantId, Map<String, String> xOCCIAtt) throws JSONException {
		JSONObject subnetContent = new JSONObject();
		subnetContent.put(KEY_NAME, DEFAULT_SUBNET_NAME + "-" + UUID.randomUUID());
		subnetContent.put(KEY_TENANT_ID, tenantId);
		subnetContent.put(KEY_NETWORK_ID, networkId);
		subnetContent.put(KEY_IP_VERSION, DEFAULT_IP_VERSION);
		String gateway = xOCCIAtt.get(OCCIConstants.NETWORK_GATEWAY);
		if (gateway != null && !gateway.isEmpty()) {
			subnetContent.put(KEY_GATEWAY_IP, gateway);			
		}
		String networkAddress = xOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS);
		subnetContent.put(KEY_CIRD, networkAddress != null ? 
				networkAddress : DEFAULT_NETWORK_ADDRESS);
		String networkAllocation = xOCCIAtt.get(OCCIConstants.NETWORK_ALLOCATION);
		if (networkAllocation != null && !networkAllocation.isEmpty()) {
			if (networkAllocation.equals(OCCIConstants.NetworkAllocation.DYNAMIC.getValue())) {
				subnetContent.put(KEY_ENABLE_DHCP, true);				
			} else if (networkAllocation.equals(OCCIConstants.NetworkAllocation.STATIC.getValue())) {
				subnetContent.put(KEY_ENABLE_DHCP, false);
			}
		}
		JSONArray dnsNameServersArray = new JSONArray();
		String[] dnsNamesServers = dnsList != null ? dnsList: DEFAULT_DNS_NAME_SERVERS;
		for (int i = 0; i < dnsNamesServers.length; i++) {
			dnsNameServersArray.put(dnsNamesServers[i]);
		}		
		subnetContent.put(KEY_DNS_NAMESERVERS, dnsNameServersArray);
		
		JSONObject subnet = new JSONObject();
		subnet.put(KEY_JSON_SUBNET, subnetContent);
		
		return subnet;
	}		
	
	protected String doPostRequest(String endpoint, String authToken, JSONObject json) {
		HttpResponse response = null;
		String responseStr = null;
		try {
			HttpPost request = new HttpPost(endpoint);
			request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
			request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			request.setEntity(new StringEntity(json.toString(), Charsets.UTF_8));
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				EntityUtils.consume(response.getEntity());
			} catch (Throwable t) {
				// Do nothing
			}
		}
		checkStatusResponse(response, responseStr);
		return responseStr;
	}	
	
	protected String doGetRequest(String endpoint, String authToken) {
		HttpResponse response = null;
		String responseStr = null;
		try {
			HttpGet request = new HttpGet(endpoint);			
			request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error("Could not make GET request.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				EntityUtils.consume(response.getEntity());
			} catch (Throwable t) {
				// Do nothing
			}
		}
		checkStatusResponse(response, responseStr);
		return responseStr;
	}	
	
	protected String doPutRequest(String endpoint, String authToken, JSONObject json) {
        HttpResponse response = null;
        String responseStr = null;
        try {
            HttpPut request = new HttpPut(endpoint);
            request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
            request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);              
            if (json != null) {
            	request.setEntity(new StringEntity(json.toString(), Charsets.UTF_8));            	
            }
            response = client.execute(request);
            responseStr = EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error(e);
            throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
        } finally {
            try {
            	EntityUtils.consume(response.getEntity());
            } catch (Throwable t) {
                // Do nothing
            }
        }
        checkStatusResponse(response, responseStr);
        return responseStr;
    }	
	
	protected void doDeleteRequest(String endpoint, String authToken) {
		HttpResponse response = null;
		String responseStr = null;
		try {
			HttpDelete request = new HttpDelete(endpoint);
			request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			response = client.execute(request);
			if (response.getStatusLine().getStatusCode() != HttpStatus.SC_NO_CONTENT) {
				responseStr = EntityUtils.toString(response.getEntity(), Charsets.UTF_8);			
			}
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				EntityUtils.consume(response.getEntity());
			} catch (Throwable t) {
				// Do nothing
			}
		}
		checkStatusResponse(response, responseStr);
	}		
	
	private void checkStatusResponse(HttpResponse response, String message) {
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
			throw new OCCIException(ErrorType.BAD_REQUEST, message);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_REQUEST_TOO_LONG) {
			if (message != null && message.contains(ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
				throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
			}
			throw new OCCIException(ErrorType.BAD_REQUEST, message);
		}
		else if (response.getStatusLine().getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Status code: " + response.getStatusLine().toString() + " | Message:" + message);
		}
	}		
	
	private void initClient() {
		this.client = HttpRequestUtil.createHttpClient();
	}		
	
	// only for test
	protected void setClient(HttpClient client) {
		this.client = client;
	}
	
	protected String[] getDnsList() {
		return dnsList;
	}	
	
}