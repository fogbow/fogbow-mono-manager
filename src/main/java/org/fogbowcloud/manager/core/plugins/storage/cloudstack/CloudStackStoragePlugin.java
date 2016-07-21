package org.fogbowcloud.manager.core.plugins.storage.cloudstack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.plugins.common.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CloudStackStoragePlugin implements StoragePlugin {
	private static final Logger LOGGER = Logger.getLogger(CloudStackStoragePlugin.class);
	
	protected static final String LIST_DISK_OFFERINGS_COMMAND = "listDiskOfferings";
	protected static final String LIST_VOLUMES_COMMAND = "listVolumes";
	protected static final String CREATE_VOLUME_COMMAND = "createVolume";
	protected static final String DELETE_VOLUME_COMMAND = "deleteVolume";
	
	protected static final String COMMAND = "command";
	protected static final String VOLUME_ID = "id";
	protected static final String VOLUME_SIZE = "size";
	protected static final String VOLUME_NAME = "name";
	protected static final String DISK_OFFERING_ID = "diskofferingid";
	protected static final String ZONE_ID = "zoneid";
	
	private Properties properties;
	private HttpClientWrapper httpClient;
	private String endpoint;
	private String zoneId;

	public CloudStackStoragePlugin(Properties properties) {
		this(properties, new HttpClientWrapper());
	}
	
	public CloudStackStoragePlugin(Properties properties, HttpClientWrapper httpClient) {
		this.properties = properties;
		this.httpClient = httpClient;
		this.endpoint = this.properties.getProperty("compute_cloudstack_api_url");
		this.zoneId = this.properties.getProperty("compute_cloudstack_zone_id");
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		LOGGER.debug("Requesting storage instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt);
		
		if (zoneId == null) {
			LOGGER.error("Default zone id must be specified.");
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		URIBuilder uriBuilder = createURIBuilder(endpoint, CREATE_VOLUME_COMMAND);
		uriBuilder.addParameter(ZONE_ID, zoneId);
		
		String volumeName = "fogbow_volume_" + UUID.randomUUID();
		uriBuilder.addParameter(VOLUME_NAME, volumeName);
		
		getDiskOffering(token,
				xOCCIAtt.get(OrderAttribute.STORAGE_SIZE.getValue()), uriBuilder);
		
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doPost(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		try {
			JSONObject volume = new JSONObject(response.getContent()).optJSONObject(
					"createvolumeresponse");
			return volume.optString("id");
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	private void getDiskOffering(Token token, String volumeSize, URIBuilder createVolumeUriBuilder) {
		LOGGER.debug("Getting disk offerings available in cloudstack with token: " 
				+ token + " and volume size: " + volumeSize);
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_DISK_OFFERINGS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		String diskOfferingId = null;
		String customDiskOfferingId = null;
		try {
			JSONArray diskOfferings = new JSONObject(response.getContent())
				.optJSONObject("listdiskofferingsresponse").optJSONArray("diskoffering");
			for (int i = 0; diskOfferings != null && i < diskOfferings.length(); i++) {
				JSONObject diskOffering = diskOfferings.optJSONObject(i);
				if (diskOffering.optString("disksize").equals(volumeSize)) {
					diskOfferingId = diskOffering.optString("id");
					break;
				}
				if (diskOffering.optBoolean("iscustomized") 
						&& diskOffering.optString("disksize").equals("0")) {
					customDiskOfferingId = diskOffering.getString("id");
				}
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		if (diskOfferingId != null) {
			createVolumeUriBuilder.addParameter(DISK_OFFERING_ID, diskOfferingId);
		} else if (customDiskOfferingId != null) {
			createVolumeUriBuilder.addParameter(DISK_OFFERING_ID, customDiskOfferingId);
			createVolumeUriBuilder.addParameter(VOLUME_SIZE, volumeSize);
		} else {
			throw new OCCIException(ErrorType.NOT_FOUND, 
					"No disk offering available to create this volume.");
		}
	}

	@Override
	public List<Instance> getInstances(Token token) {
		LOGGER.debug("Listing cloudstack volumes with access ID: " + token.getAccessId());
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_VOLUMES_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		List<Instance> instances = new LinkedList<Instance>();
		try {
			JSONObject jsonResponse = new JSONObject(response.getContent());
			JSONArray jsonVolumes = jsonResponse.optJSONObject(
					"listvolumesresponse").optJSONArray("volume");			
			for (int i = 0; jsonVolumes != null && i < jsonVolumes.length(); i++) {
				JSONObject instanceJson = jsonVolumes.optJSONObject(i);
				instances.add(mountInstance(instanceJson));
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		return instances;
	}

	private Instance mountInstance(JSONObject instanceJson) {
		String id = instanceJson.optString("id");
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get(OrderConstants.STORAGE_TERM));
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("occi.storage.name", instanceJson.optString("name"));
		attributes.put("occi.storage.status", instanceJson.optString("state"));
		int gbInKBytes = (1024 * 1024 * 1024);
		attributes.put("occi.storage.size", String.valueOf(instanceJson.optInt("size") / gbInKBytes));
		attributes.put("occi.core.id", id);
		return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), null);
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.debug("Getting storage instance " + instanceId + ", with token " + token);
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			if (instance.getId().equals(instanceId)) {
				return instance;
			}
		}
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		LOGGER.debug("Removing storage instance " + instanceId + ". With token: " + token);
		URIBuilder uriBuilder = createURIBuilder(endpoint, DELETE_VOLUME_COMMAND);
		uriBuilder.addParameter(VOLUME_ID, instanceId);
		
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		try {
			JSONObject responseJson = new JSONObject(response.getContent()).optJSONObject(
					"deletevolumeresponse");
			boolean success = responseJson.optBoolean("success");
			if (!success) {
				LOGGER.debug("Could not remove storage instance " + instanceId + ". " + responseJson.optString("displaytext"));
				throw new OCCIException(ErrorType.BAD_REQUEST, responseJson.optString("displaytext"));
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public void removeInstances(Token token) {
		LOGGER.debug("Removing all storage instances. With token: " + token);
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			removeInstance(token, instance.getId());
		}
	}
	
	protected static URIBuilder createURIBuilder(String endpoint, String command) {
		try {
			URIBuilder uriBuilder = new URIBuilder(endpoint);
			uriBuilder.addParameter(COMMAND, command);
			return uriBuilder;
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}
	
	private static final int SC_PARAM_ERROR = 431;
    private static final int SC_INSUFFICIENT_CAPACITY_ERROR = 533;
	private static final int SC_RESOURCE_UNAVAILABLE_ERROR = 534;
	private static final int SC_RESOURCE_ALLOCATION_ERROR = 535;
	
	protected void checkStatusResponse(StatusLine statusLine) {
		if (statusLine.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (statusLine.getStatusCode() == SC_PARAM_ERROR) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} else if (statusLine.getStatusCode() == SC_INSUFFICIENT_CAPACITY_ERROR || 
				statusLine.getStatusCode() == SC_RESOURCE_UNAVAILABLE_ERROR) {
			throw new OCCIException(ErrorType.NO_VALID_HOST_FOUND, ResponseConstants.NO_VALID_HOST_FOUND);
		} else if (statusLine.getStatusCode() == SC_RESOURCE_ALLOCATION_ERROR) {
			throw new OCCIException(ErrorType.QUOTA_EXCEEDED, 
					ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
		} else if (statusLine.getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST, statusLine.getReasonPhrase());
		}
	}

}
