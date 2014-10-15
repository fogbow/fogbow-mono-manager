package org.fogbowcloud.manager.core.plugins.openstack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;

public class OpenStackNovaV2ComputePlugin implements ComputePlugin {

	private final String COMPUTE_V2_API_ENDPOINT = "/v2/";
	private static final String TENANT_ID = "tenantId";

	private String computeV2APIEndpoint;
	private String networkId;
	private Map<String, String> fogbowTermToOpenStack = new HashMap<String, String>();
	private Map<String, String> imagesOpenStackToFogbow = new HashMap<String, String>();
	DefaultHttpClient client;

	private static final Logger LOGGER = Logger.getLogger(OpenStackNovaV2ComputePlugin.class);
	
	public OpenStackNovaV2ComputePlugin(Properties properties){
		computeV2APIEndpoint = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_URL_KEY)
				+ COMPUTE_V2_API_ENDPOINT;

		networkId = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_NETWORK_KEY);

		// images
		Map<String, String> imageProperties = getImageProperties(properties);
		if (imageProperties == null || imageProperties.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IMAGES_NOT_SPECIFIED);
		}

		for (String imageName : imageProperties.keySet()) {
			fogbowTermToOpenStack.put(imageName, imageProperties.get(imageName));
			imagesOpenStackToFogbow.put(imageProperties.get(imageName), imageName);
			ResourceRepository.getInstance().addImageResource(imageName);
		}
		
		fogbowTermToOpenStack.put(RequestConstants.SMALL_TERM,
				properties.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_FLAVOR_SMALL_KEY));
		fogbowTermToOpenStack.put(RequestConstants.MEDIUM_TERM,
				properties.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_FLAVOR_MEDIUM_KEY));
		fogbowTermToOpenStack.put(RequestConstants.LARGE_TERM,
				properties.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_FLAVOR_LARGE_KEY));
		
		// userdata
		fogbowTermToOpenStack.put(RequestConstants.USER_DATA_TERM, "user_data");
		
		//ssh public key
		fogbowTermToOpenStack.put(RequestConstants.PUBLIC_KEY_TERM, "ssh-public-key");
		
		initClient();
	}
	
	private static Map<String, String> getImageProperties(Properties properties) {
		Map<String, String> imageProperties = new HashMap<String, String>();

		for (Object propName : properties.keySet()) {
			String propNameStr = (String) propName;
			if (propNameStr
					.startsWith(OpenStackConfigurationConstants.COMPUTE_NOVAV2_IMAGE_PREFIX_KEY)) {
				imageProperties
						.put(propNameStr
								.substring(OpenStackConfigurationConstants.COMPUTE_NOVAV2_IMAGE_PREFIX_KEY
										.length()), properties.getProperty(propNameStr));
			}
		}
		LOGGER.debug("Image properties: " + imageProperties);
		return imageProperties;
	}
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {

		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt);

		// removing fogbow-request category
		categories.remove(new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS));

		String flavorRef = null;
		String imageRef = null;
		
		for (Category category : categories) {
			if (fogbowTermToOpenStack.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			} else if (category.getTerm().equals(RequestConstants.SMALL_TERM)
					|| category.getTerm().equals(RequestConstants.MEDIUM_TERM)
					|| category.getTerm().equals(RequestConstants.LARGE_TERM)) {				
				// There are more than one flavor category
				if (flavorRef != null) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.IRREGULAR_SYNTAX);					
				}
				flavorRef = fogbowTermToOpenStack.get(category.getTerm());
			} else if (imagesOpenStackToFogbow.values().contains(category.getTerm())){
				// There are more than one image category
				if (imageRef != null) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.IRREGULAR_SYNTAX);					
				}
				imageRef = fogbowTermToOpenStack.get(category.getTerm());
			}
		}

		String publicKey = xOCCIAtt.get(RequestAttribute.DATA_PUBLIC_KEY.getValue());
		String keyName = getKeyname(token, publicKey);
				
		String userdata = xOCCIAtt.get(RequestAttribute.USER_DATA_ATT.getValue());		
		JSONObject json;
		try {
			json = generateJsonRequest(imageRef, flavorRef, userdata, keyName);
			String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
					+ "/servers";
			String jsonResponse = doPostRequest(requestEndpoint, token.getAccessId(), json);
			
			if (keyName != null) {
				deleteKeyName(token, keyName);
			}
			
			return getAttFromJson("id", jsonResponse);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
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
				keypair.put("name", keyname);
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
		client = new DefaultHttpClient();
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		client = new DefaultHttpClient(new ThreadSafeClientConnManager(params, client
				.getConnectionManager().getSchemeRegistry()), params);
	}
	
	private void checkStatusResponse(HttpResponse response, String message) {
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
			if (message.contains(ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
				throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
			}
			throw new OCCIException(ErrorType.BAD_REQUEST, message);
		} else if (response.getStatusLine().getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getStatusLine().toString());
		}
	}

	private JSONObject generateJsonRequest(String imageRef, String flavorRef, String userdata,
			String keyName) throws JSONException {

		JSONObject server = new JSONObject();
		server.put("name", "fogbow-instance-" + UUID.randomUUID().toString());
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
				+ "/servers";
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
				instances.add(new Instance(currentServer.getString("id")));
			}
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting instances from json.", e);
		}
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.info("Getting instance " + instanceId + " with token " + token);

		String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
				+ "/servers/" + instanceId;
		String jsonResponse = doGetRequest(requestEndpoint, token.getAccessId());
		LOGGER.debug("Getting instance from json: " + jsonResponse);
		return getInstanceFromJson(jsonResponse, token);
	}
	
	private Instance getInstanceFromJson(String json, Token token) {
		try {
			JSONObject rootServer = new JSONObject(json);
			String id = rootServer.getJSONObject("server").getString("id");

			Map<String, String> attributes = new HashMap<String, String>();
			// CPU Architecture of the instance
			attributes.put("occi.compute.state", getOCCIState(rootServer.getJSONObject("server")
					.getString("status")));
			// // CPU Clock frequency (speed) in gigahertz
			// TODO How to get speed?
			attributes.put("occi.compute.speed", "Not defined");
			// TODO How to get Arch?
			attributes.put("occi.compute.architecture", "Not defined"); 

			// getting info from flavor
			String flavorId = rootServer.getJSONObject("server").getJSONObject("flavor")
					.getString("id");
			String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
					+ "/flavors/" + flavorId;
			String jsonFlavor = doGetRequest(requestEndpoint, token.getAccessId());
			JSONObject rootFlavor = new JSONObject(jsonFlavor);
			double mem = Double.parseDouble(rootFlavor.getJSONObject("flavor").getString("ram"));
			attributes.put("occi.compute.memory", String.valueOf(mem / 1024)); // Gb
			attributes.put("occi.compute.cores",
					rootFlavor.getJSONObject("flavor").getString("vcpus"));

			attributes.put("occi.compute.hostname",
					rootServer.getJSONObject("server").getString("name"));
			attributes.put("occi.core.id", id);

			List<Resource> resources = new ArrayList<Resource>();
			resources.add(ResourceRepository.getInstance().get("compute"));
			resources.add(ResourceRepository.getInstance().get("os_tpl"));
			resources.add(ResourceRepository.getInstance().get(getUsedFlavor(flavorId)));

			String imageId = rootServer.getJSONObject("server").getJSONObject("image")
					.getString("id");

			LOGGER.debug("OpenStack imageId: " + imageId + " is related to fogbow image "
					+ imagesOpenStackToFogbow.get(imageId));

			// valid image
			if (imagesOpenStackToFogbow.get(imageId) != null) {
				resources.add(ResourceRepository.getInstance().get(
						imagesOpenStackToFogbow.get(imageId)));
			}
			LOGGER.debug("Instance resources: " + resources);

			return new Instance(id, resources, attributes, new ArrayList<Instance.Link>());
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting instances from json.", e);
		}
		return null;

	}

	private String getUsedFlavor(String flavorId) {
		if (fogbowTermToOpenStack.get(RequestConstants.SMALL_TERM).equals(flavorId)) {
			return RequestConstants.SMALL_TERM;
		} else if (fogbowTermToOpenStack.get(RequestConstants.MEDIUM_TERM).equals(flavorId)) {
			return RequestConstants.MEDIUM_TERM;
		} else if (fogbowTermToOpenStack.get(RequestConstants.LARGE_TERM).equals(flavorId)) {
			return RequestConstants.LARGE_TERM;
		}
		return null;
	}

	private String getOCCIState(String instanceStatus) {
		if ("suspended".equalsIgnoreCase(instanceStatus)){
			return "suspended";
		} else if ("active".equalsIgnoreCase(instanceStatus)){
			return "active";
		}
		return "inactive";
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

		int cpuIdle = Integer.parseInt(maxCpu) - Integer.parseInt(cpuInUse);
		int memIdle = Integer.parseInt(maxMem) - Integer.parseInt(memInUse);

		return new ResourcesInfo(String.valueOf(cpuIdle), cpuInUse, String.valueOf(memIdle),
				memInUse, getFlavors(cpuIdle, memIdle), null);
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

	private List<Flavor> getFlavors(int cpuIdle, int memIdle) {
		List<Flavor> flavors = new ArrayList<Flavor>();
		// flavors
		int capacity = Math.min(cpuIdle / 1, memIdle / 2048);
		Flavor smallFlavor = new Flavor(RequestConstants.SMALL_TERM, "1", "2048", capacity);
		capacity = Math.min(cpuIdle / 2, memIdle / 4096);
		Flavor mediumFlavor = new Flavor(RequestConstants.MEDIUM_TERM, "2", "4096", capacity);
		capacity = Math.min(cpuIdle / 4, memIdle / 8192);
		Flavor largeFlavor = new Flavor(RequestConstants.LARGE_TERM, "4", "8192", capacity);
		flavors.add(smallFlavor);
		flavors.add(mediumFlavor);
		flavors.add(largeFlavor);
		return flavors;
	}

	@Override
	public void bypass(Request request, Response response) {
		response.setStatus(new Status(HttpStatus.SC_BAD_REQUEST),
				ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
	}
	
	private String doGetRequest(String endpoint, String authToken) {
		HttpResponse response = null;
		String responseStr = null;
		try {
			HttpGet request = new HttpGet(endpoint);			
			request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				response.getEntity().consumeContent();
			} catch (Throwable t) {
				// Do nothing
			}
		}
		checkStatusResponse(response, responseStr);
		return responseStr;
	}
	
	private String doPostRequest(String endpoint, String authToken, JSONObject json) {
		HttpResponse response = null;
		String responseStr = null;
		try {
			HttpPost request = new HttpPost(endpoint);
			request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
			request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			request.setEntity(new StringEntity(json.toString(), HTTP.UTF_8));
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				response.getEntity().consumeContent();
			} catch (Throwable t) {
				// Do nothing
			}
		}
		checkStatusResponse(response, responseStr);
		return responseStr;
	}
	
	private void doDeleteRequest(String endpoint, String authToken) {
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
				response.getEntity().consumeContent();
			} catch (Throwable t) {
				// Do nothing
			}
		}
		// delete message does not have message
		checkStatusResponse(response, "");
	}
}
