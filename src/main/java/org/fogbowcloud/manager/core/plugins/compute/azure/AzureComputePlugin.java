package org.fogbowcloud.manager.core.plugins.compute.azure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
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
import org.restlet.Request;
import org.restlet.Response;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.utils.KeyStoreType;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.management.ManagementClient;
import com.microsoft.windowsazure.management.ManagementService;
import com.microsoft.windowsazure.management.RoleSizeOperations;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.ComputeManagementService;
import com.microsoft.windowsazure.management.compute.DeploymentOperations;
import com.microsoft.windowsazure.management.compute.HostedServiceOperations;
import com.microsoft.windowsazure.management.compute.models.ConfigurationSet;
import com.microsoft.windowsazure.management.compute.models.ConfigurationSetTypes;
import com.microsoft.windowsazure.management.compute.models.DeploymentGetResponse;
import com.microsoft.windowsazure.management.compute.models.DeploymentSlot;
import com.microsoft.windowsazure.management.compute.models.DeploymentStatus;
import com.microsoft.windowsazure.management.compute.models.HostedServiceCreateParameters;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse.HostedService;
import com.microsoft.windowsazure.management.compute.models.InputEndpoint;
import com.microsoft.windowsazure.management.compute.models.InputEndpointTransportProtocol;
import com.microsoft.windowsazure.management.compute.models.OSVirtualHardDisk;
import com.microsoft.windowsazure.management.compute.models.Role;
import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import com.microsoft.windowsazure.management.compute.models.VirtualIPAddress;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateDeploymentParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineOSImageCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineOSImageGetResponse;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;
import com.microsoft.windowsazure.management.models.RoleSizeListResponse;
import com.microsoft.windowsazure.management.models.RoleSizeListResponse.RoleSize;

public class AzureComputePlugin implements ComputePlugin {

	private static final int SSH_PORT = 22;

	private static final Logger LOGGER = Logger
			.getLogger(AzureComputePlugin.class);

	private static final String BASE_URL = "https://management.core.windows.net/";
	private static final String AZURE_VM_DEFAULT_LABEL = "FogbowVM";
	private static final String STORAGE_CONTAINER = "vhd-store";
	private static final String PERSISTENT_VM_ROLE = "PersistentVMRole";

	protected List<Flavor> flavors;
	private int maxVCPU;
	private int maxRAM;
	private int maxInstances;	

	private String region;
	private String storageAccountName;
	private String storageKey;

	public AzureComputePlugin(Properties properties) {
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
		String storageAccountName = properties
				.getProperty("compute_azure_storage_account_name");
		if (storageAccountName == null) {
			LOGGER.error("Property compute_azure_storage_account_name must be set");
			throw new IllegalArgumentException(
					"Property compute_azure_storage_account_name must be set");
		}
		this.storageAccountName = storageAccountName;
		this.storageKey = properties.getProperty("compute_azure_storage_key");
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

		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		try {
			return requestInstance(token, xOCCIAtt, imageId, computeManagementClient);
		} finally {
			try {
				computeManagementClient.close();
			} catch (IOException e) {
			}
		}
	}

	private String requestInstance(Token token, Map<String, String> xOCCIAtt,
			String imageId, ComputeManagementClient computeManagementClient) {
		String userName = "fogbow" + (int) (Math.random() * 100000);
		String deploymentName = userName;
		String userpassword = UUID.randomUUID().toString();
		
		VirtualMachineCreateDeploymentParameters deploymentParameters = new VirtualMachineCreateDeploymentParameters();

		ResourcesInfo resourcesInfo = getResourcesInfo(token, computeManagementClient);
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

		try {
			createHostedService(computeManagementClient, deploymentName);
		} catch (Exception e) {
			LOGGER.error("It was not possible to create the Hosted Service", e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"It was not possible to create the Hosted Service");
		}
		
		String userData = xOCCIAtt.get(RequestAttribute.USER_DATA_ATT
				.getValue());	
		deploymentParameters.setDeploymentSlot(DeploymentSlot.STAGING);
		deploymentParameters.setName(deploymentName);
		deploymentParameters.setLabel(AZURE_VM_DEFAULT_LABEL);

		try {
			ArrayList<Role> rolelist = createRoleList(deploymentName, userName,
					userpassword, imageId, flavor.getName(), userData,
					computeManagementClient);
			deploymentParameters.setRoles(rolelist);
			computeManagementClient
					.getVirtualMachinesOperations().createDeployment(
							deploymentName, deploymentParameters);
		} catch (Exception e) {
			try {
				removeInstance(deploymentName, computeManagementClient);
			} catch (Exception e1) {
				// Best effort
			}
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
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		try {
			List<Instance> instances = getInstances(token, computeManagementClient);
			return instances;
		} finally {
			try {
				computeManagementClient.close();
			} catch (IOException e) {
			}
		}
	}

	private List<Instance> getInstances(Token token,
			ComputeManagementClient computeManagementClient) {
		HostedServiceOperations hostedServicesOperations = computeManagementClient.getHostedServicesOperations();
		HostedServiceListResponse hostedServiceListResponse = null;
		try {
			hostedServiceListResponse = hostedServicesOperations.list();
		} catch (Exception e) {
			LOGGER.error("Couldn't list hosted services.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, "Couldn't list hosted services.");
		}
		
		ArrayList<HostedService> hostedServices = hostedServiceListResponse.getHostedServices();
		List<Instance> instances = new LinkedList<Instance>();
		for (HostedService hostedService : hostedServices) {
			String serviceLabel = hostedService.getProperties().getLabel();
			if (serviceLabel == null || !serviceLabel.equals(AZURE_VM_DEFAULT_LABEL)) {
				continue;
			}
			DeploymentOperations deploymentsOperations = computeManagementClient.getDeploymentsOperations();
			DeploymentGetResponse deploymentGetResponse = null;
			try {
				deploymentGetResponse = deploymentsOperations.getByName(
						hostedService.getServiceName(), hostedService.getServiceName());
			} catch (ServiceException e) {
				if (e.getHttpStatusCode() == HttpStatus.SC_NOT_FOUND) {
					continue;
				}
				LOGGER.error("Couldn't retrieve deployment " + hostedService.getServiceName() + ".", e);
				throw new OCCIException(ErrorType.BAD_REQUEST, 
						"Couldn't retrieve deployment " + hostedService.getServiceName() + ".");
			} catch (Exception e) {
				LOGGER.error("Couldn't retrieve deployment " + hostedService.getServiceName() + ".", e);
				throw new OCCIException(ErrorType.BAD_REQUEST, 
						"Couldn't retrieve deployment " + hostedService.getServiceName() + ".");
			}
			String deploymentLabel = deploymentGetResponse.getLabel();
			if (deploymentLabel == null || !deploymentLabel.equals(AZURE_VM_DEFAULT_LABEL)) {
				continue;
			}
			
			instances.add(toInstance(deploymentGetResponse, token));
		}
		return instances;
	}

	private Instance toInstance(DeploymentGetResponse deployment, Token token) {
		Map<String, String> attributes = new HashMap<String, String>();

		String name = deployment.getName();
		String id = deployment.getName();
		InstanceState state = getOCCIStatus(deployment.getStatus());

		RoleInstance roleInstance = deployment.getRoleInstances().get(0);
		String instanceSize = roleInstance.getInstanceSize();
		
		Flavor flavor = getFlavorByName(instanceSize, token);
		
		attributes.put("occi.core.id", id);
		attributes.put("occi.compute.hostname", name);
		attributes.put("occi.compute.cores", flavor.getCpu());
		attributes.put("occi.compute.memory",
				String.valueOf(Integer.parseInt(flavor.getMem()) / 1024)); // Gb
		attributes.put("occi.compute.architecture", "Not defined");
		attributes.put("occi.compute.speed", "Not defined");
		attributes.put("occi.compute.state", state.getOcciState());
		
		ArrayList<VirtualIPAddress> addresses = deployment.getVirtualIPAddresses();
		if (!addresses.isEmpty()) {
			attributes.put(Instance.SSH_PUBLIC_ADDRESS_ATT, 
					addresses.get(0).getAddress().getHostAddress() + ":" + SSH_PORT);
		}

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.generateFlavorResource(instanceSize));

		return new Instance(id, resources, attributes,
				new ArrayList<Instance.Link>(), state);
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		try {
			return getInstance(token, instanceId, computeManagementClient);
		} finally {
			try {
				computeManagementClient.close();
			} catch (IOException e) {
			}
		}
	}

	private Instance getInstance(Token token, String instanceId,
			ComputeManagementClient computeManagementClient) {
		DeploymentOperations deploymentsOperations = computeManagementClient.getDeploymentsOperations();
		DeploymentGetResponse deploymentGetResponse = null;
		try {
			deploymentGetResponse = deploymentsOperations.getByName(instanceId, instanceId);
		} catch (ServiceException e) {
			if (e.getHttpStatusCode() == HttpStatus.SC_NOT_FOUND) {
				LOGGER.error("Deployment " + instanceId + " does not exist.", e);
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			LOGGER.error("Couldn't retrieve deployment " + instanceId + ".", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Couldn't retrieve deployment " + instanceId + ".");
		} catch (Exception e) {
			LOGGER.error("Couldn't retrieve deployment " + instanceId + ".", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Couldn't retrieve deployment " + instanceId + ".");
		}
		
		String deploymentLabel = deploymentGetResponse.getLabel();
		if (deploymentLabel == null || !deploymentLabel.equals(AZURE_VM_DEFAULT_LABEL)) {
			LOGGER.error("Deployment " + instanceId + " does not exist.");
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
		
		return toInstance(deploymentGetResponse, token);
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		try {
			removeInstance(instanceId, computeManagementClient);
		} catch (Exception e) {
			try {
				computeManagementClient.close();
			} catch (IOException e1) {
			}
		}
	}

	private void removeInstance(String instanceId,
			ComputeManagementClient computeManagementClient) {
		HostedServiceOperations hostedServicesOperations = computeManagementClient.getHostedServicesOperations();
		try {
			hostedServicesOperations.deleteAll(instanceId);
		} catch (Exception e) {
			LOGGER.error("Couldn't delete cloud service " + instanceId + ".", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Couldn't delete cloud service " + instanceId + ".");
		}
	}

	@Override
	public void removeInstances(Token token) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		try {
			List<Instance> instances = getInstances(token, computeManagementClient);
			for (Instance instance : instances) {
				removeInstance(instance.getId(), computeManagementClient);
			}
		} finally {
			try {
				computeManagementClient.close();
			} catch (IOException e) {
			}
		}
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		try {
			return getResourcesInfo(token, computeManagementClient);
		} finally {
			try {
				computeManagementClient.close();
			} catch (IOException e) {
			}
		}
	}

	private ResourcesInfo getResourcesInfo(Token token,
			ComputeManagementClient computeManagementClient) {
		List<Instance> instances = getInstances(token, computeManagementClient);
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
		
		URI blobURI = null;
		try {
			blobURI = upload(imagePath, imageName);
		} catch (Exception e) {
			LOGGER.error("Couldn't upload image blob to Azure storage.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Couldn't upload image blob to Azure storage.");
		}
		
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		try {
			registerImage(imageName, blobURI, computeManagementClient);
		} finally {
			try {
				computeManagementClient.close();
			} catch (IOException e) {
			}
		}
	}

	private void registerImage(String imageName, URI blobURI,
			ComputeManagementClient computeManagementClient) {
		VirtualMachineOSImageCreateParameters parameters = new VirtualMachineOSImageCreateParameters();
		parameters.setMediaLinkUri(blobURI);
		parameters.setIsPremium(false);
		parameters.setLabel(imageName);
		parameters.setName(imageName);
		parameters.setOperatingSystemType("Linux");
		
		try {
			computeManagementClient.getVirtualMachineOSImagesOperations().create(parameters);
		} catch (Exception e) {
			LOGGER.error("Couldn't register image " + imageName + ".", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Couldn't register image " + imageName + ".");
		}
	}

	private URI upload(String imagePath, String imageName)
			throws URISyntaxException, StorageException, IOException,
			FileNotFoundException {
		CloudStorageAccount cloudStorageAccount = createStorageAccount();
		CloudBlobClient blobClient = cloudStorageAccount.createCloudBlobClient();
		CloudBlobContainer container = blobClient.getContainerReference(STORAGE_CONTAINER);
		CloudPageBlob blob = container.getPageBlobReference(imageName);
		
		File source = new File(imagePath); 
		blob.upload(new FileInputStream(source), source.length());
		URI blobURI = blob.getQualifiedUri();
		return blobURI;
	}

	@Override
	public String getImageId(Token token, String imageName) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		try {
			return getImageId(imageName, computeManagementClient);
		} finally {
			try {
				computeManagementClient.close();
			} catch (IOException e) {
			}
		}
	}

	private String getImageId(String imageName,
			ComputeManagementClient computeManagementClient) {
		VirtualMachineOSImageGetResponse virtualMachineOSImageGetResponse = null;
		try {
			virtualMachineOSImageGetResponse = 
					computeManagementClient.getVirtualMachineOSImagesOperations().get(imageName);
		} catch (Exception e) {
			LOGGER.error("Couldn't retrieve image " + imageName + ".", e);
			return null;
		}
		return virtualMachineOSImageGetResponse.getName();
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
		createParameters.setLabel(AZURE_VM_DEFAULT_LABEL);

		hostedServiceOperations.create(createParameters);
	}

	protected static Configuration createConfiguration(Token token) {
		try {
			return ManagementConfiguration.configure(new URI(BASE_URL),
					token.get(AzureAttributes.SUBSCRIPTION_ID_KEY),
					token.get(AzureAttributes.KEYSTORE_PATH_KEY),
					token.get(AzureAttributes.KEYSTORE_PASSWORD_KEY),
					KeyStoreType.jks);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Can't create azure configuration");
		}
	}

	protected static ComputeManagementClient createComputeManagementClient(
			Token token) {
		Configuration config = createConfiguration(token);
		ComputeManagementClient computeManagementClient = ComputeManagementService
				.create(config);
		return computeManagementClient;
	}
	
	protected CloudStorageAccount createStorageAccount() {
		if (storageKey == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Storage key is mandatory for Azure blob uploads.");
		}
		try {
			StorageCredentials credentials = StorageCredentials.tryParseCredentials(
					"DefaultEndpointsProtocol=http;"
							+ "AccountName=" + storageAccountName + ";"
							+ "AccountKey=" + storageKey);
			return new CloudStorageAccount(credentials);
		} catch (Exception e) {
			LOGGER.error("Couldn't retrieve account from Azure storage.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					"Couldn't retrieve account from Azure storage.");
		}
	}

	protected static ManagementClient createManagementClient(
			Token token) {
		Configuration config = createConfiguration(token);
		ManagementClient managementClient = ManagementService.create(config);
		return managementClient;
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
		role.setRoleType(PERSISTENT_VM_ROLE);
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
		inputEndpoint.setPort(SSH_PORT);
		inputEndpoint.setEnableDirectServerReturn(false);
		inputEndpoint.setName("SSH");
		inputEndpoint.setProtocol(InputEndpointTransportProtocol.TCP);
		ArrayList<InputEndpoint> endpoints = configurationSet.getInputEndpoints();
		endpoints.add(inputEndpoint);
		configurationSet.setInputEndpoints(endpoints);
		return configurationSet;
	}

	private InstanceState getOCCIStatus(DeploymentStatus status) {
		if (status.equals(DeploymentStatus.RUNNING)) {
			return InstanceState.RUNNING;
		}
		if (status.equals(DeploymentStatus.DELETING)) {
			return InstanceState.FAILED;
		}
		if (status.equals(DeploymentStatus.SUSPENDED)) {
			return InstanceState.SUSPENDED;
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

	private List<Flavor> getFlavors(Token token) {
		if (flavors != null) {
			return flavors;
		}
		flavors = new LinkedList<Flavor>();
		ManagementClient managementClient = createManagementClient(token);
		try {
			updateFlavors(managementClient);
		} finally {
			try {
				managementClient.close();
			} catch (IOException e) {
			}
		}
		return flavors;
	}

	private void updateFlavors(ManagementClient managementClient) {
		RoleSizeOperations roleSizesOperations = managementClient.getRoleSizesOperations();
		RoleSizeListResponse roleSizeListResponse = null;
		try {
			roleSizeListResponse = roleSizesOperations.list();
		} catch (Exception e) {
			LOGGER.error("Couldn't list role sizes.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, "Couldn't list role sizes.");
		}
		
		for (RoleSize roleSize : roleSizeListResponse.getRoleSizes()) {
			String name = roleSize.getName();
			String cpu = String.valueOf(roleSize.getCores());
			String mem = String.valueOf(roleSize.getMemoryInMb());
			String disk = String.valueOf(
					roleSize.getVirtualMachineResourceDiskSizeInMb() / 1024);
			Flavor flavor = new Flavor(name, cpu, mem, disk);
			flavors.add(flavor);
		}
	}

}
