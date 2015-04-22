package org.fogbowcloud.manager.occi.plugins.openstack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.openstack.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackNovaV2ComputePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.NovaV2ComputeApplication;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNovaV2ComputeOpenStack {

	private static final String FIRST_INSTANCE_ID = "0";
	private static final String SECOND_INSTANCE_ID = "1";
	private PluginHelper pluginHelper;
	private OpenStackNovaV2ComputePlugin novaV2ComputeOpenStack;
	private Token defaultToken;
	private NovaV2ComputeApplication novaV2Server;
	
	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put(OpenStackConfigurationConstants.COMPUTE_NOVAV2_URL_KEY, PluginHelper.COMPUTE_NOVAV2_URL);
		properties.put(OpenStackConfigurationConstants.COMPUTE_NOVAV2_IMAGE_PREFIX_KEY
				+ PluginHelper.LINUX_X86_TERM, "imageid");
		
		novaV2ComputeOpenStack = new OpenStackNovaV2ComputePlugin(properties);
		
		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(RequestConstants.SMALL_TERM, "1", "1000", "10");
		flavorSmall.setId(SECOND_INSTANCE_ID);
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		novaV2ComputeOpenStack.setFlavors(flavors );
		
		HashMap<String, String> tokenAtt = new HashMap<String, String>();
		tokenAtt.put(KeystoneIdentityPlugin.TENANT_ID, "tenantid");
		tokenAtt.put(KeystoneIdentityPlugin.TENANT_NAME, "tenantname");
		defaultToken = new Token(PluginHelper.ACCESS_ID, PluginHelper.USERNAME,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, tokenAtt);
	
		pluginHelper = new PluginHelper();	
		novaV2Server = pluginHelper.initializeNovaV2ComputeComponent("src/test/resources/openstack");
	}
	
	@After
	public void tearDown() throws Exception {
		pluginHelper.disconnectComponent();
	}
	
	@Test
	public void testRequestOneInstance(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));
		
		Assert.assertEquals(1, novaV2ComputeOpenStack.getInstances(defaultToken).size());
	}
	
	@Test
	public void testRequestExceedQuota(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		//requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		
		for (int i = 0; i < NovaV2ComputeApplication.MAX_INSTANCE_COUNT; i++) {
			novaV2ComputeOpenStack.requestInstance(
					defaultToken, new LinkedList<Category>(categories), new HashMap<String, String>(), 
					PluginHelper.LINUX_X86_TERM);
		}
		
		try {
			novaV2ComputeOpenStack.requestInstance(
					defaultToken, new LinkedList<Category>(categories), new HashMap<String, String>(), 
					PluginHelper.LINUX_X86_TERM);
			Assert.fail();
		} catch (OCCIException e) {
			Assert.assertEquals(HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE, e.getStatus().getCode());
		}
	}
	
	@Test
	public void testRequestFailsKeyPairDeleted() {
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		//requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		
		try {
			//Last request will fail
			for (int i = 0; i < NovaV2ComputeApplication.MAX_INSTANCE_COUNT + 1; i++) {
				HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
				xOCCIAtt.put(RequestAttribute.DATA_PUBLIC_KEY.getValue(), "public key data");
				novaV2ComputeOpenStack.requestInstance(
						defaultToken, new LinkedList<Category>(categories), xOCCIAtt, 
						PluginHelper.LINUX_X86_TERM);
			}
		} catch (Exception e) {
			// A failure is actually expected
		}
		
		Assert.assertEquals(0, novaV2Server.getPublicKeys().size());
	}
	
	@Test
	public void testRequestOneInstanceWithPublicKey(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.DATA_PUBLIC_KEY.getValue(), "public key data");
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), xOCCIAtt, PluginHelper.LINUX_X86_TERM));
		
		Assert.assertEquals(1, novaV2ComputeOpenStack.getInstances(defaultToken).size());
	}
	
	@Test
	public void testGetInstances(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());	
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), new HashMap<String, String>(), 
				PluginHelper.LINUX_X86_TERM));

		// check getting all instance ids
		List<Instance> instances = novaV2ComputeOpenStack.getInstances(defaultToken); 
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(FIRST_INSTANCE_ID, instances.get(0).getId());
	}

	@Test
	public void testGetInstances2(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		Map<String, String> mapAttr = new HashMap<String, String>();
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " >= 10 && " + mem + " > 500 && " + vCpu + " > 0";
		mapAttr.put(RequestAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), mapAttr, PluginHelper.LINUX_X86_TERM));
		Assert.assertEquals(SECOND_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), mapAttr, PluginHelper.LINUX_X86_TERM));

		// check getting all instance ids
		List<Instance> instances = novaV2ComputeOpenStack.getInstances(defaultToken); 
		Assert.assertEquals(2, instances.size());
		for (Instance instance : instances) {
			Assert.assertTrue(FIRST_INSTANCE_ID.equals(instance.getId())
					|| SECOND_INSTANCE_ID.equals(instance.getId()));
		}
	}
	
	@Test
	public void testGetInstance(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());		
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));

		// check getting all instance ids
		Instance instance = novaV2ComputeOpenStack.getInstance(defaultToken, FIRST_INSTANCE_ID); 
		Assert.assertEquals(FIRST_INSTANCE_ID, instance.getId());
		Map<String, String> attributes = instance.getAttributes();
		Assert.assertEquals("active", attributes.get("occi.compute.state"));
		Assert.assertEquals("0.5", attributes.get("occi.compute.memory"));
		Assert.assertEquals("1", attributes.get("occi.compute.cores"));
		Assert.assertEquals("test2", attributes.get("occi.compute.hostname"));
		Assert.assertEquals("Not defined", attributes.get("occi.compute.architecture"));
		Assert.assertEquals("Not defined", attributes.get("occi.compute.speed"));
		Assert.assertEquals(FIRST_INSTANCE_ID, attributes.get("occi.core.id"));
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.generateFlavorResource(RequestConstants.SMALL_TERM));
		
		for (Resource resource : resources) {
			Assert.assertTrue(instance.getResources().contains(resource));		
		}
	}
	
	@Test
	public void testRemoveInstance(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));
		Assert.assertEquals(1, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		// removing one instance
		novaV2ComputeOpenStack.removeInstance(defaultToken, FIRST_INSTANCE_ID);
		
		// checking if instance was removed
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());		
	}
	
	@Test
	public void testRemoveInstances(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		//requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, categories, new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));
		Assert.assertEquals(SECOND_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, categories, new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));

		Assert.assertEquals(2, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		// removing one instance
		novaV2ComputeOpenStack.removeInstances(defaultToken);
		
		// checking if instance was removed
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());		
	}
	
	@Test
	public void testCapacity() {
		ResourcesInfo resourcesInfo = novaV2ComputeOpenStack.getResourcesInfo(defaultToken);
		List<Flavor> flavors = resourcesInfo.getFlavors();
		// Limited by instance count
		Assert.assertEquals(2, (int) flavors.get(0).getCapacity());
		Assert.assertEquals(2, (int) flavors.get(1).getCapacity());
		Assert.assertEquals(2, (int) flavors.get(2).getCapacity());
	}
	
	@Test
	public void testUpdateFlavor() throws HttpException, IOException {
		HttpClient client = Mockito.mock(HttpClient.class);
		String nameOne = "nameOne";
		String nameTwo = "nameTwo";
		String idTwo = "idTwo";
		String jsonAllFlavors = "{\"flavors\": [{\"id\": \"1\", \"name\": \"" + nameOne
				+ "\"} , {\"id\": \"" + idTwo + "\", \"name\": \"" + nameTwo + "\"}]}";
		ByteArrayEntity entityAllFlavors = new ByteArrayEntity(jsonAllFlavors.getBytes());
		String jsonFlavorTwo = "{\"flavor\": {\"name\": \"" + nameTwo  + "\", \"ram\": 512, \"vcpus\": 1, \"disk\": 1, \"id\": \"1\"}}";
		ProtocolVersion protocolVersion = new ProtocolVersion("", 0, 1);
		StatusLine statusLine = new BasicStatusLine(protocolVersion, 200, "");
		HttpResponse responseAllFlavors = Mockito.mock(HttpResponse.class);
		Mockito.when(responseAllFlavors.getEntity()).thenReturn(entityAllFlavors);
		Mockito.when(responseAllFlavors.getStatusLine()).thenReturn(statusLine);

		ByteArrayEntity entityFlavorTwo = new ByteArrayEntity(jsonFlavorTwo.getBytes());
		HttpResponse responseFlavorTwo = Mockito.mock(HttpResponse.class);
		Mockito.when(responseFlavorTwo.getEntity()).thenReturn(entityFlavorTwo);
		Mockito.when(responseFlavorTwo.getStatusLine()).thenReturn(statusLine);

		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				responseAllFlavors, responseFlavorTwo, responseAllFlavors);

		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(nameOne, "1", "1", "1"));
		novaV2ComputeOpenStack.setFlavors(flavors);
		novaV2ComputeOpenStack.setClient(client);

		Assert.assertEquals(1, novaV2ComputeOpenStack.getFlavors().size());

		// Updating Flavor List
		novaV2ComputeOpenStack.updateFlavors(defaultToken);

		Assert.assertEquals(2, novaV2ComputeOpenStack.getFlavors().size());
		
		// Adding Flavors that does not exists in the cloud
		novaV2ComputeOpenStack.getFlavors().add(new Flavor("C", "", "", "", 0));
		novaV2ComputeOpenStack.getFlavors().add(new Flavor("D", "", "", "", 0));
		
		Assert.assertEquals(4, novaV2ComputeOpenStack.getFlavors().size());
		
		// Removing Flavors that does not exists in the cloud
		novaV2ComputeOpenStack.updateFlavors(defaultToken);
		
		Assert.assertEquals(2, novaV2ComputeOpenStack.getFlavors().size());
	}
	
	public void tesGettInstanceState() {
		Assert.assertEquals(InstanceState.RUNNING, 
				novaV2ComputeOpenStack.getInstance(defaultToken, "active").getState());
		Assert.assertEquals(InstanceState.FAILED, 
				novaV2ComputeOpenStack.getInstance(defaultToken, "error").getState());
		Assert.assertEquals(InstanceState.PENDING, 
				novaV2ComputeOpenStack.getInstance(defaultToken, "initialized").getState());
		Assert.assertEquals(InstanceState.SUSPENDED, 
				novaV2ComputeOpenStack.getInstance(defaultToken, "suspended").getState());
	}	
}
