package org.fogbowcloud.manager.core.plugins.storage.azure;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.core.plugins.compute.azure.AzureConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.util.VhdFooter;
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

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.OperationResponse;
import com.microsoft.windowsazure.core.utils.KeyStoreType;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.ComputeManagementService;
import com.microsoft.windowsazure.management.compute.VirtualMachineDiskOperations;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskCreateResponse;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskListResponse;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskListResponse.VirtualMachineDisk;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskListResponse.VirtualMachineDiskUsageDetails;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;

public class AzureStoragePlugin implements StoragePlugin {
	private static final Logger LOGGER = Logger.getLogger(AzureStoragePlugin.class);
	
	private static final String DISK_STATUS_AVAILABLE = "available";
	private static final String DISK_STATUS_INUSE = "in_use";
	private static final long ONE_GB_IN_BYTES = 1024 * 1024 * 1024;
	
	private String storageAccountName;
	private String storageKey;
	private String storageConnectionString;
	
	public AzureStoragePlugin(Properties properties) {
		String storageAccountName = properties
				.getProperty(AzureConfigurationConstants.AZURE_STORAGE_ACCOUNT_NAME);
		if (storageAccountName == null) {
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
		this.storageConnectionString =
			    "DefaultEndpointsProtocol=http;" +
			    "AccountName=" + this.storageAccountName + ";" +
			    "AccountKey=" + this.storageKey;
	}
	
	private static Configuration createConfiguration(Token token) {
		try {
			return ManagementConfiguration.configure(new URI(
					AzureConfigurationConstants.AZURE_BASE_URL),
					token.get(AzureAttributes.SUBSCRIPTION_ID_KEY),
					token.get(AzureAttributes.KEYSTORE_PATH_KEY),
					token.get(AzureAttributes.KEYSTORE_PASSWORD_KEY),
					KeyStoreType.jks);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Cannot create azure configuration");
		}
	}

	protected ComputeManagementClient createComputeManagementClient(
			Token token) {
		Configuration config = createConfiguration(token);
		ComputeManagementClient computeManagementClient = ComputeManagementService
				.create(config);
		return computeManagementClient;
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		String uuid = UUID.randomUUID().toString();
		String diskId = "fogbow-disk-" + uuid;
		String size = xOCCIAtt.get(OrderAttribute.STORAGE_SIZE.getValue());
		
		CloudBlob blob = createVHD(size, diskId);
		try {
			VirtualMachineDiskCreateParameters parameters = 
					new VirtualMachineDiskCreateParameters();
			parameters.setLabel(diskId);
			parameters.setName(diskId);
			parameters.setMediaLinkUri(blob.getUri());
			VirtualMachineDiskOperations vmDiskOperations = computeManagementClient
					.getVirtualMachineDisksOperations();
			VirtualMachineDiskCreateResponse diskCreateResponse = vmDiskOperations.createDisk(parameters);
			if (diskCreateResponse.getStatusCode() == HttpStatus.SC_OK) {
				return diskId;
			}
		} catch (Exception e) {
			LOGGER.error("Could not create instance.", e);
			deleteVHD(diskId);
		}
		return null;
	}
	
	protected CloudBlob createVHD(String size, String diskId) {
		try {
			CloudStorageAccount storageAccount = CloudStorageAccount.parse(this.storageConnectionString);
			CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
			CloudBlobContainer containerReference = blobClient.getContainerReference(
					AzureConfigurationConstants.AZURE_STORAGE_CONTAINER);
			containerReference.createIfNotExists();
			CloudPageBlob pageBlob = containerReference.getPageBlobReference(diskId + ".vhd");
			long sizeInBytes = Long.parseLong(size) * ONE_GB_IN_BYTES;
			pageBlob.create(sizeInBytes);
			byte[] vhdFooterArray = VhdFooter.create(sizeInBytes).array();
			pageBlob.uploadPages(new ByteArrayInputStream(vhdFooterArray), sizeInBytes - vhdFooterArray.length, vhdFooterArray.length);
			return pageBlob;
		} catch (InvalidKeyException e) {
			LOGGER.error("Could not create the VHD file to use in new storage instance.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (URISyntaxException e) {
			LOGGER.error("Could not create the VHD file to use in new storage instance.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			// StorageException, IOException
			LOGGER.error("Could not create the VHD file to use in new storage instance.", e);
			throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
	
	private boolean deleteVHD(String diskId) {
		try {
			CloudStorageAccount storageAccount = CloudStorageAccount.parse(this.storageConnectionString);
			CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
			CloudBlobContainer containerReference = blobClient.getContainerReference(
					AzureConfigurationConstants.AZURE_STORAGE_CONTAINER);
			CloudPageBlob pageBlob = containerReference.getPageBlobReference(diskId + ".vhd");
			return pageBlob.deleteIfExists();
		} catch (Exception e) {
			LOGGER.error("Cloud not delete the VHD.", e);
		}
		return false;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		List<Instance> instances = new ArrayList<Instance>();
		try {
			VirtualMachineDiskListResponse diskListResponse = computeManagementClient.getVirtualMachineDisksOperations().listDisks();
			ArrayList<VirtualMachineDisk> disks = diskListResponse.getDisks();
			for (VirtualMachineDisk virtualMachineDisk : disks) {
				instances.add(createOCCIInstance(virtualMachineDisk));
			}
		} catch (Exception e) {
			LOGGER.error("Cloud not list disks.", e);
		}
		return instances;
	}
	
	private Instance createOCCIInstance(VirtualMachineDisk disk) {
		String id = disk.getName();
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get(OrderConstants.STORAGE_TERM));
		String diskStatus = DISK_STATUS_AVAILABLE;
		VirtualMachineDiskUsageDetails usageDetails = disk.getUsageDetails();
		if (usageDetails != null && usageDetails.getDeploymentName() != null) {
			diskStatus = DISK_STATUS_INUSE;
		}
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("occi.storage.name", disk.getLabel());
		attributes.put("occi.storage.status", diskStatus);
		attributes.put("occi.storage.size", String.valueOf(disk.getLogicalSizeInGB()));
		attributes.put("occi.core.id", id);
		//FIXME: precisamos adicionar os links? ou seja, getInstance precisar mostrar os attrs de links? 
		return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), null);
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			if (instance.getId().equals(instanceId)) {
				return instance;
			}
		}
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) throws OCCIException {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		
		Instance instance = getInstance(token, instanceId);
		if (instance == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, 
					ResponseConstants.NOT_FOUND_INSTANCE);
		}
		
		try {
			OperationResponse operationResponse = computeManagementClient
					.getVirtualMachineDisksOperations()
					.deleteDisk(instanceId, true);
			int statusCode = operationResponse.getStatusCode();
			LOGGER.debug("Azure disk deleted. Http status code: " + statusCode);
		} catch (Exception e) {
			// IOException, ServiceException
			LOGGER.error("Could not remove storage instance.", e);
			throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public void removeInstances(Token token) {
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			try {
				removeInstance(token, instance.getId());
			} catch (OCCIException e) {
				LOGGER.error("Could not remove storage instance id: " + instance.getId());
			}
		}
	}
}