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

import javax.xml.parsers.ParserConfigurationException;

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
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.storage.StorageAttribute;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;
import org.xml.sax.SAXException;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.OperationStatusResponse;
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
import com.microsoft.windowsazure.management.compute.models.DataVirtualHardDisk;
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
import com.microsoft.windowsazure.management.compute.models.VirtualHardDiskHostCaching;
import com.microsoft.windowsazure.management.compute.models.VirtualIPAddress;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateDeploymentParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDataDiskCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineGetResponse;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineOSImageCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineOSImageGetResponse;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;
import com.microsoft.windowsazure.management.models.RoleSizeListResponse;
import com.microsoft.windowsazure.management.models.RoleSizeListResponse.RoleSize;

public class AzureComputePlugin implements ComputePlugin {

	/**
	 * Defines the maximum valid value for Logical Unit Number 
	 * based on Azure doc: https://msdn.microsoft.com/en-us/library/azure/jj157199.aspx
	 */
	private static final int MAX_VALID_LUN_VALUE = 31;

	private static final int SSH_PORT = 22;

	private static final Logger LOGGER = Logger
			.getLogger(AzureComputePlugin.class);

	protected static final String AZURE_VM_DEFAULT_LABEL = "FogbowVM";
	private static final String PERSISTENT_VM_ROLE = "PersistentVMRole";

	protected List<Flavor> flavors;
	private int maxVCPU;
	private int maxRAM;
	private int maxInstances;	

	private String region;
	private String storageAccountName;
	private String storageKey;

	public AzureComputePlugin(Properties properties) {
		this.region = properties.getProperty(
				AzureConfigurationConstants.COMPUTE_AZURE_REGION);
		if (region == null) {
			region = "East US";
		}

		String maxCPUStr = properties.getProperty(
				AzureConfigurationConstants.COMPUTE_AZURE_MAX_VCPU);
		if (maxCPUStr == null) {
			LOGGER.error("Property compute_azure_max_vcpu must be set.");
			throw new IllegalArgumentException(
					"Property compute_azure_max_vcpu must be set.");
		}
		this.maxVCPU = Integer.parseInt(maxCPUStr);

		String maxRAMStr = properties.getProperty(
				AzureConfigurationConstants.COMPUTE_AZURE_MAX_RAM);
		if (maxRAMStr == null) {
			LOGGER.error("Property compute_azure_max_ram must be set.");
			throw new IllegalArgumentException(
					"Property compute_azure_max_ram must be set.");
		}
		this.maxRAM = Integer.parseInt(maxRAMStr);

		String maxInstancesStr = properties
				.getProperty(AzureConfigurationConstants.COMPUTE_AZURE_MAX_INSTANCES);
		if (maxInstancesStr == null) {
			LOGGER.error("Property compute_azure_max_instances must be set.");
			throw new IllegalArgumentException(
					"Property compute_azure_max_instances must be set.");
		}
		this.maxInstances = Integer.parseInt(maxInstancesStr);
		String storageAccountName = properties
				.getProperty(AzureConfigurationConstants.AZURE_STORAGE_ACCOUNT_NAME);
		if (storageAccountName == null) {
			LOGGER.error("Property compute_azure_storage_account_name must be set");
			throw new IllegalArgumentException(
					"Property compute_azure_storage_account_name must be set");
		}
		this.storageAccountName = storageAccountName;
		String storageKey = properties.getProperty(
				AzureConfigurationConstants.AZURE_STORAGE_KEY);
		if (storageKey == null) {
			throw new IllegalArgumentException(
					"Property compute_azure_storage_key must be set");
		}
		this.storageKey = storageKey;
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
	
	protected String getPassword() {
		return UUID.randomUUID().toString();
	}

	private String requestInstance(Token token, Map<String, String> xOCCIAtt,
			String imageId, ComputeManagementClient computeManagementClient) {
		String userName = "fogbow" + (int) (Math.random() * 100000);
		String deploymentName = userName;
		String userpassword = getPassword();
		
		VirtualMachineCreateDeploymentParameters deploymentParameters = new VirtualMachineCreateDeploymentParameters();

		ResourcesInfo resourcesInfo = getResourcesInfo(token, computeManagementClient);
		if (Integer.parseInt(resourcesInfo.getInstancesIdle()) == 0) {
			throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
					ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
		}
		
		Flavor flavor = RequirementsHelper.findSmallestFlavor(
				new LinkedList<Flavor>(getFlavors(token)), 
				xOCCIAtt.get(OrderAttribute.REQUIREMENTS.getValue()));

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
		
		String userData = xOCCIAtt.get(OrderAttribute.USER_DATA_ATT
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
		
		getFlavors(token);
		
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
		response.setStatus(new Status(HttpStatus.SC_BAD_REQUEST),
				ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
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

	protected URI upload(String imagePath, String imageName)
			throws URISyntaxException, StorageException, IOException,
			FileNotFoundException {
		CloudStorageAccount cloudStorageAccount = createStorageAccount();
		CloudBlobClient blobClient = cloudStorageAccount.createCloudBlobClient();
		CloudBlobContainer container = blobClient.getContainerReference(
				AzureConfigurationConstants.AZURE_STORAGE_CONTAINER);
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
			return ManagementConfiguration.configure(new URI(
					AzureConfigurationConstants.AZURE_BASE_URL),
					token.get(AzureAttributes.SUBSCRIPTION_ID_KEY),
					token.get(AzureAttributes.KEYSTORE_PATH_KEY),
					token.get(AzureAttributes.KEYSTORE_PASSWORD_KEY),
					KeyStoreType.jks);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Can't create azure configuration");
		}
	}

	protected ComputeManagementClient createComputeManagementClient(
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

	protected ManagementClient createManagementClient(
			Token token) {
		Configuration config = createConfiguration(token);
		ManagementClient managementClient = ManagementService.create(config);
		return managementClient;
	}

	protected ArrayList<Role> createRoleList(String virtualMachineName,
			String username, String userPassword, String imageID,
			String roleSizeName, String userData,
			ComputeManagementClient computeManagementClient) throws Exception {
		int random = (int) (Math.random() * 100);
		ArrayList<Role> roleList = new ArrayList<Role>();
		Role role = new Role();
		String roleName = virtualMachineName;
		String computerName = virtualMachineName;
		URI mediaLinkUriValue = new URI("http://" + storageAccountName
				+ ".blob.core.windows.net/" + AzureConfigurationConstants.AZURE_STORAGE_CONTAINER + "/"
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

	@Override
	public String attach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		String storageId = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		LOGGER.debug("Trying to attach disk " + storageId + " to VM " + instanceId);
		
		ComputeManagementClient computeManagementClient = 
				createComputeManagementClient(token);
		try {
			VirtualMachineDataDiskCreateParameters parameters = 
					new VirtualMachineDataDiskCreateParameters();
			parameters.setHostCaching(VirtualHardDiskHostCaching.READWRITE);
			parameters.setName(storageId);
			parameters.setMediaLinkUri(new URI(""));
			Integer nextLUN = findNextLUN(instanceId, computeManagementClient);
			parameters.setLogicalUnitNumber(nextLUN);
			
			OperationStatusResponse operationStatusResponse = computeManagementClient
					.getVirtualMachineDisksOperations()
				.createDataDisk(instanceId, instanceId, instanceId, parameters);
			if (operationStatusResponse.getStatusCode() != HttpStatus.SC_OK) {
				throw new OCCIException(ErrorType.BAD_REQUEST, 
						operationStatusResponse.getError().getMessage());
			}
			return UUID.randomUUID().toString();
		} catch (URISyntaxException e) {
			LOGGER.debug("Error while setting the MediaLinkUri in "
					+ "the VirtualMachineDataDiskCreateParameters.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (ParserConfigurationException e) {
			LOGGER.debug("Error while trying to parse configuration.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (SAXException e) {
			LOGGER.debug("Could not attach disk to the virtual machine.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			//ExecutionException, IOException, ServiceException
			LOGGER.debug(e.getMessage(), e);
			throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	private Integer findNextLUN(String instanceId,
			ComputeManagementClient computeManagementClient) throws IOException, ServiceException, 
			ParserConfigurationException, SAXException, URISyntaxException {
		
		VirtualMachineGetResponse vmGetResponse = computeManagementClient
				.getVirtualMachinesOperations().get(instanceId, instanceId, instanceId);
		ArrayList<DataVirtualHardDisk> dataVHDs = vmGetResponse.getDataVirtualHardDisks();
		
		//get lunsInUse
		List<Integer> lunsInUse = new ArrayList<Integer>();
		for (DataVirtualHardDisk dataVHD : dataVHDs) {
			Integer logicalUnitNumber = dataVHD.getLogicalUnitNumber();
			//I'm forcing the LUN to be zero because when the disk is the first one
			//Azure return the value as null instead of zero
			if (logicalUnitNumber == null) {
				logicalUnitNumber = new Integer(0);
			}
			lunsInUse.add(logicalUnitNumber);
		}
		
		//get first free lun in the range of valid LUN values
		for (int i = 0; i <= MAX_VALID_LUN_VALUE; i++) {
			Integer currentLUN = new Integer(i);
			if (!lunsInUse.contains(currentLUN)) {
				return currentLUN;
			}
		}
		return null;
	}

	@Override
	public void dettach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		String storageId = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		LOGGER.debug("Trying to detach disk " + storageId + " from VM " + instanceId);
		ComputeManagementClient computeManagementClient = 
				createComputeManagementClient(token);
		try {
			Integer diskLUN = getDiskLogicalUnitNumber(computeManagementClient, instanceId, storageId);
			if (diskLUN == null) {
				LOGGER.debug("Could not detach disk " + storageId + " from VM " 
						+ instanceId + ". The disk does not exists.");
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND_INSTANCE);
			}
			boolean deleteFromStorage = false;
			//real detach
			OperationStatusResponse deleteDataDiskResponse = computeManagementClient
					.getVirtualMachineDisksOperations().deleteDataDisk(
							instanceId,instanceId, instanceId, 
							diskLUN, 
							deleteFromStorage);
			if (deleteDataDiskResponse.getHttpStatusCode() != HttpStatus.SC_OK) {
				LOGGER.debug("Could not detach disk " + storageId + " from VM " 
						+ instanceId + ". Http code: " + deleteDataDiskResponse.getHttpStatusCode() 
						+ ". " + deleteDataDiskResponse.getError().getMessage());
				throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, 
						deleteDataDiskResponse.getError().getMessage());
			}
			LOGGER.debug("Disk successfully detached.");
		} catch (ParserConfigurationException e) {
			LOGGER.debug("Error while trying to parse azure configuration.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (SAXException e) {
			LOGGER.debug("Error while trying to parse azure configuration.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (URISyntaxException e) {
			LOGGER.debug("Error while trying to parse azure configuration.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			// ExecutionException, IOException, 
			// ServiceException, InterruptedException
			LOGGER.debug("An error occurred while trying to detach disk.", e);
			throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
	
	private Integer getDiskLogicalUnitNumber(
			ComputeManagementClient computeManagementClient, 
			String instanceId, String diskName) throws IOException, ServiceException, 
			ParserConfigurationException, SAXException, URISyntaxException {
		
		VirtualMachineGetResponse vmGetResponse = computeManagementClient
				.getVirtualMachinesOperations()
				.get(instanceId, instanceId, instanceId);
		ArrayList<DataVirtualHardDisk> dataVirtualHardDisks = vmGetResponse.getDataVirtualHardDisks();
		
		for (DataVirtualHardDisk dataVHD : dataVirtualHardDisks) {
			if (dataVHD.getName().equals(diskName)) {
				//I'm forcing the LUN to be zero because when the disk is the first one
				//Azure return the value as null instead of zero
				Integer logicalUnitNumber = dataVHD.getLogicalUnitNumber();
				if (logicalUnitNumber == null) {
					logicalUnitNumber = new Integer(0);
				}
				return logicalUnitNumber;
			}
		}
		return null;
	}

}
