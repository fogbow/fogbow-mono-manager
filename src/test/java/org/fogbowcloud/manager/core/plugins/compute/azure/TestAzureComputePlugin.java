package org.fogbowcloud.manager.core.plugins.compute.azure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.ws.rs.core.UriBuilder;

import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.DeploymentOperations;
import com.microsoft.windowsazure.management.compute.HostedServiceOperations;
import com.microsoft.windowsazure.management.compute.VirtualMachineOSImageOperations;
import com.microsoft.windowsazure.management.compute.VirtualMachineOperations;
import com.microsoft.windowsazure.management.compute.models.DeploymentGetResponse;
import com.microsoft.windowsazure.management.compute.models.DeploymentStatus;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse.HostedService;
import com.microsoft.windowsazure.management.compute.models.HostedServiceProperties;
import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateDeploymentParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineOSImageCreateParameters;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineOSImageGetResponse;

public class TestAzureComputePlugin {

	private static final String FLAVOR_NAME_STANDARD_D1 = "Standard_D1";
	protected static final String FLAVOR_NAME_EXTRA_SMALL = "ExtraSmall";

	private static final String VM_DEFAULT_PASSWORD = "password";
	private static final String VM_DEFAULT_PREFIX = "fogbow";
	private static final String VM_DEFAULT_ID_1 = "id1";
	private static final String VM_DEFAULT_ID_2 = "id2";

	private static final String TOKEN_DEFAULT_ACCESS_ID = "accessId";
	private static final String TOKEN_DEFAULT_USERNAME = "token";
	
	@Test(expected=IllegalArgumentException.class)
	public void testConstructorWithoutMaxVCPU() {
		Properties properties = new Properties();
		properties.setProperty("compute_azure_max_ram", "1024");
		properties.setProperty("compute_azure_max_instances", "1");
		new AzureComputePlugin(properties);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testConstructorWithoutMaxRAM() {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_azure_vcpu", "1");
		properties.setProperty("compute_ec2_azure_instances", "1");
		new AzureComputePlugin(properties);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testConstructorWithoutMaxInstances() {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_azure_vcpu", "1");
		properties.setProperty("compute_ec2_azure_ram", "1024");
		new AzureComputePlugin(properties);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testBypass() {
		AzureComputePlugin plugin = createAzureComputePlugin();
		plugin.bypass(null, null);
	}

	private static final String UPLOAD_URI = "http://URI";
	private static final String IMAGE_NAME = "name";

	@Test
	public void testGetImageId() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);

		plugin.getImageId(createToken(null), IMAGE_NAME);
		Mockito.verify(
				computeManagementClient.getVirtualMachineOSImagesOperations())
				.get(IMAGE_NAME);
	}

	@Test
	public void testUploadImage() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		UriBuilder builder = UriBuilder.fromPath(UPLOAD_URI);
		Mockito.doReturn(builder.build()).when(plugin)
				.upload(UPLOAD_URI, IMAGE_NAME);

		plugin.uploadImage(createToken(null), UPLOAD_URI, IMAGE_NAME, null);
		Mockito.verify(
				computeManagementClient.getVirtualMachineOSImagesOperations())
				.create(Mockito
						.any(VirtualMachineOSImageCreateParameters.class));
	}

	@Test
	public void testGetImageState() {
		AzureComputePlugin plugin = createAzureComputePlugin();
		Assert.assertEquals(ImageState.ACTIVE,
				plugin.getImageState(createToken(null), IMAGE_NAME));
	}

	@Test
	public void testRequestInstances() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);
		Mockito.doReturn(VM_DEFAULT_PASSWORD).when(plugin).getPassword();
		List<AzureTestInstanceConfigurationSet> instances = createDefaultInstances();
		recordInstances(computeManagementClient, instances);

		Token token = createToken(null);
		String imageName = plugin.requestInstance(token,
				new LinkedList<Category>(), new HashMap<String, String>(),
				VM_DEFAULT_ID_1);
		Assert.assertTrue(imageName.contains(VM_DEFAULT_PREFIX));
		Mockito.verify(plugin).createRoleList(imageName, imageName,
				VM_DEFAULT_PASSWORD, VM_DEFAULT_ID_1, FLAVOR_NAME_EXTRA_SMALL,
				null, computeManagementClient);
	}
	
	@Test
	public void testRequestInstanceWithUserData() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);
		Mockito.doReturn(VM_DEFAULT_PASSWORD).when(plugin).getPassword();
		List<AzureTestInstanceConfigurationSet> instances = createDefaultInstances();
		recordInstances(computeManagementClient, instances);

		Token token = createToken(null);
		String userData = UUID.randomUUID().toString();
		HashMap<String, String> occiAtt = new HashMap<String, String>();
		occiAtt.put(OrderAttribute.USER_DATA_ATT.getValue(), userData);
		String imageName = plugin.requestInstance(token,
				new LinkedList<Category>(), occiAtt,
				VM_DEFAULT_ID_1);
		Assert.assertTrue(imageName.contains(VM_DEFAULT_PREFIX));
		Mockito.verify(plugin).createRoleList(imageName, imageName,
				VM_DEFAULT_PASSWORD, VM_DEFAULT_ID_1, FLAVOR_NAME_EXTRA_SMALL,
				userData, computeManagementClient);
	}

	@Test(expected = OCCIException.class)
	public void testRequestInstanceMaxInstancesExceeded() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instances = createDefaultInstances(
				VM_DEFAULT_ID_1, VM_DEFAULT_ID_2);
		recordInstances(computeManagementClient, instances);

		plugin.requestInstance(createToken(null), new LinkedList<Category>(),
				new HashMap<String, String>(), VM_DEFAULT_ID_1);
	}

	@Test(expected = OCCIException.class)
	public void testRequestInstanceMemoryExceeded() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instances = createDefaultInstances();
		instances.add(new AzureTestInstanceConfigurationSet(VM_DEFAULT_ID_1,
				AzureComputePlugin.AZURE_VM_DEFAULT_LABEL,
				FLAVOR_NAME_STANDARD_D1));
		recordInstances(computeManagementClient, instances);

		plugin.requestInstance(createToken(null), new LinkedList<Category>(),
				new HashMap<String, String>(), VM_DEFAULT_ID_1);
	}

	@Test
	public void testGetInstances() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		recordInstances(computeManagementClient,
				createDefaultInstances(VM_DEFAULT_ID_1, VM_DEFAULT_ID_2));
		List<Instance> instance = plugin.getInstances(createToken(null));

		Assert.assertEquals(2, instance.size());
	}

	@Test(expected = OCCIException.class)
	public void testGetInstanceNullSubscriptionID() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);
		recordInstances(computeManagementClient,
				createDefaultInstances(VM_DEFAULT_ID_1, VM_DEFAULT_ID_2));

		plugin.getInstances(new Token(TOKEN_DEFAULT_ACCESS_ID,
				TOKEN_DEFAULT_USERNAME, null, new HashMap<String, String>()));
	}

	@Test
	public void testRemoveInstance() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instancesConfiguration = createDefaultInstances(
				VM_DEFAULT_ID_1, VM_DEFAULT_ID_2);
		recordInstances(computeManagementClient, instancesConfiguration);

		plugin.removeInstance(createToken(null), VM_DEFAULT_ID_1);
		Mockito.verify(computeManagementClient.getHostedServicesOperations())
				.deleteAll(VM_DEFAULT_ID_1);

	}

	@Test
	public void testRemoveInstances() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instancesConfiguration = createDefaultInstances(
				VM_DEFAULT_ID_1, VM_DEFAULT_ID_2);
		recordInstances(computeManagementClient, instancesConfiguration);

		plugin.removeInstances(createToken(null));
		Mockito.verify(computeManagementClient.getHostedServicesOperations())
				.deleteAll(VM_DEFAULT_ID_1);
		Mockito.verify(computeManagementClient.getHostedServicesOperations())
				.deleteAll(VM_DEFAULT_ID_2);
	}

	private static final String DIFFERENT_LABEL = "otherlabel";

	@Test
	public void testGetInstanceWithDifferentLabels() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instances = new LinkedList<AzureTestInstanceConfigurationSet>();
		instances.add(new AzureTestInstanceConfigurationSet(VM_DEFAULT_ID_1,
				DIFFERENT_LABEL, FLAVOR_NAME_EXTRA_SMALL));
		instances.add(new AzureTestInstanceConfigurationSet(VM_DEFAULT_ID_2));
		recordInstances(computeManagementClient, instances);

		List<Instance> instance = plugin.getInstances(createToken(null));

		Assert.assertEquals(1, instance.size());
	}

	private void recordInstances(
			ComputeManagementClient computeManagementClient,
			List<AzureTestInstanceConfigurationSet> instances) throws Exception {
		ArrayList<HostedService> deploymentResponse = new ArrayList<HostedService>();
		DeploymentOperations deploymentsOperations = Mockito
				.mock(DeploymentOperations.class);
		for (AzureTestInstanceConfigurationSet instance : instances) {
			HostedService hostedService = new HostedService();
			hostedService.setServiceName(instance.getId());
			HostedServiceProperties properties = new HostedServiceProperties();
			properties.setLabel(instance.getLabel());
			hostedService.setProperties(properties);
			deploymentResponse.add(hostedService);

			DeploymentGetResponse deployment = new DeploymentGetResponse();
			RoleInstance roleInstance = new RoleInstance();
			roleInstance.setInstanceSize(instance.getSizeName());
			ArrayList<RoleInstance> roleInstances = new ArrayList<RoleInstance>();
			roleInstances.add(roleInstance);
			deployment.setRoleInstances(roleInstances);
			deployment.setName(instance.getId());
			deployment.setLabel(instance.getLabel());
			deployment.setStatus(DeploymentStatus.RUNNING);
			Mockito.doReturn(deployment).when(deploymentsOperations)
					.getByName(instance.getId(), instance.getId());
		}
		HostedServiceOperations hostedServiceOperation = Mockito
				.mock(HostedServiceOperations.class);
		Mockito.doReturn(hostedServiceOperation).when(computeManagementClient)
				.getHostedServicesOperations();

		HostedServiceListResponse response = new HostedServiceListResponse();
		response.setHostedServices(deploymentResponse);

		Mockito.doReturn(response).when(hostedServiceOperation).list();
		Mockito.doReturn(deploymentsOperations).when(computeManagementClient)
				.getDeploymentsOperations();
	}

	private List<AzureTestInstanceConfigurationSet> createDefaultInstances(
			String... instanceIDs) {
		List<AzureTestInstanceConfigurationSet> instances = new LinkedList<AzureTestInstanceConfigurationSet>();
		for (String instanceID : instanceIDs) {
			instances.add(new AzureTestInstanceConfigurationSet(instanceID));
		}
		return instances;
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

	private void recordFlavors(AzureComputePlugin azureComputePlugin) {
		Flavor standardD1 = new Flavor(FLAVOR_NAME_STANDARD_D1, "2", "3584",
				new Integer(0));
		Flavor extraSmall = new Flavor(FLAVOR_NAME_EXTRA_SMALL, "1", "784",
				new Integer(0));
		ArrayList<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(standardD1);
		flavors.add(extraSmall);
		azureComputePlugin.flavors = flavors;
	}

	private static final String IMAGE_ID = "randomID";

	private ComputeManagementClient createComputeManagementClient(
			AzureComputePlugin computePlugin) throws Exception {
		ComputeManagementClient computeManagementClient = Mockito
				.mock(ComputeManagementClient.class);
		Mockito.doReturn(computeManagementClient).when(computePlugin)
				.createComputeManagementClient(Mockito.any(Token.class));
		
		VirtualMachineOperations vmOperation = Mockito
				.mock(VirtualMachineOperations.class);
		Mockito.doReturn(vmOperation).when(computeManagementClient)
				.getVirtualMachinesOperations();
		Mockito.doReturn(new OperationStatusResponse())
				.when(vmOperation)
				.createDeployment(
						Mockito.anyString(),
						(VirtualMachineCreateDeploymentParameters) Mockito
								.any(Map.class));
		
		HostedServiceOperations hostedServiceOperations = Mockito
				.mock(HostedServiceOperations.class);
		Mockito.doReturn(hostedServiceOperations).when(computeManagementClient)
				.getHostedServicesOperations();
		
		VirtualMachineOSImageOperations VMImageOperations = Mockito
				.mock(VirtualMachineOSImageOperations.class);
		VirtualMachineOSImageGetResponse VMImageResponse = Mockito.mock(VirtualMachineOSImageGetResponse.class);
		Mockito.doReturn(IMAGE_ID).when(VMImageResponse).getName();
		Mockito.doReturn(VMImageResponse).when(VMImageOperations).get(Mockito.anyString());
		Mockito.doReturn(VMImageOperations).when(computeManagementClient)
				.getVirtualMachineOSImagesOperations();
		return computeManagementClient;
	}

	private AzureComputePlugin createAzureComputePlugin() {
		Properties properties = new Properties();
		properties.put("compute_azure_storage_account_name", "ana123");
		properties.put("compute_azure_max_vcpu", "3");
		properties.put("compute_azure_max_ram", "3500");
		properties.put("compute_azure_max_instances", "2");
		AzureComputePlugin computePlugin = Mockito.spy(new AzureComputePlugin(
				properties));
		return computePlugin;
	}

}
