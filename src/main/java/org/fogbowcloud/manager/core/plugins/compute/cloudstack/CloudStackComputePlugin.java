package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.identity.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Request;
import org.restlet.Response;

public class CloudStackComputePlugin implements ComputePlugin {

	private static final Logger LOGGER = Logger.getLogger(CloudStackComputePlugin.class);
	
	protected static final String LIST_VMS_COMMAND = "listVirtualMachines";
	protected static final String DEPLOY_VM_COMMAND = "deployVirtualMachine";
	protected static final String LIST_RESOURCE_LIMITS_COMMAND = "listResourceLimits";
	protected static final String DESTROY_VM_COMMAND = "destroyVirtualMachine";
	protected static final String LIST_SERVICE_OFFERINGS_COMMAND = "listServiceOfferings";
	protected static final String LIST_TEMPLATES_COMMAND = "listTemplates";
	protected static final String REGISTER_TEMPLATE_COMMAND = "registerTemplate";
	protected static final String LIST_OS_TYPES_COMMAND = "listOsTypes";
	
	private static final String COMMAND = "command";
	protected static final String TEMPLATE_ID = "templateid";
	protected static final String SERVICE_OFFERING_ID = "serviceofferingid";
	protected static final String ZONE_ID = "zoneid";
	protected static final String VM_ID = "id";
	protected static final String TEMPLATE_FILTER = "templatefilter";
	protected static final String IS_PUBLIC = "ispublic";
	protected static final String URL = "url";
	protected static final String OS_TYPE_ID = "ostypeid";
	protected static final String NAME = "name";
	protected static final String HYPERVISOR = "hypervisor";
	protected static final String FORMAT = "format";
	protected static final String DISPLAY_TEXT = "displaytext";
	protected static final String USERDATA = "userdata";
	
	private static final int LIMIT_TYPE_INSTANCES = 0;
	private static final int LIMIT_TYPE_MEMORY = 9;
	private static final int LIMIT_TYPE_CPU = 8;

	private static final String DEFAULT_HYPERVISOR = "KVM";
	private static final String DEFAULT_OS_TYPE_NAME = "Other (64-bit)";
	
	private Properties properties;
	private HttpClientWrapper httpClient;
	private String endpoint;
	private String zoneId;
	private String imageDownloadBaseUrl;
	private String imageDownloadBasePath;
	private String hypervisor;
	private String osTypeId;

	public CloudStackComputePlugin(Properties properties) {
		this(properties, new HttpClientWrapper());
	}
	
	public CloudStackComputePlugin(Properties properties, HttpClientWrapper httpClient) {
		this.properties = properties;
		this.httpClient = httpClient;
		this.endpoint = this.properties.getProperty("compute_cloudstack_api_url");
		this.zoneId = this.properties.getProperty("compute_cloudstack_zone_id");
		this.imageDownloadBaseUrl = this.properties.getProperty("compute_cloudstack_image_download_base_url");
		this.imageDownloadBasePath = this.properties.getProperty("compute_cloudstack_image_download_base_path");
		String hypervisorType = this.properties.getProperty("compute_cloudstack_hypervisor");
		this.hypervisor = hypervisorType == null ? DEFAULT_HYPERVISOR : hypervisorType;
		this.osTypeId = this.properties.getProperty("compute_cloudstack_image_download_os_type_id");
	}
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		
		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt);

		if (imageId == null) {
			LOGGER.error("Local image id must be specified.");
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		if (zoneId == null) {
			LOGGER.error("Default zone id must be specified.");
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		categories.remove(new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS));
		
		URIBuilder uriBuilder = createURIBuilder(endpoint, DEPLOY_VM_COMMAND);
		uriBuilder.addParameter(TEMPLATE_ID, imageId);
		uriBuilder.addParameter(ZONE_ID, zoneId);
		
		Flavor serviceOffering = getFlavor(token,
				xOCCIAtt.get(RequestAttribute.REQUIREMENTS.getValue()));
		String serviceOfferingId = null;
		if (serviceOffering != null) {
			serviceOfferingId = serviceOffering.getId();
		}
		uriBuilder.addParameter(SERVICE_OFFERING_ID, serviceOfferingId);
		String userdata = xOCCIAtt.get(RequestAttribute.USER_DATA_ATT.getValue());
		if (userdata != null) {
			uriBuilder.addParameter(USERDATA, userdata);
		}
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doPost(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		try {
			JSONObject vm = new JSONObject(response.getContent()).optJSONObject(
					"deployvirtualmachineresponse");
			return vm.optString("id");
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	private Flavor getFlavor(Token token, String requirements) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_SERVICE_OFFERINGS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		List<Flavor> flavours = new LinkedList<Flavor>();
		try {
			JSONArray jsonOfferings = new JSONObject(response.getContent()).optJSONObject(
					"listserviceofferingsresponse").optJSONArray("serviceoffering");
			for (int i = 0; jsonOfferings != null && i < jsonOfferings.length(); i++) {
				JSONObject jsonOffering = jsonOfferings.optJSONObject(i);
				flavours.add(new Flavor(jsonOffering.optString(NAME),
						jsonOffering.optString("id"),
						jsonOffering.optString("cpunumber"), 
						jsonOffering.optString("memory"), 
						Integer.valueOf(0).toString()));
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		return RequirementsHelper.findSmallestFlavor(flavours, requirements);
	}

	@Override
	public List<Instance> getInstances(Token token) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_VMS_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		List<Instance> instances = new LinkedList<Instance>();
		try {
			JSONArray jsonVms = new JSONObject(response.getContent()).optJSONObject(
					"listvirtualmachinesresponse").optJSONArray("virtualmachine");			
			for (int i = 0; jsonVms != null && i < jsonVms.length(); i++) {
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
		
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		JSONObject instanceJson = null;
		try {
			JSONArray instancesJson = new JSONObject(response.getContent()).optJSONObject(
					"listvirtualmachinesresponse").optJSONArray("virtualmachine");
			if (instancesJson == null || instancesJson.length() == 0) {
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
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		
		int instancesQuota = 0;
		int cpuQuota = 0;
		int memQuota = 0;
		
		try {
			JSONArray limitsJson = new JSONObject(response.getContent()).optJSONObject(
					"listresourcelimitsresponse").optJSONArray("resourcelimit");
			for (int i = 0; limitsJson != null && i < limitsJson.length(); i++) {
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
		throw new UnsupportedOperationException();
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName,
			String diskFormat) {
		if (imagePath.indexOf(imageDownloadBasePath) != 0) {
			LOGGER.warn("Image path is not relative to the base path.");
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		String imageURL = imagePath.replace(imageDownloadBasePath, imageDownloadBaseUrl + "/");
		URIBuilder uriBuilder = createURIBuilder(endpoint, REGISTER_TEMPLATE_COMMAND);
		uriBuilder.addParameter(DISPLAY_TEXT, imageName);
		uriBuilder.addParameter(FORMAT, diskFormat.toUpperCase());
		uriBuilder.addParameter(HYPERVISOR, hypervisor);
		uriBuilder.addParameter(NAME, imageName);
		uriBuilder.addParameter(OS_TYPE_ID, getOSTypeId(token));
		uriBuilder.addParameter(ZONE_ID, zoneId);
		uriBuilder.addParameter(URL, imageURL);
		uriBuilder.addParameter(IS_PUBLIC, Boolean.TRUE.toString());
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doPost(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
	}

	@Override
	public String getImageId(Token token, String imageName) {
		JSONObject template = getTemplateByName(token, imageName);
		return template == null ? null : template.optString("id");
	}
	
	@Override
	public ImageState getImageState(Token token, String imageName) {
		JSONObject template = getTemplateByName(token, imageName);
		if (template == null) {
			return null;
		}
		boolean isReady = template.optBoolean("isready");
		if (isReady) {
			return ImageState.ACTIVE;
		}
		return ImageState.PENDING;
	}

	private JSONObject getTemplateByName(Token token, String templateName) {
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_TEMPLATES_COMMAND);
		uriBuilder.addParameter(TEMPLATE_FILTER, "executable");
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());	
		try {
			JSONArray jsonTemplates = new JSONObject(response.getContent()).optJSONObject(
					"listtemplatesresponse").optJSONArray("template");
			for (int i = 0; jsonTemplates != null && i < jsonTemplates.length(); i++) {
				JSONObject template = jsonTemplates.optJSONObject(i);
				if (template.optString(NAME).equals(templateName)) {
					return template;
				}
			}
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		return null;
	}
	
	private String getOSTypeId(Token token) {
		if (osTypeId != null) {
			return osTypeId;
		}
		
		URIBuilder uriBuilder = createURIBuilder(endpoint, LIST_OS_TYPES_COMMAND);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper response = httpClient.doGet(uriBuilder.toString());
		checkStatusResponse(response.getStatusLine());
		try {
			JSONArray jsonOsTypes = new JSONObject(response.getContent()).optJSONObject(
					"listostypesresponse").optJSONArray("ostype");
			for (int i = 0; jsonOsTypes != null && i < jsonOsTypes.length(); i++) {
				JSONObject osType = jsonOsTypes.optJSONObject(i);
				if (osType.optString("description").equals(DEFAULT_OS_TYPE_NAME)) {
					return osType.optString("id");
				}
			}
			if (jsonOsTypes != null) {
				return jsonOsTypes.optJSONObject(0).optString("id");
			}
			
			LOGGER.warn("No OS type has been defined and default value "
					+ DEFAULT_OS_TYPE_NAME + " wansn't foud.");
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		} catch (JSONException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
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
