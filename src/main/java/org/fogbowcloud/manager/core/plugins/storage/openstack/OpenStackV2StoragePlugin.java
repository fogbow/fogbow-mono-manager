package org.fogbowcloud.manager.core.plugins.storage.openstack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenStackV2StoragePlugin implements StoragePlugin {

	protected static final String KEY_JSON_ID = "id";
	protected static final String KEY_JSON_SIZE = "size";
	protected static final String KEY_JSON_INSTANCE_UUID = "instance_uuid";
	protected static final String KEY_JSON_MOUNTPOINT = "mountpoint";
	protected static final String KEY_JSON_VOLUME = "volume";
	protected static final String KEY_JSON_VOLUMES = "volumes";
	protected static final String SUFIX_ENDPOINT_VOLUMES = "/volumes";
	protected static final String SUFIX_ENDPOINT_ACTION = "/action";
	protected static final String COMPUTE_V2_API_ENDPOINT = "/v2/";
	
	public static final String STORAGE_NOVAV2_URL_KEY = "storage_v2_url";
	
	protected static final String TENANT_ID = "tenantId";

	private HttpClient client;
	private String storageV2APIEndpoint;
	
	private static final Logger LOGGER = Logger.getLogger(OpenStackV2StoragePlugin.class);
	protected static final String SIZE = KEY_JSON_SIZE;
	
	public OpenStackV2StoragePlugin(Properties properties) {
		this.storageV2APIEndpoint = properties
				.getProperty(STORAGE_NOVAV2_URL_KEY)
				+ COMPUTE_V2_API_ENDPOINT;
		
		initClient();
	}
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}		
		String size = xOCCIAtt.get(OrderAttribute.STORAGE_SIZE.getValue());
		
		JSONObject jsonRequest = null;
		try {			
			jsonRequest = generateJsonEntityToCreateInstance(size);
		} catch (JSONException e) {
			LOGGER.error("An error occurred when generating json.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}		
		
		String endpoint = this.storageV2APIEndpoint + tenantId + SUFIX_ENDPOINT_VOLUMES;
		String responseStr = doPostRequest(endpoint, token.getAccessId(), jsonRequest);
		Instance instanceFromJson = getInstanceFromJson(responseStr);
		return instanceFromJson != null ? instanceFromJson.getId() : null;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}		
		
		String endpoint = this.storageV2APIEndpoint + tenantId + SUFIX_ENDPOINT_VOLUMES;
		String responseStr = doGetRequest(endpoint, token.getAccessId());
		return getInstancesFromJson(responseStr);
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}		
		
		String endpoint = this.storageV2APIEndpoint + tenantId 
				+ SUFIX_ENDPOINT_VOLUMES + "/" + instanceId;
		String responseStr = doGetRequest(endpoint, token.getAccessId());
		return getInstanceFromJson(responseStr);
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		String tenantId = token.getAttributes().get(TENANT_ID);
		if (tenantId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}		
		
		String endpoint = this.storageV2APIEndpoint + tenantId 
				+ SUFIX_ENDPOINT_VOLUMES + "/" + instanceId;
		doDeleteRequest(endpoint, token.getAccessId());
	}

	@Override
	public void removeInstances(Token token) {
		for (Instance instance : getInstances(token)) {
			removeInstance(token, instance.getId());
		}		
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
		}
		else if (response.getStatusLine().getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getStatusLine().toString());
		}
	}	

	
	protected Instance getInstanceFromJson(String json) {
		try {
			JSONObject rootServer = new JSONObject(json);
			JSONObject volumeJson = rootServer.getJSONObject(KEY_JSON_VOLUME);
			String id = volumeJson.getString(KEY_JSON_ID);
			
			List<Resource> resources = new ArrayList<Resource>();
			resources.add(ResourceRepository.getInstance().get(OrderConstants.STORAGE_TERM));
			
			Map<String, String> attributes = new HashMap<String, String>();
			// CPU Architecture of the instance
			attributes.put("occi.storage.name", volumeJson.optString("name"));
			attributes.put("occi.storage.status", volumeJson.optString("status"));
			attributes.put("occi.storage.size", volumeJson.optString("size"));
			attributes.put("occi.core.id", id);

			return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), null);
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting instance storage from json.", e);
		}
		return null;
	}	
	
	protected List<Instance> getInstancesFromJson(String json) {
		try {
			JSONObject rootServer = new JSONObject(json);
			JSONArray jsonArray = rootServer.getJSONArray(OpenStackV2StoragePlugin.KEY_JSON_VOLUMES);
			List<Instance> instances = new ArrayList<Instance>();
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject volumeJsonObject = jsonArray.getJSONObject(i);
				instances.add(new Instance(volumeJsonObject.getString(KEY_JSON_ID)));				
			}

			return instances;
		} catch (JSONException e) {
			LOGGER.warn("There was an exception while getting instances from json.", e);
		}
		return null;
	}		
	
	protected JSONObject generateJsonEntityToCreateInstance(String size) throws JSONException {

		JSONObject volumeContent = new JSONObject();
		volumeContent.put(KEY_JSON_SIZE, size);

		JSONObject volume = new JSONObject();
		volume.put(KEY_JSON_VOLUME, volumeContent);
		
		return volume;
	}	
	
	private void initClient() {
		client = HttpClients.createMinimal();
	}	
	
	public void setClient(HttpClient client) {
		this.client = client;
	}
	
}
