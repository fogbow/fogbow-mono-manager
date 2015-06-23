package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.client.utils.URIBuilder;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.identity.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Request;
import org.restlet.Response;

public class CloudStackComputePlugin implements ComputePlugin {

	private static final String EXPUNGE = "expunge";
	private static final String COMMAND = "command";
	private static final String LIST_VMS_COMMAND = "listVirtualMachines";
	private static final String LIST_RESOURCE_LIMITS_COMMAND = "listResourceLimits";
	
	private static final String DESTROY_VM_COMMAND = "destroyVirtualMachine";
	
	private static final String VM_ID = "id";
	
	private static final int LIMIT_TYPE_INSTANCES = 0;
	private static final int LIMIT_TYPE_MEMORY = 9;
	private static final int LIMIT_TYPE_CPU = 8;
	
	private Properties properties;
	private HttpClientWrapper httpClient;
	private String endpoint;
	private String defaultZoneId;

	public CloudStackComputePlugin(Properties properties) {
		this(properties, new HttpClientWrapper());
	}
	
	public CloudStackComputePlugin(Properties properties, HttpClientWrapper httpClient) {
		this.properties = properties;
		this.httpClient = httpClient;
		this.endpoint = this.properties.getProperty("compute_cloudstack_api_url");
		this.defaultZoneId = this.properties.getProperty("compute_cloudstack_default_zone");
	}
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		return null;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_VMS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		String response = httpClient.doGet(uriBuilder.toString());
		System.out.println(response);
		List<Instance> instances = new LinkedList<Instance>();
		try {
			JSONArray jsonVms = new JSONObject(response).optJSONObject(
					"listvirtualmachinesresponse").optJSONArray("virtualmachine");
			for (int i = 0; i < jsonVms.length(); i++) {
				JSONObject instanceJson = jsonVms.optJSONObject(i);
				instances.add(mountInstance(instanceJson));
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_VMS_COMMAND);
		uriBuilder.addParameter(VM_ID, instanceId);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		String response = httpClient.doGet(uriBuilder.toString());
		JSONObject instanceJson = null;
		try {
			JSONArray instancesJson = new JSONObject(response).optJSONObject(
					"listvirtualmachinesresponse").optJSONArray("virtualmachine");
			if (instancesJson.length() == 0) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			instanceJson = instancesJson.optJSONObject(0);
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		return mountInstance(instanceJson);
	}

	private Instance mountInstance(JSONObject instanceJson) {
		Map<String, String> attributes = new HashMap<String, String>();
		
		InstanceState state = getInstanceState(instanceJson.optString("state"));
		attributes.put("occi.compute.state", state.getOcciState());
		attributes.put("occi.compute.speed", instanceJson.optString("cpuspeed"));
		attributes.put("occi.compute.architecture", "Not defined");
		attributes.put("occi.compute.memory", String.valueOf(instanceJson.optDouble("memory") / 1024)); // Gb
		attributes.put("occi.compute.cores", instanceJson.optString("cpunumber"));
		attributes.put("occi.compute.hostname", instanceJson.optString("hostname"));
		
		String id = instanceJson.optString(VM_ID);
		attributes.put("occi.core.id", id);
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		
		String serviceOfferingName = instanceJson.optString("serviceofferingname");
		resources.add(ResourceRepository.generateFlavorResource(serviceOfferingName));
		
		return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), state);
	}

	private InstanceState getInstanceState(String vmState) {
		if ("Running".equalsIgnoreCase(vmState)) {
			return InstanceState.RUNNING;
		}
		if ("Shutdowned".equalsIgnoreCase(vmState)) {
			return InstanceState.SUSPENDED;
		}
		if ("Error".equalsIgnoreCase(vmState)) {
			return InstanceState.FAILED;
		}
		return InstanceState.PENDING;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, DESTROY_VM_COMMAND);
		uriBuilder.addParameter(VM_ID, instanceId);
		uriBuilder.addParameter(EXPUNGE, Boolean.TRUE.toString());
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		httpClient.doPost(uriBuilder.toString());
	}

	@Override
	public void removeInstances(Token token) {
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			removeInstance(token, instance.getId());
		}
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_RESOURCE_LIMITS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		String response = httpClient.doGet(uriBuilder.toString());
		
		int instancesQuota = 0;
		int cpuQuota = 0;
		int memQuota = 0;
		
		try {
			JSONArray limitsJson = new JSONObject(response).optJSONObject(
					"listresourcelimitsresponse").optJSONArray("resourcelimit");
			for (int i = 0; i < limitsJson.length(); i++) {
				JSONObject limit = limitsJson.optJSONObject(i);
				int max = limit.optInt("max") < 0 ? Integer.MAX_VALUE : limit.optInt("max");
				int capacityType = limit.optInt("resourcetype");
				switch (capacityType) {
				case LIMIT_TYPE_INSTANCES:
					instancesQuota = max;
					break;
				case LIMIT_TYPE_CPU:
					cpuQuota = max;
					break;
				case LIMIT_TYPE_MEMORY:
					memQuota = max;
					break;
				default:
					break;
				}
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		List<Instance> instances = getInstances(token);
		Integer cpuInUse = 0;
		Integer memInUse = 0;
		Integer instancesInUse = instances.size();
		for (Instance instance : instances) {
			cpuInUse += Integer.valueOf(
					instance.getAttributes().get("occi.compute.cores"));
			memInUse += (int) (Double.valueOf(
					instance.getAttributes().get("occi.compute.memory")) * 1024);
		}
		
		ResourcesInfo resInfo = new ResourcesInfo(
				String.valueOf(cpuQuota - cpuInUse), cpuInUse.toString(), 
				String.valueOf(memQuota - memInUse), memInUse.toString(), 
				String.valueOf(instancesQuota - instancesInUse), instancesInUse.toString());
		return resInfo;
	}

	@Override
	public void bypass(Request request, Response response) {
		
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName,
			String diskFormat) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getImageId(Token token, String imageName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ImageState getImageState(Token token, String imageName) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private static URIBuilder createURIBuilder(String endpoint, String command) {
		try {
			URIBuilder uriBuilder = new URIBuilder(endpoint);
			uriBuilder.addParameter(COMMAND, command);
			return uriBuilder;
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}
	
}
