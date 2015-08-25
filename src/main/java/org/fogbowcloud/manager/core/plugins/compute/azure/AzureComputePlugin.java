package org.fogbowcloud.manager.core.plugins.compute.azure;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.core.plugins.util.SslHelper;
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
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.restlet.Request;
import org.restlet.Response;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.utils.KeyStoreType;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.ComputeManagementService;
import com.microsoft.windowsazure.management.compute.HostedServiceOperations;
import com.microsoft.windowsazure.management.compute.models.ConfigurationSet;
import com.microsoft.windowsazure.management.compute.models.ConfigurationSetTypes;
import com.microsoft.windowsazure.management.compute.models.DeploymentSlot;
import com.microsoft.windowsazure.management.compute.models.HostedServiceCreateParameters;
import com.microsoft.windowsazure.management.compute.models.InputEndpoint;
import com.microsoft.windowsazure.management.compute.models.InputEndpointTransportProtocol;
import com.microsoft.windowsazure.management.compute.models.OSVirtualHardDisk;
import com.microsoft.windowsazure.management.compute.models.Role;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateDeploymentParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineRoleType;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;

public class AzureComputePlugin implements ComputePlugin {

	private static final Logger LOGGER = Logger
			.getLogger(AzureComputePlugin.class);

	private static final String BASE_URL = "https://management.core.windows.net/";
	private static final String GET_FLAVOR_COMMAND = "/rolesizes";
	private static final String GET_CLOUD_SERVICE_COMMAND = "/services/hostedservices";
	private static final String GET_VMS_COMMAND = "/services/hostedservices/%s/deployments/%s";
	private static final String DELETE_VM_COMMAND = "/services/hostedservices/%s/deployments/%s";
	private static final String DELETE_CLOUD_SERVICE_COMMAND = "/services/hostedservices/%s";
	private static final String LIST_IMAGES_COMMAND = "/services/vmimages";

	private static final String AZURE_VM_DEFAULT_LABEL = "FogbowVM";
	private static final String[] AZURE_STATE_RUNNING = { "Running" };
	private static final String[] AZURE_STATE_FAILED = { "Deleting" };
	private static final String[] AZURE_STATE_PENDING = { "Deploying",
			"Starting", "RunningTransitioning", "SuspendedTransitioning" };
	private static final String[] AZURE_STATE_SUSPENDED = { "Suspending",
			"Suspended" };
	
	private static final String STORAGE_CONTAINER = "vhd-store";

	private static final int XML_VM_NAME = 0;
	private static final int XML_VM_ID = 0;
	private static final int XML_VM_STATE = 3;
	private static final int XML_ROLE_INSTANCES = 7;
	private static final int XML_ROLE_INSTANCE = 0;
	private static final int XML_ROLE_INSTANCE_FLAVOR = 5;
	private static final int XML_FLAVOR_NAME = 0;
	private static final int XML_FLAVOR_CPU = 2;
	private static final int XML_FLAVOR_MEM = 3;
	private static final int XML_FLAVOR_DISK = 8;
	private static final int XML_CLOUD_SERVICE = 1;

	private SSLConnectionSocketFactory sslSocketFactory;
	protected List<Flavor> flavors;
	private HttpClientWrapper httpWrapper;
	private Properties properties;
	private int maxVCPU;
	private int maxRAM;
	private int maxInstances;	

	private String region;

	protected AzureComputePlugin(HttpClientWrapper httpWrapper,
			Properties properties) {
		this.httpWrapper = httpWrapper;
		this.properties = properties;

		this.region = properties.getProperty("compute_azure_region");
		if (region == null) {
			region = "East US";
		}

		String maxCPUStr = properties.getProperty("compute_azure_max_vcpu");
		if (maxCPUStr == null) {
			LOGGER.error("Property compute_azure_max_vcpu must be set.");
			throw new IllegalArgumentException(
					"Property compute_azure_max_vcpu must be set.");
		}
		this.maxVCPU = Integer.parseInt(maxCPUStr);

		String maxRAMStr = properties.getProperty("compute_azure_max_ram");
		if (maxRAMStr == null) {
			LOGGER.error("Property compute_azure_max_ram must be set.");
			throw new IllegalArgumentException(
					"Property compute_azure_max_ram must be set.");
		}
		this.maxRAM = Integer.parseInt(maxRAMStr);

		String maxInstancesStr = properties
				.getProperty("compute_azure_max_instances");
		if (maxInstancesStr == null) {
			LOGGER.error("Property compute_azure_max_instances must be set.");
			throw new IllegalArgumentException(
					"Property compute_azure_max_instances must be set.");
		}
		this.maxInstances = Integer.parseInt(maxInstancesStr);
	}

	public AzureComputePlugin(Properties properties) {
		this(new HttpClientWrapper(), properties);
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		LOGGER.debug("Requesting instance with token=" + token
				+ "; categories=" + categories + "; xOCCIAtt=" + xOCCIAtt);

		if (imageId == null) {
			LOGGER.error("Local image id must be specified.");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		String userName = "fogbow" + (int) (Math.random() * 10000);
		String deploymentName = userName;
		String userpassword = UUID.randomUUID().toString();
		System.out.println(userpassword);
		VirtualMachineCreateDeploymentParameters deploymentParameters = new VirtualMachineCreateDeploymentParameters();

		ResourcesInfo resourcesInfo = getResourcesInfo(token);
		if (Integer.parseInt(resourcesInfo.getInstancesIdle()) == 0) {
			throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
					ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
		}
		
		Flavor flavor = RequirementsHelper.findSmallestFlavor(
				new LinkedList<Flavor>(getFlavors(token)), 
				xOCCIAtt.get(RequestAttribute.REQUIREMENTS.getValue()));

		if (Integer.parseInt(resourcesInfo.getCpuIdle()) < Integer
				.parseInt(flavor.getCpu())
				|| Integer.parseInt(resourcesInfo.getMemIdle()) < Integer
						.parseInt(flavor.getMem())) {
			throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
					ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
		}

		ComputeManagementClient computeManagementClient = null;
		try {
			computeManagementClient = createComputeManagementClient(token);
			createHostedService(computeManagementClient, deploymentName);
		} catch (Exception e) {
			System.out.println(e);
			LOGGER.error("It was not possible to create the Hosted Service", e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"It was not possible to create the Hosted Service");
		}
		
		String userData = xOCCIAtt.get(RequestAttribute.USER_DATA_ATT
				.getValue());	
		deploymentParameters.setDeploymentSlot(DeploymentSlot.Staging);
		deploymentParameters.setName(deploymentName);
		deploymentParameters.setLabel(AZURE_VM_DEFAULT_LABEL);

		try {
			ArrayList<Role> rolelist = createRoleList(deploymentName, userName,
					userpassword, imageId, flavor.getName(), userData,
					computeManagementClient);
			deploymentParameters.setRoles(rolelist);
			computeManagementClient.getVirtualMachinesOperations()
					.createDeployment(deploymentName, deploymentParameters);
			computeManagementClient.close();
		} catch (Exception e) {
			LOGGER.error("It was not possible to create the Virtual Machine", e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"It was not possible to create the Virtual Machine");
		}
		return deploymentName;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		if (token.get(AzureAttributes.SUBSCRIPTION_ID_KEY) == null) {
			LOGGER.error("Subscription ID can't be null");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"Subscription ID can't be null");
		}
		List<String> cloudServicesNames = getCloudServicesNames(token);
		List<Instance> instances = new LinkedList<Instance>();
		for (String cloudService : cloudServicesNames) {
			Instance instance = getInstance(token, cloudService);
			if (instance != null) {
				instances.add(instance);
			}
		}
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(String.format(GET_VMS_COMMAND, instanceId, instanceId));
		HttpResponseWrapper response = httpWrapper.doGetSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		try {
			checkStatusResponse(response.getStatusLine());
			return mountInstanceFromXML(token, response);
		} catch (OCCIException occiException) {
			/*
			 * Azure throws the error "not found" when there is a cloud service
			 * not associated with any virtual machines, we want to ignore those
			 * cloud services.
			 */
			if (!occiException.getType().equals(ErrorType.NOT_FOUND)) {
				throw occiException;
			}
		}
		return null;
	}

	public void removeCloudService(Token token, String cloudServiceId) {
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(String.format(DELETE_CLOUD_SERVICE_COMMAND, cloudServiceId));
		HttpResponseWrapper response = httpWrapper.doDeleteSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		checkStatusResponse(response.getStatusLine());
	}

	public void removeCloudServices(Token token) {
		List<String> cloudServices = getCloudServicesNames(token);
		for (String cloudService : cloudServices) {
			removeCloudService(token, cloudService);
		}
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		StringBuilder urlDeleteVM = new StringBuilder(BASE_URL);
		urlDeleteVM.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		urlDeleteVM.append(String.format(DELETE_VM_COMMAND, instanceId,
				instanceId));
		HttpResponseWrapper response = httpWrapper.doDeleteSSL(
				urlDeleteVM.toString(), getSSLFromToken(token),
				getHeaders(null));
		checkStatusResponse(response.getStatusLine());
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
		List<Instance> instances = getInstances(token);
		int cpuInUse = 0;
		int ramInUse = 0;
		for (Instance instance : instances) {
			Map<String, String> attributes = instance.getAttributes();
			String memoryStr = attributes.get("occi.compute.memory");
			ramInUse += Integer.parseInt(memoryStr) * 1024;
			String coresStr = attributes.get("occi.compute.cores");
			cpuInUse += Integer.parseInt(coresStr);
		}
		return new ResourcesInfo(String.valueOf(maxVCPU - cpuInUse),
				String.valueOf(cpuInUse), String.valueOf(maxRAM - ramInUse),
				String.valueOf(ramInUse), String.valueOf(maxInstances
						- instances.size()), String.valueOf(instances.size()));
	}

	@Override
	public void bypass(Request request, Response response) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName,
			String diskFormat) {
		// I think it is not possible to upload a new image in azure.
		throw new UnsupportedOperationException();
	}

	@Override
	public String getImageId(Token token, String imageName) {
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(LIST_IMAGES_COMMAND);
		HttpResponseWrapper response = httpWrapper.doGetSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		System.out.println(response.getContent());
		return null;
	}

	@Override
	public ImageState getImageState(Token token, String imageName) {
		// Since there are only Azure images, the only possible state is active
		return ImageState.ACTIVE;
	}

	private void createHostedService(
			ComputeManagementClient computeManagementClient,
			String hostedServiceName) throws Exception {
		HostedServiceOperations hostedServiceOperations = computeManagementClient
				.getHostedServicesOperations();

		HostedServiceCreateParameters createParameters = new HostedServiceCreateParameters();
		createParameters.setServiceName(hostedServiceName);
		createParameters.setLocation(region);

		hostedServiceOperations.create(createParameters);
		LOGGER.debug("hostedservice created: " + hostedServiceName);
	}

	protected static Configuration createConfiguration(Token token)
			throws Exception {
		return ManagementConfiguration.configure(new URI(BASE_URL),
				token.get(AzureAttributes.SUBSCRIPTION_ID_KEY),
				token.get(AzureAttributes.KEYSTORE_PATH_KEY),
				token.get(AzureAttributes.KEYSTORE_PASSWORD_KEY),
				KeyStoreType.jks);
	}

	protected static ComputeManagementClient createComputeManagementClient(
			Token token) throws Exception {
		Configuration config = createConfiguration(token);
		ComputeManagementClient computeManagementClient = ComputeManagementService
				.create(config);
		return computeManagementClient;
	}

	private ArrayList<Role> createRoleList(String virtualMachineName,
			String username, String userPassword, String imageID,
			String roleSizeName, String userData,
			ComputeManagementClient computeManagementClient) throws Exception {
		int random = (int) (Math.random() * 100);
		ArrayList<Role> roleList = new ArrayList<Role>();
		Role role = new Role();
		String roleName = virtualMachineName;
		String computerName = virtualMachineName;
		String storageAccountName = properties
				.getProperty("compute_azure_storage_account_name");
		if (storageAccountName == null) {
			LOGGER.error("The azure storage account name can't be null");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"The azure storage account name can't be null");
		}
		URI mediaLinkUriValue = new URI("http://" + storageAccountName
				+ ".blob.core.windows.net/" + STORAGE_CONTAINER + "/"
				+ virtualMachineName + random + ".vhd");
		String osVHarddiskName = username + "oshdname" + random;
		ArrayList<ConfigurationSet> configurationSetList = new ArrayList<ConfigurationSet>();
		ConfigurationSet configurationSet = new ConfigurationSet();
		configurationSet
				.setConfigurationSetType(ConfigurationSetTypes.LINUXPROVISIONINGCONFIGURATION);
		configurationSet.setComputerName(computerName);
		configurationSet.setUserName(username);
		configurationSet.setUserPassword(userPassword);
		configurationSet.setDisableSshPasswordAuthentication(true);
		if (userData != null) {
			configurationSet.setCustomData(userData);
		}
		
		configurationSet.setHostName(virtualMachineName + ".cloudapp.net");
		configurationSetList.add(configurationSet);
		configurationSetList.add(createSSHConfiguration());

		OSVirtualHardDisk oSVirtualHardDisk = new OSVirtualHardDisk();
		oSVirtualHardDisk.setName(osVHarddiskName);
		oSVirtualHardDisk.setMediaLink(mediaLinkUriValue);
		oSVirtualHardDisk.setSourceImageName(imageID);

		role.setRoleName(roleName);
		role.setRoleType(VirtualMachineRoleType.PersistentVMRole.toString());
		role.setRoleSize(roleSizeName);
		role.setProvisionGuestAgent(true);
		role.setConfigurationSets(configurationSetList);
		role.setOSVirtualHardDisk(oSVirtualHardDisk);
		roleList.add(role);
		return roleList;
	}
	
	private ConfigurationSet createSSHConfiguration() {
		ConfigurationSet configurationSet = new ConfigurationSet();
		configurationSet.setConfigurationSetType(ConfigurationSetTypes.NETWORKCONFIGURATION);
		InputEndpoint inputEndpoint = new InputEndpoint();
		inputEndpoint.setLocalPort(22);
		inputEndpoint.setPort(22);
		inputEndpoint.setEnableDirectServerReturn(false);
		inputEndpoint.setName("SSH");
		inputEndpoint.setProtocol(InputEndpointTransportProtocol.TCP);
		ArrayList<InputEndpoint> endpoints = configurationSet.getInputEndpoints();
		endpoints.add(inputEndpoint);
		configurationSet.setInputEndpoints(endpoints);
		return configurationSet;
	}

	private InstanceState getOCCIStatus(String azureStatus) {
		for (String azureStatusRunning : AZURE_STATE_RUNNING) {
			if (azureStatus.equals(azureStatusRunning)) {
				return InstanceState.RUNNING;
			}
		}
		for (String azureStatusPending : AZURE_STATE_PENDING) {
			if (azureStatus.equals(azureStatusPending)) {
				return InstanceState.PENDING;
			}
		}
		for (String azureStatusFailed : AZURE_STATE_FAILED) {
			if (azureStatus.equals(azureStatusFailed)) {
				return InstanceState.FAILED;
			}
		}
		for (String azureStatusSuspended : AZURE_STATE_SUSPENDED) {
			if (azureStatus.equals(azureStatusSuspended)) {
				return InstanceState.SUSPENDED;
			}
		}
		return InstanceState.PENDING;
	}
	
	private Flavor getFlavorByName(String flavorStr, Token token) {
		Flavor flavor = null;
		for (Flavor flavorSearch : getFlavors(token)) {
			if (flavorSearch.getName().equals(flavorStr)) {
				flavor = flavorSearch;
			}
		}
		return flavor;
	}

	private Instance mountInstanceFromXML(Token token,
			HttpResponseWrapper response) {
		Element virtualMachine = getElementFromResponse(response.getContent());
		Map<String, String> attributes = new HashMap<String, String>();

		String name = virtualMachine.getChildren().get(XML_VM_NAME).getText();
		String id = virtualMachine.getChildren().get(XML_VM_ID).getText();
		String stateStr = virtualMachine.getChildren().get(XML_VM_STATE)
				.getText();
		Element roleInstance = virtualMachine.getChildren()
				.get(XML_ROLE_INSTANCES).getChildren().get(XML_ROLE_INSTANCE);
		InstanceState state = getOCCIStatus(stateStr);
		String flavorStr = roleInstance.getChildren()
				.get(XML_ROLE_INSTANCE_FLAVOR).getText();

		Flavor flavor = getFlavorByName(flavorStr, token);
		
		attributes.put("occi.core.id", id);
		attributes.put("occi.compute.hostname", name);
		attributes.put("occi.compute.cores", flavor.getCpu());
		attributes.put("occi.compute.memory",
				String.valueOf(Integer.parseInt(flavor.getMem()) / 1024)); // Gb
		attributes.put("occi.compute.architecture", "Not defined");
		attributes.put("occi.compute.speed", "Not defined");
		attributes.put("occi.compute.state", state.getOcciState());

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.generateFlavorResource(flavorStr));

		return new Instance(id, resources, attributes,
				new ArrayList<Instance.Link>(), state);
	}

	private List<String> getCloudServicesNames(Token token) {
		List<String> cloudServicesNames = new LinkedList<String>();
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(GET_CLOUD_SERVICE_COMMAND);
		HttpResponseWrapper response = httpWrapper.doGetSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		checkStatusResponse(response.getStatusLine());
		List<Element> cloudServices = getElementFromResponse(
				response.getContent()).getChildren();
		for (Element cloudService : cloudServices) {
			String cloudServiceName = cloudService.getChildren()
					.get(XML_CLOUD_SERVICE).getText();
			cloudServicesNames.add(cloudServiceName);
		}
		return cloudServicesNames;
	}

	private List<Flavor> getFlavors(Token token) {
		if (flavors != null) {
			return flavors;
		}
		flavors = new LinkedList<Flavor>();
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(GET_FLAVOR_COMMAND);
		HttpResponseWrapper response = httpWrapper.doGetSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		checkStatusResponse(response.getStatusLine());
		List<Element> flavorsAzure = getElementFromResponse(
				response.getContent()).getChildren();
		for (Element flavorAzure : flavorsAzure) {
			String name = flavorAzure.getChildren().get(XML_FLAVOR_NAME)
					.getText();
			String cpu = flavorAzure.getChildren().get(XML_FLAVOR_CPU)
					.getText();
			String mem = flavorAzure.getChildren().get(XML_FLAVOR_MEM)
					.getText();
			String disk = String.valueOf(Integer.parseInt(flavorAzure
					.getChildren().get(XML_FLAVOR_DISK).getText()) / 1024);
			Flavor flavor = new Flavor(name, cpu, mem, disk);
			flavors.add(flavor);
		}
		return flavors;
	}

	private SSLConnectionSocketFactory getSSLFromToken(Token token) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		return sslSocketFactory;
	}

	private Element getElementFromResponse(String response) {
		SAXBuilder builder = new SAXBuilder();
		Document document;
		try {
			document = builder.build(new StringReader(response));
		} catch (Exception e) {
			LOGGER.warn("It was not possible to retrieve"
					+ " XML from the response", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
		Element element = document.getRootElement();
		return element;
	}

	protected void checkStatusResponse(StatusLine statusLine) {
		if (statusLine.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED,
					ResponseConstants.UNAUTHORIZED);
		} else if (statusLine.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND,
					statusLine.getReasonPhrase());
		} else if (statusLine.getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					statusLine.getReasonPhrase());
		}
	}

	private Map<String, String> getHeaders(Map<String, String> extraHeaders) {
		Map<String, String> headers = new HashMap<String, String>();
		if (extraHeaders != null) {
			headers.putAll(extraHeaders);
		}
		headers.put("x-ms-version", " 2015-04-01");
		return headers;
	}

}
