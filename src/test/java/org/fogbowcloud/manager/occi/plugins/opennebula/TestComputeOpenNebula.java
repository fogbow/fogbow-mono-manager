package org.fogbowcloud.manager.occi.plugins.opennebula;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.opennebula.OneConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.core.plugins.opennebula.OpenNebulaComputePlugin;
import org.fogbowcloud.manager.core.ssh.DefaultSSHTunnel;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.user.User;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;
import org.restlet.Request;
import org.restlet.Response;

public class TestComputeOpenNebula {

	private static final String OPEN_NEBULA_URL = "http://localhost:2633/RPC2";
	private static final String INSTANCE_ID = "0";
	private static final String SMALL_FLAVOR_DATA = "{mem=128, cpu=1}";
	private static final String MEDIUM_FLAVOR_DATA = "{mem=256, cpu=2}";
	private static final String LARGE_FLAVOR_DATA = "{mem=512, cpu=4}";
	private static final String IMAGE1_ID = "1";
	private static final String IMAGE1_NAME = "image1";
	private static final int NETWORK_ID = 0;
	private static String DEFAULT_TEMPLATE;
	
	private static String SMALL_TEMPLATE;
		
	private Properties properties;
	private Map<String, String> xOCCIAtt;
	private OpenNebulaComputePlugin computeOpenNebula;
	
	@Before
	public void setUp() throws IOException{
		properties = new Properties();
		properties.put(OneConfigurationConstants.COMPUTE_ONE_URL, OPEN_NEBULA_URL);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_SMALL_KEY, SMALL_FLAVOR_DATA);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_MEDIUM_KEY, MEDIUM_FLAVOR_DATA);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_LARGE_KEY, LARGE_FLAVOR_DATA);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_NETWORK_KEY, NETWORK_ID);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_IMAGE_PREFIX_KEY + IMAGE1_NAME, IMAGE1_ID);

		// default userdata
		xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(DefaultSSHTunnel.USER_DATA_ATT, "userdata");
		
		DEFAULT_TEMPLATE = PluginHelper
				.getContentFile("src/test/resources/opennebula/default.template")
				.replaceAll("#NET_ID#", "" + NETWORK_ID).replaceAll("#IMAGE_ID#", IMAGE1_ID)
				.replaceAll("#USERDATA#", "userdata").replaceAll("\n", "").replaceAll(" ", "");
	
		SMALL_TEMPLATE = DEFAULT_TEMPLATE.replace("#MEM#", "128").replace("#CPU#", "1");
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

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE))
				.thenReturn(INSTANCE_ID);
						
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(IMAGE1_NAME,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID, computeOpenNebula.requestInstance(accessId,
				categories, xOCCIAtt));
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceWithoutImageCategory() throws ClientConfigurationException{
		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
						
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		computeOpenNebula.requestInstance(accessId,	categories, xOCCIAtt);
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceWithoutFlavor() throws ClientConfigurationException{
		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
						
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(IMAGE1_NAME,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		computeOpenNebula.requestInstance(accessId, categories, xOCCIAtt);
	}
		
	@Test
	public void testRemoveInstance() throws ClientConfigurationException{
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		
		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.delete()).thenReturn(new OneResponse(true, ""+INSTANCE_ID));
				
		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, INSTANCE_ID)).thenReturn(vm);
						
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(IMAGE1_NAME,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID, computeOpenNebula.requestInstance(accessId,
				categories, xOCCIAtt));
		
		// removing the instance
		computeOpenNebula.removeInstance(accessId, INSTANCE_ID);
	}
	
	@Test(expected=OCCIException.class)
	public void testRemoveNotFoundInstance() throws ClientConfigurationException{
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		
		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);		
		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(clientFactory).createVirtualMachine(oneClient, INSTANCE_ID);
						
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
	
		// removing the instance
		computeOpenNebula.removeInstance(accessId, INSTANCE_ID);
	}
	
	@Test(expected = OCCIException.class)
	public void testRemoveUnauthorizedInstance() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.doThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER))
				.when(clientFactory).createVirtualMachine(oneClient, INSTANCE_ID);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// removing the instance
		computeOpenNebula.removeInstance(accessId, INSTANCE_ID);
	}
	
	@Test
	public void testRemoveInstances() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		VirtualMachinePool firstVMPool = Mockito.mock(VirtualMachinePool.class);
		VirtualMachinePool secondVMPool = Mockito.mock(VirtualMachinePool.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.delete()).thenReturn(new OneResponse(true,  ""+ INSTANCE_ID));

		@SuppressWarnings("unchecked")
		Iterator<VirtualMachine> firstMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(firstMockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(firstMockIterator.next()).thenReturn(vm);
		Mockito.when(firstVMPool.iterator()).thenReturn(firstMockIterator);

		@SuppressWarnings("unchecked")
		Iterator<VirtualMachine> secondMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(secondMockIterator.hasNext()).thenReturn(false);
		Mockito.when(secondVMPool.iterator()).thenReturn(secondMockIterator);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachinePool(oneClient)).thenReturn(firstVMPool,
				secondVMPool);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(IMAGE1_NAME, RequestConstants.TEMPLATE_OS_SCHEME,
				RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(accessId, categories, xOCCIAtt));

		// removing instances
		computeOpenNebula.removeInstances(accessId);

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(accessId);
		Assert.assertEquals(0, instances.size());
	}
	
	@Test
	public void testGetInstance() throws ClientConfigurationException{
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE))
				.thenReturn(INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, INSTANCE_ID)).thenReturn(vm);
				
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(IMAGE1_NAME,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID, computeOpenNebula.requestInstance(accessId,
				categories, xOCCIAtt));
		
		// getting specific instance
		Instance instance = computeOpenNebula.getInstance(accessId, INSTANCE_ID);
		Assert.assertEquals(INSTANCE_ID, instance.getId());
		
		//TODO add asserts
	}
	
	@Test(expected=OCCIException.class)
	public void testGetNotFoundInstance() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, "not_found")).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, "Error getting vm not_found"));
				
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// getting instance
		computeOpenNebula.getInstance(accessId, "not_found");
	}
	
	@Test(expected=OCCIException.class)
	public void testGetUnauthorizedInstance() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, "instance")).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER));
				
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		// getting instance
		computeOpenNebula.getInstance(accessId, "instance");
	}
	
	@Test
	public void testGetInstances() throws ClientConfigurationException{		
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		VirtualMachinePool vmPool = Mockito.mock(VirtualMachinePool.class);
		
		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		
		@SuppressWarnings("unchecked")
		Iterator<VirtualMachine> mockIterator = Mockito.mock(Iterator.class);
		Mockito.when(mockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(mockIterator.next()).thenReturn(vm);
		Mockito.when(vmPool.iterator()).thenReturn(mockIterator);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachinePool(oneClient)).thenReturn(vmPool);
		
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(IMAGE1_NAME,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID, computeOpenNebula.requestInstance(accessId,
				categories, xOCCIAtt));

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(accessId);
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(INSTANCE_ID, instances.get(0).getId());
	}
	
	@Test
	public void testEmptyGetInstances() throws ClientConfigurationException{		
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		VirtualMachinePool vmPool = Mockito.mock(VirtualMachinePool.class);
		
		@SuppressWarnings("unchecked")
		Iterator<VirtualMachine> mockIterator = Mockito.mock(Iterator.class);
		Mockito.when(mockIterator.hasNext()).thenReturn(false);
		Mockito.when(vmPool.iterator()).thenReturn(mockIterator);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createVirtualMachinePool(oneClient)).thenReturn(vmPool);
		
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(accessId);
		Assert.assertEquals(0, instances.size());
	}

	@Test
	public void testGetResourcesInfo(){
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient)).thenReturn(user);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("10", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("5120", resourcesInfo.getMemIdle());
		Assert.assertEquals("0", resourcesInfo.getMemInUse());
		Assert.assertNull(resourcesInfo.getCert());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1", "128", 10));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2", "256", 5));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4", "512", 2));
		Assert.assertEquals(flavors, resourcesInfo.getFlavours());
	}
	
	@Test
	public void testGetResourcesInfoWithUsed(){
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("2");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("256");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient)).thenReturn(user);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("8", resourcesInfo.getCpuIdle());
		Assert.assertEquals("2", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4864", resourcesInfo.getMemIdle());
		Assert.assertEquals("256", resourcesInfo.getMemInUse());
		Assert.assertNull(resourcesInfo.getCert());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1", "128", 8));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2", "256", 4));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4", "512", 2));
		Assert.assertEquals(flavors, resourcesInfo.getFlavours());
	}
	
	@Test
	public void testGetResourcesInfoWithoutQuota(){
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient)).thenReturn(user);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo with default values
		Assert.assertEquals("100", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("20480", resourcesInfo.getMemIdle());
		Assert.assertEquals("0", resourcesInfo.getMemInUse());
		Assert.assertNull(resourcesInfo.getCert());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1", "128", 100));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2", "256", 50));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4", "512", 25));
		Assert.assertEquals(flavors, resourcesInfo.getFlavours());
	}
}
