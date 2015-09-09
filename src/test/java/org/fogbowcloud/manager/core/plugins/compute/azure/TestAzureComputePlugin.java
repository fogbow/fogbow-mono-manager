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
import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse.HostedService;
import com.microsoft.windowsazure.management.compute.models.HostedServiceProperties;
import com.microsoft.windowsazure.management.compute.models.VirtualMachineCreateDeploymentParameters;

public class TestAzureComputePlugin {

	@Test
	public void testRequestInstances() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);
		List<AzureTestInstanceConfigurationSet> instances = new LinkedList<AzureTestInstanceConfigurationSet>();
		instances.add(new AzureTestInstanceConfigurationSet("id1"));
		recordInstances(computeManagementClient, instances);

		VirtualMachineOperations vmOperation = Mockito
				.mock(VirtualMachineOperations.class);
		Mockito.doReturn(vmOperation).when(computeManagementClient)
				.getVirtualMachinesOperations();
		Mockito.doReturn(new OperationStatusResponse()).when(vmOperation)
				.createDeployment(Mockito.anyString(),
						(VirtualMachineCreateDeploymentParameters) Mockito.any(Map.class));
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(AzureAttributes.SUBSCRIPTION_ID_KEY, "subscription_key");
		attributes.put(AzureAttributes.KEYSTORE_PATH_KEY, "/path");
		Token token = new Token("accessId", "user", null, attributes);
		plugin.requestInstance(token, new LinkedList<Category>(), attributes,
				"id");
	}

	@Test
	public void testGetInstances() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instances = new LinkedList<AzureTestInstanceConfigurationSet>();
		instances.add(new AzureTestInstanceConfigurationSet("id1"));
		instances.add(new AzureTestInstanceConfigurationSet("id2"));
		recordInstances(computeManagementClient, instances);

		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(AzureAttributes.SUBSCRIPTION_ID_KEY, "subscription_key");
		attributes.put(AzureAttributes.KEYSTORE_PATH_KEY, "/path");
		List<Instance> instance = plugin.getInstances(new Token("accessId",
				"user", null, attributes));

		Assert.assertEquals(2, instance.size());
	}

	@Test(expected = OCCIException.class)
	public void testGetInstanceNullSubscriptionID() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instances = new LinkedList<AzureTestInstanceConfigurationSet>();
		instances.add(new AzureTestInstanceConfigurationSet("id1"));
		instances.add(new AzureTestInstanceConfigurationSet("id2"));
		recordInstances(computeManagementClient, instances);

		HashMap<String, String> attributes = new HashMap<String, String>();
		plugin.getInstances(new Token("accessId", "user", null, attributes));
	}

	@Test
	public void testGetInstanceWithDifferentLabels() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);

		List<AzureTestInstanceConfigurationSet> instances = new LinkedList<AzureTestInstanceConfigurationSet>();
		instances.add(new AzureTestInstanceConfigurationSet("id1",
				"otherlabel",
				AzureTestInstanceConfigurationSet.DEFAULT_SIZE_NAME));
		instances.add(new AzureTestInstanceConfigurationSet("id2"));
		recordInstances(computeManagementClient, instances);

		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(AzureAttributes.SUBSCRIPTION_ID_KEY, "subscription_key");
		attributes.put(AzureAttributes.KEYSTORE_PATH_KEY, "/path");
		List<Instance> instance = plugin.getInstances(new Token("accessId",
				"user", null, attributes));

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

	private void recordFlavors(AzureComputePlugin azureComputePlugin) {
		Flavor standardD1 = new Flavor("Standard_D1", "1", "3584", new Integer(
				0));
		Flavor extraSmall = new Flavor("ExtraSmall", "1", "784", new Integer(0));
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
			AzureComputePlugin computePlugin) {
		ComputeManagementClient computeManagementClient = Mockito
				.mock(ComputeManagementClient.class);
		Mockito.doReturn(computeManagementClient).when(computePlugin)
				.createComputeManagementClient(Mockito.any(Token.class));
		return computeManagementClient;
	}

	private AzureComputePlugin createAzureComputePlugin() {
		Properties properties = new Properties();
		properties.put("compute_azure_storage_account_name", "ana123");
		properties.put("compute_azure_max_vcpu", "100");
		properties.put("compute_azure_max_ram", "9999999");
		properties.put("compute_azure_max_instances", "50");
		AzureComputePlugin computePlugin = Mockito.spy(new AzureComputePlugin(
				properties));
		return computePlugin;
	}

}
