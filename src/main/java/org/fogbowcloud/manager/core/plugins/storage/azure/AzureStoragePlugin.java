package org.fogbowcloud.manager.core.plugins.storage.azure;

import java.io.ByteArrayInputStream;
import java.net.URI;
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
	
	private static final String BASE_URL = "https://management.core.windows.net/";
	private static final String STORAGE_CONTAINER = "vhd-store";
	private static final String DISK_STATUS_AVAILABLE = "available";
	private static final String DISK_STATUS_INUSE = "in_use";
	private static final long ONE_GB_IN_BYTES = 1024 * 1024 * 1024;
	
	private String storageAccountName;
	private String storageKey;
	private String storageConnectionString;
	
	public AzureStoragePlugin(Properties properties) {
		String storageAccountName = properties
				.getProperty("compute_azure_storage_account_name");
		if (storageAccountName == null) {
			LOGGER.error("Property compute_azure_storage_account_name must be set");
			throw new IllegalArgumentException(
					"Property compute_azure_storage_account_name must be set");
		}
		this.storageAccountName = storageAccountName;
		this.storageKey = properties.getProperty("compute_azure_storage_key");
		this.storageConnectionString =
			    "DefaultEndpointsProtocol=http;" +
			    "AccountName=" + this.storageAccountName + ";" +
			    "AccountKey=" + this.storageKey;
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
		if (blob == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Cloud not create VHD blob file in the storage account.");
		}
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
			LOGGER.debug("Could not create instance.", e);
			deleteVHD(diskId);
		}
		return null;
	}
	
	protected CloudBlob createVHD(String size, String diskId) {
		try {
			CloudStorageAccount storageAccount = CloudStorageAccount.parse(this.storageConnectionString);
			CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
			CloudBlobContainer containerReference = blobClient.getContainerReference(STORAGE_CONTAINER);
			containerReference.createIfNotExists();
			CloudPageBlob pageBlob = containerReference.getPageBlobReference(diskId + ".vhd");
			long sizeInBytes = Long.parseLong(size) * ONE_GB_IN_BYTES;
			pageBlob.create(sizeInBytes);
			byte[] vhdFooterArray = VhdFooter.create(sizeInBytes).array();
			pageBlob.uploadPages(new ByteArrayInputStream(vhdFooterArray), sizeInBytes - vhdFooterArray.length, vhdFooterArray.length);
			return pageBlob;
		} catch (Exception e) {
			LOGGER.debug("Could not create VHD blob file in the Azure Storage Account.", e);
		}
		return null;
	}
	
	private void deleteVHD(String diskId) {
		try {
			CloudStorageAccount storageAccount = CloudStorageAccount.parse(this.storageConnectionString);
			CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
			CloudBlobContainer containerReference = blobClient.getContainerReference(STORAGE_CONTAINER);
			CloudPageBlob pageBlob = containerReference.getPageBlobReference(diskId + ".vhd");
			pageBlob.deleteIfExists();
		} catch (Exception e) {
			LOGGER.debug("Could not delete VHD.", e);
		}
	}

	@Override
	public List<Instance> getInstances(Token token) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		List<Instance> instances = new ArrayList<Instance>();
		try {
			VirtualMachineDiskListResponse diskListResponse = computeManagementClient.getVirtualMachineDisksOperations().listDisks();
			ArrayList<VirtualMachineDisk> disks = diskListResponse.getDisks();
			for (VirtualMachineDisk virtualMachineDisk : disks) {
				instances.add(mountInstance(virtualMachineDisk));
			}
		} catch (Exception e) {
			LOGGER.debug("Cloud not list disks.", e);
			e.printStackTrace();
		}
		return instances;
	}
	
	private Instance mountInstance(VirtualMachineDisk disk) {
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
	public void removeInstance(Token token, String instanceId) {
		ComputeManagementClient computeManagementClient = createComputeManagementClient(token);
		
		Instance instance = getInstance(token, instanceId);
		if (instance == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, 
					ResponseConstants.NOT_FOUND_INSTANCE);
		}
		
		try {
			OperationResponse operationResponse = computeManagementClient.getVirtualMachineDisksOperations()
				.deleteDisk(instanceId, true);
			int statusCode = operationResponse.getStatusCode();
			LOGGER.debug("Deleted disk: " + statusCode);
		} catch (Exception e) {
			LOGGER.debug("Could not delete instance " + instanceId, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public void removeInstances(Token token) {
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			removeInstance(token, instance.getId());
		}
	}
}