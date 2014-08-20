package org.fogbowcloud.manager.occi.plugins.opennebula;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.opennebula.OneConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.core.plugins.opennebula.OpenNebulaComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.template.Template;
import org.opennebula.client.template.TemplatePool;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;
import org.restlet.Request;
import org.restlet.Response;

public class TestComputeOpenNebula {

	private static final int TEMPLATE_ID = 0;
	private static final String OPEN_NEBULA_URL = "http://localhost:2633/RPC2";
	private static final String FIRST_INSTANCE_ID = "0";

	private Properties properties;
	private OpenNebulaComputePlugin computeOpenNebula;
	
	@Before
	public void setUp() throws IOException{
		properties = new Properties();
		properties.put(OneConfigurationConstants.COMPUTE_ONE_URL, OPEN_NEBULA_URL);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_SMALL_TPL, TEMPLATE_ID);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_MEDIUM_TPL, 1);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_LARGE_TPL, 2);
	}
	
	@Test(expected=OCCIException.class)
	public void testBypassDoesNotWork() {
		computeOpenNebula = new OpenNebulaComputePlugin(properties);
		Request req = new Request();
		computeOpenNebula.bypass(req, new Response(req));
	}
	
	@Test
	public void testRequestInstance() throws ClientConfigurationException{
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		Template template = Mockito.mock(Template.class);
		Mockito.when(template.instantiate()).thenReturn(new OneResponse(true, FIRST_INSTANCE_ID));
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Mockito.when(templatePool.getById(TEMPLATE_ID)).thenReturn(template);
		
		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.createTemplatePool(oneClient))
				.thenReturn(templatePool);
		
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenNebula.requestInstance(accessId,
				categories, new HashMap<String, String>()));
	}
	
	@Ignore
	@Test
	public void testRemoveInstance() throws ClientConfigurationException{
		
	}
	
	@Ignore
	@Test
	public void testRemoveInstances(){
		
	}
	
	@Test
	public void testGetInstance() throws ClientConfigurationException{
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(FIRST_INSTANCE_ID);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.instantiate()).thenReturn(new OneResponse(true, FIRST_INSTANCE_ID));

		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Mockito.when(templatePool.getById(TEMPLATE_ID)).thenReturn(template);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, FIRST_INSTANCE_ID)).thenReturn(vm);
		
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenNebula.requestInstance(accessId,
				categories, new HashMap<String, String>()));
		
		// getting specific instance
		Instance instance = computeOpenNebula.getInstance(accessId, FIRST_INSTANCE_ID);		
		Assert.assertEquals(FIRST_INSTANCE_ID, instance.getId());
		
		//TODO add asserts
	}
	
	@Test
	public void testGetInstances() throws ClientConfigurationException{
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		VirtualMachinePool vmPool = Mockito.mock(VirtualMachinePool.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(FIRST_INSTANCE_ID);
		
		@SuppressWarnings("unchecked")
		Iterator<VirtualMachine> mockIterator = Mockito.mock(Iterator.class);
		Mockito.when(mockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(mockIterator.next()).thenReturn(vm);
        Mockito.when(vmPool.iterator()).thenReturn(mockIterator);
		
		Template template = Mockito.mock(Template.class);
		Mockito.when(template.instantiate()).thenReturn(new OneResponse(true, FIRST_INSTANCE_ID));
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Mockito.when(templatePool.getById(TEMPLATE_ID)).thenReturn(template);
		
		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.createTemplatePool(oneClient))
				.thenReturn(templatePool);
		Mockito.when(clientFactory.createVirtualMachinePool(oneClient)).thenReturn(vmPool);
		
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenNebula.requestInstance(accessId,
				categories, new HashMap<String, String>()));

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(accessId);
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(FIRST_INSTANCE_ID, instances.get(0).getId());
	}

	@Ignore
	@Test
	public void testGetResourcesInfo(){
		
	}	
}
