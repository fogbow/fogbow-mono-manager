package org.fogbowcloud.manager.core.plugins.compute.openstack;

import java.io.File;
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
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.util.HttpPatch;
import org.fogbowcloud.manager.core.util.HttpRequestUtil;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.storage.StorageAttribute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;

public class OpenStackNovaV2ComputePlugin implements ComputePlugin {

	protected static final int DEFAULT_HTTPCLIENT_TIMEOUT = 10000; // 10 seconds 
	
	private static final String OS_VOLUME_ATTACHMENTS = "/os-volume_attachments";
	private static final String SERVERS = "/servers";
	private static final String SUFFIX_ENDPOINT_FLAVORS = "/flavors";
	private static final String SUFFIX_ENDPOINT_NETWORKS = "/networks";
	private static final String NO_VALID_HOST_WAS_FOUND = "No valid host was found";
	private static final String STATUS_JSON_FIELD = "status";
	private static final String IMAGES_JSON_FIELD = "images";
	private static final String ID_JSON_FIELD = "id";
	private static final String BARE = "bare";
	private static final String CONTAINER_FORMAT = "/container_format";
	private static final String VISIBILITY_JSON_FIELD = "visibility";
	private static final String PRIVATE = "private";
	private static final String DISK_FORMAT = "/disk_format";
	private static final String VALUE_JSON_FIELD = "value";
	private static final String PATH_JSON_FIELD = "path";
	private static final String REPLACE_VALUE_UPLOAD_IMAGE = "replace";
	private static final String OP_JSON_FIELD = "op";
	protected static final String NAME_JSON_FIELD = "name";
	protected static final String V2_IMAGES_FILE = "/file";
	protected static final String V2_IMAGES = "/v2/images";
	private static final String NETWORK_V2_API_ENDPOINT = "/v2.0";

	private final String COMPUTE_V2_API_ENDPOINT = "/v2/";
	protected static final String TENANT_ID = "tenantId";

	private String glanceV2APIEndpoint;
	private String glanceV2ImageVisibility;
	private String computeV2APIEndpoint;
	private String networkV2APIEndpoint;
	private String networkId;
	private Map<String, String> fogbowTermToOpenStack = new HashMap<String, String>();
	private HttpClient client;
	private Integer httpClientTimeout;
	private List<Flavor> flavors;

	private static final Logger LOGGER = Logger.getLogger(OpenStackNovaV2ComputePlugin.class);
	
	public OpenStackNovaV2ComputePlugin(Properties properties){
		glanceV2APIEndpoint = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_GLANCEV2_URL_KEY);
		
		computeV2APIEndpoint = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_URL_KEY)
				+ COMPUTE_V2_API_ENDPOINT;

		networkId = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_NETWORK_KEY);
		
		glanceV2ImageVisibility = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_GLANCEV2_IMAGE_VISIBILITY, 
						PRIVATE);
		
		networkV2APIEndpoint = properties.getProperty(
				OpenStackConfigurationConstants.NETWORK_NOVAV2_URL_KEY) + NETWORK_V2_API_ENDPOINT;
		
		// userdata
		fogbowTermToOpenStack.put(OrderConstants.USER_DATA_TERM, "user_data");
		
		//ssh public key
		fogbowTermToOpenStack.put(OrderConstants.PUBLIC_KEY_TERM, "ssh-public-key");
		
		httpClientTimeout = DEFAULT_HTTPCLIENT_TIMEOUT;
		try {
			String timeoutStr = properties.getProperty(
					OpenStackConfigurationConstants.COMPUTE_HTTPCLIENT_TIMEOUT,
					String.valueOf(DEFAULT_HTTPCLIENT_TIMEOUT));
			httpClientTimeout = Integer.parseInt(timeoutStr);		
		} catch (Exception e) {}
		
		flavors = new ArrayList<Flavor>();	
		
		initClient();
	}
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageRef) {

		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt);

		if (imageRef == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}
		
		// removing fogbow-order category
		categories.remove(new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS));
		
		Flavor foundFlavor = getFlavor(token,
				xOCCIAtt.get(OrderAttribute.REQUIREMENTS.getValue()));
		String flavorId = null;
		if (foundFlavor != null) {
			flavorId = foundFlavor.getId();
		}
		
		// TODO Think about ! Is necessary ?
		for (Category category : categories) {
			if (category.getScheme().equals(OrderConstants.TEMPLATE_RESOURCE_SCHEME)) {
				continue;
			}		
			String openstackRef = fogbowTermToOpenStack.get(category.getTerm());
			if (openstackRef == null
					&& !category.getScheme().equals(OrderConstants.TEMPLATE_OS_SCHEME)
					&& !category.getScheme().equals(OrderConstants.TEMPLATE_RESOURCE_SCHEME)) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			}
		}		

		String publicKey = xOCCIAtt.get(OrderAttribute.DATA_PUBLIC_KEY.getValue());
		String keyName = getKeyname(token, publicKey);
		
		String userdata = xOCCIAtt.get(OrderAttribute.USER_DATA_ATT.getValue());
		
		String orderNetworkId = xOCCIAtt.get(OrderAttribute.NETWORK_ID.getValue());
		if (orderNetworkId == null || orderNetworkId.isEmpty()) {
			orderNetworkId = this.networkId;
		}
		try {
			JSONObject json = generateJsonRequest(imageRef, flavorId, userdata, keyName, orderNetworkId);
			String requestEndpoint = computeV2APIEndpoint + tenantId + SERVERS;
			String jsonResponse = doPostRequest(requestEndpoint, token.getAccessId(), json);
			return getAttFromJson(ID_JSON_FIELD, jsonResponse);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			if (keyName != null) {
				try {
					deleteKeyName(token, keyName);
				} catch (Throwable t) {
					LOGGER.warn("Could not delete key.", t);
				}
			}
		}
	}

	protected synchronized void updateFlavors(Token token) {
		try {
			String tenantId = token.getAttributes().get(TENANT_ID);
			if (tenantId == null) {
				return;
			}
			
			String endpoint = computeV2APIEndpoint + tenantId + SUFFIX_ENDPOINT_FLAVORS;
			String authToken = token.getAccessId();
			String jsonResponseFlavors = doGetRequest(endpoint, authToken);

			Map<String, String> nameToFlavorId = new HashMap<String, String>();

			JSONArray jsonArrayFlavors = new JSONObject(jsonResponseFlavors)
					.getJSONArray("flavors");
			for (int i = 0; i < jsonArrayFlavors.length(); i++) {
				JSONObject itemFlavor = jsonArrayFlavors.getJSONObject(i);
				nameToFlavorId.put(itemFlavor.getString("name"), itemFlavor.getString("id"));
			}

			List<Flavor> newFlavors = detailFlavors(endpoint, authToken, nameToFlavorId);
			if (newFlavors != null) {
				this.flavors.addAll(newFlavors);			
			}
			removeInvalidFlavors(nameToFlavorId);

		} catch (Exception e) {
			LOGGER.warn("Error while updating flavors.", e);
		}
	}

	private List<Flavor> detailFlavors(String endpoint, String authToken,
			Map<String, String> nameToIdFlavor) throws JSONException {
		List<Flavor> newFlavors = new ArrayList<Flavor>();
		List<Flavor> flavorsCopy = new ArrayList<Flavor>(flavors);
		for (String flavorName : nameToIdFlavor.keySet()) {
			boolean containsFlavor = false;
			for (Flavor flavor : flavorsCopy) {
				if (flavor.getName().equals(flavorName)) {
					containsFlavor = true;
					break;
				}
			}
			if (containsFlavor) {
				continue;
			}
			String newEndpoint = endpoint + "/" + nameToIdFlavor.get(flavorName);
			String jsonResponseSpecificFlavor = doGetRequest(newEndpoint, authToken);

			JSONObject specificFlavor = new JSONObject(jsonResponseSpecificFlavor)
					.getJSONObject("flavor");

			String id = specificFlavor.getString("id");
			String name = specificFlavor.getString("name");
			String disk = specificFlavor.getString("disk");
			String ram = specificFlavor.getString("ram");
			String vcpus = specificFlavor.getString("vcpus");

			newFlavors.add(new Flavor(name, id, vcpus, ram, disk));		
		}
		return newFlavors;
	}

	private void removeInvalidFlavors(Map<String, String> nameToIdFlavor) {
		ArrayList<Flavor> copyFlavors = new ArrayList<Flavor>(flavors);		
		for (Flavor flavor : copyFlavors) {
			boolean containsFlavor = false;
			for (String flavorName : nameToIdFlavor.keySet()) {
				if (flavorName.equals(flavor.getName())) {
					containsFlavor = true;
				}
			}
			if (!containsFlavor) {
				this.flavors.remove(flavor);
			}
		}
	}

	private void deleteKeyName(Token token, String keyName) {
		String keynameEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
				+ "/os-keypairs/" + keyName;
		doDeleteRequest(keynameEndpoint, token.getAccessId());
	}

	private String getKeyname(Token token, String publicKey) {
		String keyname = null;
		if (publicKey != null && !publicKey.isEmpty()) {
			String osKeypairsEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
					+ "/os-keypairs";

			keyname = UUID.randomUUID().toString();
			JSONObject keypair = new JSONObject();
			try {
				keypair.put(NAME_JSON_FIELD, keyname);
				keypair.put("public_key", publicKey);
				JSONObject root = new JSONObject();
				root.put("keypair", keypair);
				doPostRequest(osKeypairsEndpoint, token.getAccessId(), root);
			} catch (JSONException e) {
				LOGGER.error(e);
				throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
			}
		}
		return keyname;
	}

	private String getAttFromJson(String attName, String jsonStr) throws JSONException {
		JSONObject root = new JSONObject(jsonStr);
		return root.getJSONObject("server").getString(attName);
	}

	private void initClient() {
		this.client = HttpRequestUtil.createHttpClient(this.httpClientTimeout, null, null);
	}
	
	protected void setClient(HttpClient client) {
		this.client = client;
	}
	
	private void checkStatusResponse(HttpResponse response, String message) {
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
			throw new OCCIException(ErrorType.BAD_REQUEST, message);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_REQUEST_TOO_LONG 
				|| response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN) {
			if (message.contains(ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
				throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
			}
			throw new OCCIException(ErrorType.BAD_REQUEST, message);
		} else if ((response.getStatusLine().getStatusCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR) &&
				(message.contains(NO_VALID_HOST_WAS_FOUND))){
			throw new OCCIException(ErrorType.NO_VALID_HOST_FOUND, ResponseConstants.NO_VALID_HOST_FOUND);
		}
		else if (response.getStatusLine().getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Status code: " + response.getStatusLine().toString() + " | Message:" + message);
		}
	}

	private JSONObject generateJsonRequest(String imageRef, String flavorRef, String userdata,
			String keyName, String networkId) throws JSONException {

		JSONObject server = new JSONObject();
		server.put(NAME_JSON_FIELD, "fogbow-instance-" + UUID.randomUUID().toString());
		server.put("imageRef", imageRef);
		server.put("flavorRef", flavorRef);
		if (userdata != null) {
			server.put("user_data", userdata);
		}

		if (networkId != null && !networkId.isEmpty()) {
			ArrayList<JSONObject> nets = new ArrayList<JSONObject>();
			JSONObject net = new JSONObject();
			net.put("uuid", networkId);
			nets.add(net);
			server.put("networks", nets);
		}
		
		if (keyName != null && !keyName.isEmpty()){
			server.put("key_name", keyName);
		}

		JSONObject root = new JSONObject();
		root.put("server", server);
		return root;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
				+ SERVERS;
		String jsonResponse = doGetRequest(requestEndpoint, token.getAccessId());
		return getInstancesFromJson(jsonResponse);
	}
	
	private List<Instance> getInstancesFromJson(String json) {
		LOGGER.debug("Getting instances from json: " + json);
		List<Instance> instances = new ArrayList<Instance>();
		JSONObject root;
		try {
			root = new JSONObject(json);
			JSONArray servers = root.getJSONArray("servers");
			for (int i = 0; i < servers.length(); i++) {
				JSONObject currentServer = servers.getJSONObject(i);
				instances.add(new Instance(currentServer.getString(ID_JSON_FIELD)));
			}
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting instances from json.", e);
		}
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.info("Getting instance " + instanceId + " with token " + token);
		
		if (getFlavors() == null || getFlavors().isEmpty()) {
			updateFlavors(token);
		}

		String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
				+ "/servers/" + instanceId;
		String jsonResponse = doGetRequest(requestEndpoint, token.getAccessId());
		
		LOGGER.debug("Getting instance from json: " + jsonResponse);
		return getInstanceFromJson(jsonResponse, token);
	}
	
	private Instance getInstanceFromJson(String json, Token token) {
		try {
			JSONObject rootServer = new JSONObject(json);
			String id = rootServer.getJSONObject("server").getString(ID_JSON_FIELD);

			Map<String, String> attributes = new HashMap<String, String>();
			InstanceState state = getInstanceState(rootServer.getJSONObject("server")
					.getString(STATUS_JSON_FIELD));
			// CPU Architecture of the instance
			attributes.put("occi.compute.state", state.getOcciState());
			// // CPU Clock frequency (speed) in gigahertz
			// TODO How to get speed?
			attributes.put("occi.compute.speed", "Not defined");
			// TODO How to get Arch?
			attributes.put("occi.compute.architecture", "Not defined"); 

			// getting info from flavor
			String flavorId = rootServer.getJSONObject("server").getJSONObject("flavor")
					.getString(ID_JSON_FIELD);
			String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
					+ "/flavors/" + flavorId;
			String jsonFlavor = doGetRequest(requestEndpoint, token.getAccessId());
			JSONObject rootFlavor = new JSONObject(jsonFlavor);
			double mem = Double.parseDouble(rootFlavor.getJSONObject("flavor").getString("ram"));
			attributes.put("occi.compute.memory", String.valueOf(mem / 1024)); // Gb
			attributes.put("occi.compute.cores",
					rootFlavor.getJSONObject("flavor").getString("vcpus"));

			attributes.put("occi.compute.hostname",
					rootServer.getJSONObject("server").getString(NAME_JSON_FIELD));
			attributes.put("occi.core.id", id);
			
			// getting local private IP
			JSONArray addressesNamesArray = rootServer.getJSONObject("server").getJSONObject("addresses").names();
			String networkMac = "";
			String networkName = null;
			if (addressesNamesArray != null && addressesNamesArray.length() > 0) {
				networkName = rootServer.getJSONObject("server").getJSONObject("addresses").names().getString(0);
							
				JSONArray networkArray = rootServer.getJSONObject("server").getJSONObject("addresses").getJSONArray(networkName);
				if (networkArray != null) {
					for (int i = 0; i < networkArray.length(); i++) {
						JSONObject networkObject = networkArray.getJSONObject(i);
						String addr = networkObject.getString("addr");
						networkMac = networkObject.getString("OS-EXT-IPS-MAC:mac_addr");
						if (addr != null && !addr.isEmpty()) {
							attributes.put(Instance.LOCAL_IP_ADDRESS_ATT, addr);
							break;
						}
					}
				}
			}

			List<Resource> resources = new ArrayList<Resource>();
			resources.add(ResourceRepository.getInstance().get("compute"));
			resources.add(ResourceRepository.getInstance().get("os_tpl"));
			
			//TODO check this line
			resources.add(ResourceRepository.generateFlavorResource(getUsedFlavor(flavorId)));

			LOGGER.debug("Instance resources: " + resources);

			ArrayList<Link> links = new ArrayList<Instance.Link>();
			
			Link privateIpLink = new Link();
			privateIpLink.setType(OrderConstants.NETWORK_TERM);
			String serverNetworkId = getNetworkIdByName(token, networkName);
			privateIpLink.setId(serverNetworkId);
			privateIpLink.setName("</" + OrderConstants.NETWORK_TERM + "/" + serverNetworkId + ">");
			
			Map<String, String> linkAttributes = new HashMap<String, String>();
			linkAttributes.put("rel", OrderConstants.INFRASTRUCTURE_OCCI_SCHEME 
					+ OrderConstants.NETWORK_TERM);
			linkAttributes.put("category", OrderConstants.INFRASTRUCTURE_OCCI_SCHEME 
					+ OrderConstants.NETWORK_INTERFACE_TERM);
			linkAttributes.put(OCCIConstants.NETWORK_INTERFACE_INTERFACE, "eth0");
			linkAttributes.put(OCCIConstants.NETWORK_INTERFACE_MAC, networkMac);
			linkAttributes.put(OCCIConstants.NETWORK_INTERFACE_STATE, 
					OCCIConstants.NetworkState.ACTIVE.getValue());
			
			privateIpLink.setAttributes(linkAttributes);
			links.add(privateIpLink);
			
			return new Instance(id, resources, attributes, links, state);
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting instances from json.", e);
		}
		return null;
	}

	private String getNetworkIdByName(Token token, String networkName) {
		
		if (networkName == null) {
			return null;
		}
		
		String requestEndpoint = networkV2APIEndpoint 
				+ SUFFIX_ENDPOINT_NETWORKS;
		String json = doGetRequest(requestEndpoint, token.getAccessId());
		try {
			JSONObject rootNetworks = new JSONObject(json);
			JSONArray jsonArray = rootNetworks.getJSONArray("networks");
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject networkJSONObject = jsonArray.getJSONObject(i);
				if (networkJSONObject.getString("name")
						.equals(networkName)) {
					return networkJSONObject.getString("id");
				}
			}
		} catch (JSONException e) {
			LOGGER.error("Could not retrieve network details.", e);
		}
		return null;
	}

	protected String getUsedFlavor(String flavorId) {
		for (Flavor flavor : getFlavors()) {
			if (flavor.getId().equals(flavorId)) {
				return flavor.getName();
			}
		}
		return null;
		
	}
	
	protected InstanceState getInstanceState(String instanceStatus) {
		if ("active".equalsIgnoreCase(instanceStatus)) {
			return InstanceState.RUNNING;
		}
		if ("suspended".equalsIgnoreCase(instanceStatus)) {
			return InstanceState.SUSPENDED;
		}
		if ("error".equalsIgnoreCase(instanceStatus)) {
			return InstanceState.FAILED;
		}
		return InstanceState.PENDING;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
				+ "/servers/" + instanceId;
		doDeleteRequest(requestEndpoint, token.getAccessId());
	}

	@Override
	public void removeInstances(Token token) {
		List<Instance> allInstances = getInstances(token);
		for (Instance instance : allInstances) {
			removeInstance(token, instance.getId());
		}
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
				+ "/limits";

		String jsonResponse = doGetRequest(requestEndpoint, token.getAccessId());
		
		String maxCpu = getAttFromLimitsJson(OpenStackConfigurationConstants.MAX_TOTAL_CORES_ATT,
				jsonResponse);
		String cpuInUse = getAttFromLimitsJson(
				OpenStackConfigurationConstants.TOTAL_CORES_USED_ATT, jsonResponse);
		
		String maxMem = getAttFromLimitsJson(
				OpenStackConfigurationConstants.MAX_TOTAL_RAM_SIZE_ATT, jsonResponse);
		String memInUse = getAttFromLimitsJson(OpenStackConfigurationConstants.TOTAL_RAM_USED_ATT,
				jsonResponse);
		
		String maxInstances = getAttFromLimitsJson(
				OpenStackConfigurationConstants.MAX_TOTAL_INSTANCES_ATT, jsonResponse);
		String instancesInUse = getAttFromLimitsJson(
				OpenStackConfigurationConstants.TOTAL_INSTANCES_USED_ATT, jsonResponse);

		int cpuIdle = Integer.parseInt(maxCpu) - Integer.parseInt(cpuInUse);
		int memIdle = Integer.parseInt(maxMem) - Integer.parseInt(memInUse);
		int instancesIdle = Integer.parseInt(maxInstances) - Integer.parseInt(instancesInUse);

		return new ResourcesInfo(String.valueOf(cpuIdle), cpuInUse, String.valueOf(memIdle),
				memInUse, String.valueOf(instancesIdle), instancesInUse);
	}
	
	private String getAttFromLimitsJson(String attName, String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject("limits").getJSONObject("absolute").getString(attName)
					.toString();
		} catch (JSONException e) {
			return null;
		}
	}

	@Override
	public void bypass(Request request, Response response) {
		response.setStatus(new Status(HttpStatus.SC_BAD_REQUEST),
				ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
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
	
	protected void doDeleteRequest(String endpoint, String authToken) {
		HttpResponse response = null;
		try {
			HttpDelete request = new HttpDelete(endpoint);
			request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			response = client.execute(request);
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
		// delete message does not have message
		checkStatusResponse(response, "");
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName, String diskFormat) {
		LOGGER.info("Uploading image... ");
		LOGGER.info("Token=" + token.getAccessId() + "; imagePath=" + imagePath + "; imageName="
				+ imageName);
		
		if (imageName == null || imageName.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Image empty.");
		}
		
		JSONObject json = new JSONObject();		
		try {
			json.put(NAME_JSON_FIELD, imageName);
			json.put(VISIBILITY_JSON_FIELD, glanceV2ImageVisibility);
		} catch (JSONException e) {}

		String responseStrCreateImage = doPostRequest(glanceV2APIEndpoint + V2_IMAGES,
				token.getAccessId(), json);
		String id = null;
		try {
			JSONObject featuresImage = new JSONObject(responseStrCreateImage);
			id = featuresImage.getString(ID_JSON_FIELD);
		} catch (JSONException e) {}
		
		try {			
			ArrayList<JSONObject> nets = new ArrayList<JSONObject>();
			JSONObject replace_disck_format = new JSONObject();
			replace_disck_format.put(OP_JSON_FIELD, REPLACE_VALUE_UPLOAD_IMAGE);
			replace_disck_format.put(PATH_JSON_FIELD, DISK_FORMAT);
			replace_disck_format.put(VALUE_JSON_FIELD, diskFormat);
			nets.add(replace_disck_format);
			JSONObject replace_container_format = new JSONObject();
			replace_container_format.put(OP_JSON_FIELD, REPLACE_VALUE_UPLOAD_IMAGE);
			replace_container_format.put(PATH_JSON_FIELD, CONTAINER_FORMAT);
			replace_container_format.put(VALUE_JSON_FIELD, BARE);
			nets.add(replace_container_format);
			
			doPatchRequest(glanceV2APIEndpoint + V2_IMAGES + "/" + id, token.getAccessId(),
					nets.toString());
			
			doPutRequest(glanceV2APIEndpoint + V2_IMAGES + "/" + id + V2_IMAGES_FILE,
					token.getAccessId(), imagePath);			
		} catch (Exception e) {
			LOGGER.error("Error while registering image.", e);
			doDeleteRequest(glanceV2APIEndpoint + V2_IMAGES + "/" + id, token.getAccessId());
			throw new OCCIException(ErrorType.BAD_REQUEST, "Upload failed.");
		}
	}

	@Override
	public String getImageId(Token token, String imageName) {
		String responseJsonImages = doGetRequest(glanceV2APIEndpoint + V2_IMAGES,
				token.getAccessId());

		try {
			JSONArray arrayImages = new JSONObject(responseJsonImages)
					.getJSONArray(IMAGES_JSON_FIELD);
			for (int i = 0; i < arrayImages.length(); i++) {
				if (arrayImages.getJSONObject(i).getString(NAME_JSON_FIELD).equals(imageName)) {
					return arrayImages.getJSONObject(i).getString(ID_JSON_FIELD);
				}
			}
		} catch (JSONException e) {
			LOGGER.error("Error while parsing JSONObject for image state.", e);
		}

		return null;
	}

	protected String doPatchRequest(String endpoint, String authToken, String json) {
        HttpResponse response = null;
        String responseStr = null;
        try {
            HttpPatch request = new HttpPatch(endpoint);
            request.addHeader(OCCIHeaders.CONTENT_TYPE, "application/openstack-images-v2.1-json-patch");
            request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
            request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
            request.setEntity(new StringEntity(json, Charsets.UTF_8));
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
    
	protected String doPutRequest(String endpoint, String authToken, String path) {
        HttpResponse response = null;
        String responseStr = null;
        try {
            HttpPut request = new HttpPut(endpoint);
            request.addHeader(OCCIHeaders.CONTENT_TYPE, "application/octet-stream");
            request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);                   
            request.setEntity(new FileEntity(new File(path), ContentType.APPLICATION_OCTET_STREAM));
            response = client.execute(request);
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

	public List<Flavor> getFlavors() {
		return this.flavors;
	}
	
	public void setFlavors(List<Flavor> flavors) {
		this.flavors = flavors;
	}
	
	public Flavor getFlavor(Token token, String requirements) {
		updateFlavors(token);
		// Finding flavor
		return RequirementsHelper.findSmallestFlavor(getFlavors(), requirements);
	}
	
	protected int getHttpClientTimeout() {
		return httpClientTimeout;
	}
	
	@Override
	public ImageState getImageState(Token token, String imageName) {
		LOGGER.debug("Getting image status from image " + imageName + " with token " + token);
		String responseJsonImages = doGetRequest(glanceV2APIEndpoint + V2_IMAGES,
				token.getAccessId());
		try {
			JSONArray arrayImages = new JSONObject(responseJsonImages)
					.getJSONArray(IMAGES_JSON_FIELD);
			for (int i = 0; i < arrayImages.length(); i++) {
				if (arrayImages.getJSONObject(i).getString(NAME_JSON_FIELD).equals(imageName)) {
					/*
					 * Possible OpenStack image status described on 
					 * http://docs.openstack.org/developer/glance/statuses.html
					 */
					String imageStatus = arrayImages.getJSONObject(i).getString(STATUS_JSON_FIELD);
					if ("active".equalsIgnoreCase(imageStatus)) {
						return ImageState.ACTIVE;
					} else if ("queued".equalsIgnoreCase(imageStatus)
							|| "saving".equalsIgnoreCase(imageStatus)) {
						return ImageState.PENDING;
					}					return ImageState.FAILED;
				}
			}
		} catch (JSONException e) {
			LOGGER.error("Error while parsing JSONObject for image state.", e);
		}
		return null;	
	}

	@Override
	public String attach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}	
		
		String storageIdd = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		String mountpoint = xOCCIAtt.get(StorageAttribute.DEVICE_ID.getValue());
		
		JSONObject jsonRequest = null;
		try {			
			jsonRequest = generateJsonToAttach(storageIdd, mountpoint);
		} catch (JSONException e) {
			LOGGER.error("An error occurred when generating json.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}			
		
		String prefixEndpoint = this.computeV2APIEndpoint;
		String endpoint = prefixEndpoint + tenantId + SERVERS
				+ "/" +  instanceId + OS_VOLUME_ATTACHMENTS;
		String responseStr = doPostRequest(endpoint, token.getAccessId(), jsonRequest);
		
		return getAttAttachmentIdJson(responseStr);
	}
	
	private String getAttAttachmentIdJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject("volumeAttachment").getString("id").toString();
		} catch (JSONException e) {
			return null;
		}
	}

	protected JSONObject generateJsonToAttach(String volume, String mountpoint) throws JSONException {

		JSONObject osAttachContent = new JSONObject();
		osAttachContent.put("volumeId", volume);

		JSONObject osAttach = new JSONObject();
		osAttach.put("volumeAttachment", osAttachContent);
		
		return osAttach;
	}			
	
	@Override
	public void dettach(Token token, List<Category> categories, Map<String, String> xOCCIAtt) {
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}	
		
		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		String attachmentId = xOCCIAtt.get(StorageAttribute.ATTACHMENT_ID.getValue());		
		
		String endpoint = this.computeV2APIEndpoint + tenantId + SERVERS + 
				"/" + instanceId + OS_VOLUME_ATTACHMENTS + "/" + attachmentId;
		doDeleteRequest(endpoint, token.getAccessId());		
	}
}
