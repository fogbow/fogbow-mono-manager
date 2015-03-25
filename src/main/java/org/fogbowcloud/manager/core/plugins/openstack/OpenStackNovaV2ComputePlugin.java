package org.fogbowcloud.manager.core.plugins.openstack;

import java.io.File;
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
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
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
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.util.HttpPatch;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;

public class OpenStackNovaV2ComputePlugin implements ComputePlugin {

	private static final String NO_VALID_HOST_WAS_FOUND = "No valid host was found";
	private static final String STATUS_JSON_FIELD = "status";
	private static final String IMAGES_JSON_FIELD = "images";
	private static final String ID_JSON_FIELD = "id";
	private static final String BARE = "bare";
	private static final String CONTAINER_FORMAT = "/container_format";
	private static final String VISIBILITY_JSON_FIELD = "visibility";
	private static final String PUBLIC = "public";
	private static final String NAME_JSON_FIELD = "name";
	private static final String DISK_FORMAT = "/disk_format";
	private static final String VALUE_JSON_FIELD = "value";
	private static final String PATH_JSON_FIELD = "path";
	private static final String REPLACE_VALUE_UPLOAD_IMAGE = "replace";
	private static final String OP_JSON_FIELD = "op";
	private static final String V2_IMAGES_FILE = "/file";
	private static final String V2_IMAGES = "/v2/images";

	private final String COMPUTE_V2_API_ENDPOINT = "/v2/";
	private static final String TENANT_ID = "tenantId";

	private String glanceV2APIEndpoint;
	private String computeV2APIEndpoint;
	private String networkId;
	private Map<String, String> fogbowTermToOpenStack = new HashMap<String, String>();
	private DefaultHttpClient client;

	private static final Logger LOGGER = Logger.getLogger(OpenStackNovaV2ComputePlugin.class);
	
	public OpenStackNovaV2ComputePlugin(Properties properties){
		glanceV2APIEndpoint = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_GLANCEV2_URL_KEY);
		
		computeV2APIEndpoint = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_URL_KEY)
				+ COMPUTE_V2_API_ENDPOINT;

		networkId = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_NOVAV2_NETWORK_KEY);

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
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageRef) {

		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt);

		if (imageRef == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		// removing fogbow-request category
		categories.remove(new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS));

		String flavorRef = null;
		
		for (Category category : categories) {
			String openstackRef = fogbowTermToOpenStack.get(category.getTerm());
			if (openstackRef == null && !category.getScheme().equals(
					RequestConstants.TEMPLATE_OS_SCHEME)) {
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
				flavorRef = openstackRef;
			}
		}

		String publicKey = xOCCIAtt.get(RequestAttribute.DATA_PUBLIC_KEY.getValue());
		String keyName = getKeyname(token, publicKey);
				
		String userdata = xOCCIAtt.get(RequestAttribute.USER_DATA_ATT.getValue());		
		try {
			JSONObject json = generateJsonRequest(imageRef, flavorRef, userdata, keyName);
			String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
					+ "/servers";
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
		client = new DefaultHttpClient();
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		client = new DefaultHttpClient(new ThreadSafeClientConnManager(params, client
				.getConnectionManager().getSchemeRegistry()), params);
	}
	
	public void setClient(DefaultHttpClient client) {
		this.client = client;
	}
	
	private void checkStatusResponse(HttpResponse response, String message) {
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
			throw new OCCIException(ErrorType.BAD_REQUEST, message);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_REQUEST_TOO_LONG) {
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
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getStatusLine().toString());
		}
	}

	private JSONObject generateJsonRequest(String imageRef, String flavorRef, String userdata,
			String keyName) throws JSONException {

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

			List<Resource> resources = new ArrayList<Resource>();
			resources.add(ResourceRepository.getInstance().get("compute"));
			resources.add(ResourceRepository.getInstance().get("os_tpl"));
			resources.add(ResourceRepository.getInstance().get(getUsedFlavor(flavorId)));

			LOGGER.debug("Instance resources: " + resources);

			return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), state);
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
	
	private InstanceState getInstanceState(String instanceStatus) {
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
				memInUse, getFlavors(cpuIdle, memIdle, instancesIdle));
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

	private List<Flavor> getFlavors(int cpuIdle, int memIdle, int instancesIdle) {
		List<Flavor> flavors = new ArrayList<Flavor>();
		
		// flavors
		int capacitySmall = Math.min(instancesIdle, Math.min(cpuIdle / 1, memIdle / 128));
		Flavor smallFlavor = new Flavor(RequestConstants.SMALL_TERM, "1", "128", capacitySmall);
		int capacityMedium = Math.min(instancesIdle, Math.min(cpuIdle / 2, memIdle / 512));
		Flavor mediumFlavor = new Flavor(RequestConstants.MEDIUM_TERM, "2", "512", capacityMedium);
		int capacityLarge = Math.min(instancesIdle, Math.min(cpuIdle / 4, memIdle / 1024));
		Flavor largeFlavor = new Flavor(RequestConstants.LARGE_TERM, "4", "1024", capacityLarge);
		
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
			json.put(VISIBILITY_JSON_FIELD, PUBLIC);
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

    private String doPatchRequest(String endpoint, String authToken, String json) {
        HttpResponse response = null;
        String responseStr = null;
        try {
            HttpPatch request = new HttpPatch(endpoint);
            request.addHeader(OCCIHeaders.CONTENT_TYPE, "application/openstack-images-v2.1-json-patch");
            request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
            request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
            request.setEntity(new StringEntity(json, HTTP.UTF_8));
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
    
    private String doPutRequest(String endpoint, String authToken, String path) {
        HttpResponse response = null;
        String responseStr = null;
        try {
            HttpPut request = new HttpPut(endpoint);
            request.addHeader(OCCIHeaders.CONTENT_TYPE, "application/octet-stream");
            request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);                   
            request.setEntity(new FileEntity(new File(path), "application/octet-stream"));
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
        checkStatusResponse(response, responseStr);
        return responseStr;
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
					}
					return ImageState.FAILED;
				}
			}
		} catch (JSONException e) {
			LOGGER.error("Error while parsing JSONObject for image state.", e);
		}
		return null;	
	}
}
