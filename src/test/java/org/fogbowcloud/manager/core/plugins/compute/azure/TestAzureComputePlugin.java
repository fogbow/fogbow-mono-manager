package org.fogbowcloud.manager.core.plugins.compute.azure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.microsoft.windowsazure.management.ManagementClient;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.DeploymentOperations;
import com.microsoft.windowsazure.management.compute.HostedServiceOperations;
import com.microsoft.windowsazure.management.compute.models.DeploymentGetResponse;
import com.microsoft.windowsazure.management.compute.models.DeploymentStatus;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse;
import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse.HostedService;
import com.microsoft.windowsazure.management.compute.models.HostedServiceProperties;

public class TestAzureComputePlugin {

	private static final String DEFAULT_TEST_FLAVOR_NAME = "Standard_D1";

	@Test
	public void testGetInstances() throws Exception {
		AzureComputePlugin plugin = createAzureComputePlugin();
		ComputeManagementClient computeManagementClient = createComputeManagementClient(plugin);
		recordFlavors(plugin);
		recordInstances(computeManagementClient, "id1", "id2");

		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(AzureAttributes.SUBSCRIPTION_ID_KEY, "subscription_key");
		attributes.put(AzureAttributes.KEYSTORE_PATH_KEY, "/path");
		List<Instance> instance = plugin.getInstances(new Token("accessId",
				"user", null, attributes));

		Assert.assertEquals(2, instance.size());
	}

	private void recordInstances(
			ComputeManagementClient computeManagementClient,
			String... instanceIds) throws Exception {
		ArrayList<HostedService> deploymentResponse = new ArrayList<HostedService>();
		DeploymentOperations deploymentsOperations = Mockito
				.mock(DeploymentOperations.class);

		for (String instanceId : instanceIds) {
			HostedService hostedService = new HostedService();
			hostedService.setServiceName(instanceId);
			HostedServiceProperties properties = new HostedServiceProperties();
			properties.setLabel(AzureComputePlugin.AZURE_VM_DEFAULT_LABEL);
			hostedService.setProperties(properties);
			deploymentResponse.add(hostedService);

			DeploymentGetResponse deployment = new DeploymentGetResponse();
			RoleInstance roleInstance = new RoleInstance();
			roleInstance.setInstanceSize(DEFAULT_TEST_FLAVOR_NAME);
			ArrayList<RoleInstance> roleInstances = new ArrayList<RoleInstance>();
			roleInstances.add(roleInstance);
			deployment.setRoleInstances(roleInstances);
			deployment.setName(instanceId);
			deployment.setLabel(AzureComputePlugin.AZURE_VM_DEFAULT_LABEL);
			deployment.setStatus(DeploymentStatus.RUNNING);
			Mockito.doReturn(deployment).when(deploymentsOperations)
					.getByName(instanceId, instanceId);
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
		Flavor flavor = new Flavor(DEFAULT_TEST_FLAVOR_NAME, "1", "3584",
				new Integer(0));
		ArrayList<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(flavor);
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
