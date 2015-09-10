package org.fogbowcloud.manager.core.plugins.compute.azure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.management.ManagementClient;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.DeploymentOperations;
import com.microsoft.windowsazure.management.compute.HostedServiceOperations;
import com.microsoft.windowsazure.management.compute.VirtualMachineOperations;
import com.microsoft.windowsazure.management.compute.models.DeploymentGetResponse;
import com.microsoft.windowsazure.management.compute.models.DeploymentStatus;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse.HostedService;
import com.microsoft.windowsazure.management.compute.models.HostedServiceProperties;
import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateDeploymentParameters;

public class TestAzureComputePlugin {

	private static final String FLAVOR_NAME_STANDARD_D1 = "Standard_D1";
	protected static final String FLAVOR_NAME_EXTRA_SMALL = "ExtraSmall";

	private static final String VM_DEFAULT_PASSWORD = "password";
	private static final String VM_DEFAULT_PREFIX = "fogbow";
	private static final String VM_DEFAULT_ID_1 = "id1";
	private static final String VM_DEFAULT_ID_2 = "id2";

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

	@Test(expected = OCCIException.class)
	public void testRequestInstanceMaxInstancesExceeded() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);
		List<AzureTestInstanceConfigurationSet> instances = 
				createDefaultInstances(VM_DEFAULT_ID_1, VM_DEFAULT_ID_2);
		recordInstances(computeManagementClient, instances);
		plugin.requestInstance(createToken(null),
				new LinkedList<Category>(), new HashMap<String, String>(),
				VM_DEFAULT_ID_1);
	}

	@Test(expected = OCCIException.class)
	public void testRequestInstanceMemoryExceeded() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);
		List<AzureTestInstanceConfigurationSet> instances = createDefaultInstances();
		instances.add(new AzureTestInstanceConfigurationSet
				(VM_DEFAULT_ID_1, AzureComputePlugin.AZURE_VM_DEFAULT_LABEL, FLAVOR_NAME_STANDARD_D1));
		recordInstances(computeManagementClient, instances);
		plugin.requestInstance(createToken(null),
				new LinkedList<Category>(), new HashMap<String, String>(),
				VM_DEFAULT_ID_1);
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

		plugin.getInstances(new Token("token", "user", null,
				new HashMap<String, String>()));
	}

	@Test
	public void testGetInstanceWithDifferentLabels() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instances = new LinkedList<AzureTestInstanceConfigurationSet>();
		instances.add(new AzureTestInstanceConfigurationSet(VM_DEFAULT_ID_1,
				"otherlabel", FLAVOR_NAME_EXTRA_SMALL));
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
		return new Token("accessId", "user", null, attributes);
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

	private ManagementClient createManagementClient(
			AzureComputePlugin computePlugin) {
		ManagementClient managementClient = Mockito
				.mock(ManagementClient.class);
		Mockito.doReturn(managementClient).when(computePlugin)
				.createManagementClient(Mockito.any(Token.class));
		return managementClient;
	}

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
