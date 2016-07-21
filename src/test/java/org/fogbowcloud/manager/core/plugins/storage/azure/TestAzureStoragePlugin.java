package org.fogbowcloud.manager.core.plugins.storage.azure;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.core.plugins.compute.azure.AzureConfigurationConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.microsoft.azure.storage.blob.CloudPageBlob;
import com.microsoft.windowsazure.core.OperationResponse;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.VirtualMachineDiskOperations;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskCreateResponse;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskListResponse;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineDiskListResponse.VirtualMachineDisk;

public class TestAzureStoragePlugin {
	
	private static final String TOKEN_DEFAULT_ACCESS_ID = "accessId";
	private static final String TOKEN_DEFAULT_USERNAME = "token";
	private static final String DEFAULT_INSTANCE_ID = "diskid";
	private static final String DEFAULT_INSTANCE_ID2 = "diskid2";
	private static final String DEFAULT_DISK_SIZE = "1";
	private static final String FAKE_RESOURCE_URI = "http://localhost/fake/resource";

	@Test
	public void testRequestInstances() throws Exception {
		AzureStoragePlugin plugin = createAzureStoragePlugin();
		Token token = createToken(null);
		ComputeManagementClient computeManagementClient = 
				createComputeManagementClient(plugin);

		List<Category> categories = new LinkedList<Category>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), DEFAULT_DISK_SIZE);

		CloudPageBlob pageBlob = new CloudPageBlob(new URI(FAKE_RESOURCE_URI));
		Mockito.doReturn(pageBlob).when(plugin)
			.createVHD(Mockito.anyString(), Mockito.anyString());
		
		VirtualMachineDiskOperations vmDiskOperations = Mockito.mock(
				VirtualMachineDiskOperations.class);
		VirtualMachineDiskCreateParameters parameters = 
				new VirtualMachineDiskCreateParameters();
		parameters.setLabel(DEFAULT_INSTANCE_ID);
		parameters.setName(DEFAULT_INSTANCE_ID);
		parameters.setMediaLinkUri(new URI(FAKE_RESOURCE_URI));
		VirtualMachineDiskCreateResponse vmCreateDiskResponse = 
				Mockito.mock(VirtualMachineDiskCreateResponse.class);
		Mockito.when(vmCreateDiskResponse.getStatusCode())
			.thenReturn(HttpStatus.SC_OK);
		Mockito.when(vmDiskOperations.createDisk(
				Mockito.any(VirtualMachineDiskCreateParameters.class)))
			.thenReturn(vmCreateDiskResponse);
		Mockito.when(computeManagementClient.getVirtualMachineDisksOperations())
			.thenReturn(vmDiskOperations);
		
		plugin.requestInstance(token, categories, xOCCIAtt);
		Mockito.verify(vmDiskOperations).createDisk(
				Mockito.any(VirtualMachineDiskCreateParameters.class));
	}
	
	@Test
	public void testGetInstances() throws Exception {
		AzureStoragePlugin plugin = createAzureStoragePlugin();
		ComputeManagementClient computeManagementClient = 
				createComputeManagementClient(plugin);
		Token token = createToken(null);
		
		VirtualMachineDiskOperations vmDiskOperations = 
				Mockito.mock(VirtualMachineDiskOperations.class);
		
		ArrayList<VirtualMachineDisk> disks = new ArrayList<VirtualMachineDisk>();
		
		VirtualMachineDisk vmDisk = Mockito.mock(VirtualMachineDisk.class);
		disks.add(vmDisk);
		VirtualMachineDisk vmDisk2 = Mockito.mock(VirtualMachineDisk.class);
		disks.add(vmDisk2);
		
		VirtualMachineDiskListResponse vmDiskListResponse = 
				Mockito.mock(VirtualMachineDiskListResponse.class);
		Mockito.when(vmDiskListResponse.getDisks()).thenReturn(disks);
		Mockito.when(vmDiskOperations.listDisks()).thenReturn(vmDiskListResponse);
		Mockito.when(computeManagementClient
				.getVirtualMachineDisksOperations()).thenReturn(vmDiskOperations);
		
		List<Instance> instances = plugin.getInstances(token);
		Assert.assertEquals(2, instances.size());
	}
	
	@Test
	public void testGetInstance() throws Exception {
		AzureStoragePlugin plugin = createAzureStoragePlugin();
		ComputeManagementClient computeManagementClient = 
				createComputeManagementClient(plugin);
		Token token = createToken(null);
		
		VirtualMachineDiskOperations vmDiskOperations = 
				Mockito.mock(VirtualMachineDiskOperations.class);
		
		ArrayList<VirtualMachineDisk> disks = new ArrayList<VirtualMachineDisk>();
		
		VirtualMachineDisk vmDisk = Mockito.mock(VirtualMachineDisk.class);
		Mockito.when(vmDisk.getName()).thenReturn(DEFAULT_INSTANCE_ID);
		disks.add(vmDisk);
		VirtualMachineDisk vmDisk2 = Mockito.mock(VirtualMachineDisk.class);
		Mockito.when(vmDisk2.getName()).thenReturn(DEFAULT_INSTANCE_ID2);
		disks.add(vmDisk2);
		
		VirtualMachineDiskListResponse vmDiskListResponse = 
				Mockito.mock(VirtualMachineDiskListResponse.class);
		Mockito.when(vmDiskListResponse.getDisks()).thenReturn(disks);
		Mockito.when(vmDiskOperations.listDisks()).thenReturn(vmDiskListResponse);
		Mockito.when(computeManagementClient
				.getVirtualMachineDisksOperations()).thenReturn(vmDiskOperations);
		
		Instance instance = plugin.getInstance(token, DEFAULT_INSTANCE_ID);
		Assert.assertEquals(DEFAULT_INSTANCE_ID, instance.getId());
	}
	
	@Test
	public void testRemoveInstances() throws Exception {
		AzureStoragePlugin plugin = createAzureStoragePlugin();
		ComputeManagementClient computeManagementClient = 
				createComputeManagementClient(plugin);
		Token token = createToken(null);
		
		VirtualMachineDiskOperations vmDiskOperations = 
				Mockito.mock(VirtualMachineDiskOperations.class);
		
		ArrayList<VirtualMachineDisk> disks = new ArrayList<VirtualMachineDisk>();
		
		VirtualMachineDisk vmDisk = Mockito.mock(VirtualMachineDisk.class);
		Mockito.when(vmDisk.getName()).thenReturn(DEFAULT_INSTANCE_ID);
		disks.add(vmDisk);
		VirtualMachineDisk vmDisk2 = Mockito.mock(VirtualMachineDisk.class);
		Mockito.when(vmDisk2.getName()).thenReturn(DEFAULT_INSTANCE_ID2);
		disks.add(vmDisk2);
		
		VirtualMachineDiskListResponse vmDiskListResponse = 
				Mockito.mock(VirtualMachineDiskListResponse.class);
		Mockito.when(vmDiskListResponse.getDisks()).thenReturn(disks);
		Mockito.when(vmDiskOperations.listDisks()).thenReturn(vmDiskListResponse);
		
		OperationResponse vmDiskDeleteResponse = Mockito.mock(OperationResponse.class);
		Mockito.when(vmDiskDeleteResponse.getStatusCode()).thenReturn(HttpStatus.SC_OK);
		Mockito.when(vmDiskOperations.deleteDisk(Mockito.anyString(), 
				Mockito.anyBoolean())).thenReturn(vmDiskDeleteResponse);
		
		Mockito.when(computeManagementClient
				.getVirtualMachineDisksOperations()).thenReturn(vmDiskOperations);
		
		plugin.removeInstances(token);
		Mockito.verify(vmDiskOperations, Mockito.times(2))
			.deleteDisk(Mockito.anyString(), Mockito.anyBoolean());
	}
	
	@Test
	public void testRemoveOneInstance() throws Exception {
		AzureStoragePlugin plugin = createAzureStoragePlugin();
		ComputeManagementClient computeManagementClient = 
				createComputeManagementClient(plugin);
		Token token = createToken(null);
		
		VirtualMachineDiskOperations vmDiskOperations = 
				Mockito.mock(VirtualMachineDiskOperations.class);
		
		ArrayList<VirtualMachineDisk> disks = new ArrayList<VirtualMachineDisk>();
		
		VirtualMachineDisk vmDisk = Mockito.mock(VirtualMachineDisk.class);
		Mockito.when(vmDisk.getName()).thenReturn(DEFAULT_INSTANCE_ID);
		disks.add(vmDisk);
		VirtualMachineDisk vmDisk2 = Mockito.mock(VirtualMachineDisk.class);
		Mockito.when(vmDisk2.getName()).thenReturn(DEFAULT_INSTANCE_ID2);
		disks.add(vmDisk2);
		
		VirtualMachineDiskListResponse vmDiskListResponse = 
				Mockito.mock(VirtualMachineDiskListResponse.class);
		Mockito.when(vmDiskListResponse.getDisks()).thenReturn(disks);
		Mockito.when(vmDiskOperations.listDisks()).thenReturn(vmDiskListResponse);
		
		OperationResponse vmDiskDeleteResponse = Mockito.mock(OperationResponse.class);
		Mockito.when(vmDiskDeleteResponse.getStatusCode()).thenReturn(HttpStatus.SC_OK);
		Mockito.when(vmDiskOperations.deleteDisk(Mockito.anyString(), 
				Mockito.anyBoolean())).thenReturn(vmDiskDeleteResponse);
		
		Mockito.when(computeManagementClient
				.getVirtualMachineDisksOperations()).thenReturn(vmDiskOperations);
		
		plugin.removeInstance(token, DEFAULT_INSTANCE_ID);
		Mockito.verify(vmDiskOperations)
			.deleteDisk(Mockito.anyString(), Mockito.anyBoolean());
	}
	
	private AzureStoragePlugin createAzureStoragePlugin() {
		Properties properties = new Properties();
		properties.put(AzureConfigurationConstants
				.AZURE_STORAGE_ACCOUNT_NAME, "ana123");
		properties.put(AzureConfigurationConstants
				.AZURE_STORAGE_KEY, "a2V5MTIz");
		AzureStoragePlugin storagePlugin = Mockito.spy(
				new AzureStoragePlugin(properties));
		return storagePlugin;
	}
	
	private ComputeManagementClient createComputeManagementClient(
			AzureStoragePlugin storagePlugin) throws Exception {
		ComputeManagementClient computeManagementClient = Mockito
				.mock(ComputeManagementClient.class);
		Mockito.doReturn(computeManagementClient).when(storagePlugin)
				.createComputeManagementClient(Mockito.any(Token.class));
		
		return computeManagementClient;
	}
	
	private Token createToken(Map<String, String> extraAttributes) {
		HashMap<String, String> attributes = new HashMap<String, String>();
		if (extraAttributes != null) {
			attributes.putAll(extraAttributes);
		}
		attributes.put(AzureAttributes.SUBSCRIPTION_ID_KEY, "subscription_key");
		attributes.put(AzureAttributes.KEYSTORE_PATH_KEY, "/path");
		return new Token(TOKEN_DEFAULT_ACCESS_ID, TOKEN_DEFAULT_USERNAME, null,
				attributes);
	}
}