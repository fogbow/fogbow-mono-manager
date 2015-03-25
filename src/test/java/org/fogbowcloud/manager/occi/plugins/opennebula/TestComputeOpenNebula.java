package org.fogbowcloud.manager.occi.plugins.opennebula;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.opennebula.OneConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.core.plugins.opennebula.OpenNebulaComputePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.group.Group;
import org.opennebula.client.user.User;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;
import org.restlet.Request;
import org.restlet.Response;

public class TestComputeOpenNebula {

	private static final String OPEN_NEBULA_URL = "http://localhost:2633/RPC2";
	private static final String INSTANCE_ID = "0";
	private static final String SMALL_FLAVOR_DATA = "{mem=128, cpu=1.0}";
	private static final String MEDIUM_FLAVOR_DATA = "{mem=256, cpu=2.0}";
	private static final String LARGE_FLAVOR_DATA = "{mem=512, cpu=4.0}";
	private static final String IMAGE1_ID = "1";
	private static final String IMAGE1_NAME = "image1";
	private static final int NETWORK_ID = 0;
	private static String DEFAULT_TEMPLATE;

	private static String SMALL_TEMPLATE;

	private Properties properties;
	private Map<String, String> xOCCIAtt;
	private OpenNebulaComputePlugin computeOpenNebula;
	private Token defaultToken;

	@Before
	public void setUp() throws IOException {
		properties = new Properties();
		properties.put(OneConfigurationConstants.COMPUTE_ONE_URL, OPEN_NEBULA_URL);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_SMALL_KEY, SMALL_FLAVOR_DATA);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_MEDIUM_KEY, MEDIUM_FLAVOR_DATA);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_LARGE_KEY, LARGE_FLAVOR_DATA);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_NETWORK_KEY, NETWORK_ID);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_IMAGE_PREFIX_KEY + IMAGE1_NAME,
				IMAGE1_ID);

		defaultToken = new Token(PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS,
				PluginHelper.USERNAME, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());

		// default userdata
		xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.USER_DATA_ATT.getValue(),
				Base64.encodeBase64URLSafeString("userdata".getBytes(Charsets.UTF_8)));

		DEFAULT_TEMPLATE = PluginHelper
				.getContentFile("src/test/resources/opennebula/default.template")
				.replaceAll("#NET_ID#", "" + NETWORK_ID)
				.replaceAll("#IMAGE_ID#", IMAGE1_ID)
				.replaceAll("#USERDATA#",
						Base64.encodeBase64URLSafeString("userdata".getBytes(Charsets.UTF_8)))
				.replaceAll("\n", "").replaceAll(" ", "");

		SMALL_TEMPLATE = DEFAULT_TEMPLATE.replace("#MEM#", "128").replace("#CPU#", "1.0");
	}

	@Test
	public void testBypassDoesNotWork() {
		computeOpenNebula = new OpenNebulaComputePlugin(properties);
		Request req = new Request();
		Response response = new Response(req);
		computeOpenNebula.bypass(req, response);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus().getCode());
		Assert.assertEquals(ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE, response
				.getStatus().getDescription());
	}

	@Test
	public void testRequestInstance() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));
	}

	@Test(expected = OCCIException.class)
	public void testRequestInstanceWithoutImageCategory() throws ClientConfigurationException {
		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, null);
	}

	@Test(expected = OCCIException.class)
	public void testRequestInstanceWithoutFlavor() throws ClientConfigurationException {
		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_NAME);
	}

	@Test
	public void testRemoveInstance() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.delete()).thenReturn(new OneResponse(true, "" + INSTANCE_ID));

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, INSTANCE_ID)).thenReturn(vm);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));

		// removing the instance
		computeOpenNebula.removeInstance(defaultToken, INSTANCE_ID);
	}

	@Test(expected = OCCIException.class)
	public void testRemoveNotFoundInstance() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.doThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND))
				.when(clientFactory).createVirtualMachine(oneClient, INSTANCE_ID);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// removing the instance
		computeOpenNebula.removeInstance(defaultToken, INSTANCE_ID);
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
		computeOpenNebula.removeInstance(defaultToken, INSTANCE_ID);
	}

	@Test
	public void testRemoveInstances() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		VirtualMachinePool firstVMPool = Mockito.mock(VirtualMachinePool.class);
		VirtualMachinePool secondVMPool = Mockito.mock(VirtualMachinePool.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.delete()).thenReturn(new OneResponse(true, "" + INSTANCE_ID));

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
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachinePool(oneClient)).thenReturn(firstVMPool,
				secondVMPool);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));

		// removing instances
		computeOpenNebula.removeInstances(defaultToken);

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(defaultToken);
		Assert.assertEquals(0, instances.size());
	}

	@Test
	public void testGetInstanceDefaultArch() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.getName()).thenReturn("one-instance");
		Mockito.when(vm.lcmStateStr()).thenReturn("Running");
		Mockito.when(vm.xpath("TEMPLATE/MEMORY")).thenReturn("128.0");
		Mockito.when(vm.xpath("TEMPLATE/CPU")).thenReturn("1.0");
		Mockito.when(vm.xpath("TEMPLATE/DISK/IMAGE")).thenReturn(IMAGE1_NAME);
		Mockito.when(vm.xpath("TEMPLATE/OS/ARCH")).thenReturn("");

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, INSTANCE_ID)).thenReturn(vm);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));

		// getting specific instance
		Instance instance = computeOpenNebula.getInstance(defaultToken, INSTANCE_ID);
		Assert.assertEquals(INSTANCE_ID, instance.getId());
		Assert.assertEquals("x86", instance.getAttributes().get("occi.compute.architecture"));
		Assert.assertEquals("active", instance.getAttributes().get("occi.compute.state"));
		Assert.assertEquals("Not defined", instance.getAttributes().get("occi.compute.speed"));
		Assert.assertEquals(String.valueOf(128d / 1024d),
				instance.getAttributes().get("occi.compute.memory"));
		Assert.assertEquals("1.0", instance.getAttributes().get("occi.compute.cores"));
		Assert.assertEquals("one-instance", instance.getAttributes().get("occi.compute.hostname"));
		Assert.assertEquals(INSTANCE_ID, instance.getAttributes().get("occi.core.id"));

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.getInstance().get(RequestConstants.SMALL_TERM));

		for (Resource resource : resources) {
			Assert.assertTrue(instance.getResources().contains(resource));
		}
		Assert.assertTrue(instance.getLinks().isEmpty());
	}

	@Test
	public void testGetInstance() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.getName()).thenReturn("one-instance");
		Mockito.when(vm.lcmStateStr()).thenReturn("Running");
		Mockito.when(vm.xpath("TEMPLATE/MEMORY")).thenReturn("128.0");
		Mockito.when(vm.xpath("TEMPLATE/CPU")).thenReturn("1.0");
		Mockito.when(vm.xpath("TEMPLATE/DISK/IMAGE")).thenReturn(IMAGE1_NAME);
		Mockito.when(vm.xpath("TEMPLATE/OS/ARCH")).thenReturn("x64");

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, INSTANCE_ID)).thenReturn(vm);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));

		// getting specific instance
		Instance instance = computeOpenNebula.getInstance(defaultToken, INSTANCE_ID);
		Assert.assertEquals(INSTANCE_ID, instance.getId());
		Assert.assertEquals("x64", instance.getAttributes().get("occi.compute.architecture"));
		Assert.assertEquals("active", instance.getAttributes().get("occi.compute.state"));
		Assert.assertEquals("Not defined", instance.getAttributes().get("occi.compute.speed"));
		Assert.assertEquals(String.valueOf(128d / 1024d),
				instance.getAttributes().get("occi.compute.memory"));
		Assert.assertEquals("1.0", instance.getAttributes().get("occi.compute.cores"));
		Assert.assertEquals("one-instance", instance.getAttributes().get("occi.compute.hostname"));
		Assert.assertEquals(INSTANCE_ID, instance.getAttributes().get("occi.core.id"));

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.getInstance().get(RequestConstants.SMALL_TERM));

		for (Resource resource : resources) {
			Assert.assertTrue(instance.getResources().contains(resource));
		}
		Assert.assertTrue(instance.getLinks().isEmpty());
	}

	@Test(expected = OCCIException.class)
	public void testGetNotFoundInstance() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, "not_found")).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, "Error getting vm not_found"));

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting instance
		computeOpenNebula.getInstance(defaultToken, "not_found");
	}

	@Test(expected = OCCIException.class)
	public void testGetUnauthorizedInstance() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, "instance")).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER));

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting instance
		computeOpenNebula.getInstance(defaultToken, "instance");
	}

	@Test
	public void testGetInstances() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		VirtualMachinePool vmPool = Mockito.mock(VirtualMachinePool.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.getName()).thenReturn("one-instance");
		Mockito.when(vm.lcmStateStr()).thenReturn("Running");
		Mockito.when(vm.xpath("TEMPLATE/MEMORY")).thenReturn("128.0");
		Mockito.when(vm.xpath("TEMPLATE/CPU")).thenReturn("1.0");
		Mockito.when(vm.xpath("TEMPLATE/DISK/IMAGE")).thenReturn(IMAGE1_NAME);
		Mockito.when(vm.xpath("TEMPLATE/OS/ARCH")).thenReturn("");

		@SuppressWarnings("unchecked")
		Iterator<VirtualMachine> mockIterator = Mockito.mock(Iterator.class);
		Mockito.when(mockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(mockIterator.next()).thenReturn(vm);
		Mockito.when(vmPool.iterator()).thenReturn(mockIterator);

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachinePool(oneClient)).thenReturn(vmPool);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(defaultToken);
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(INSTANCE_ID, instances.get(0).getId());
	}

	@Test
	public void testEmptyGetInstances() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		VirtualMachinePool vmPool = Mockito.mock(VirtualMachinePool.class);

		@SuppressWarnings("unchecked")
		Iterator<VirtualMachine> mockIterator = Mockito.mock(Iterator.class);
		Mockito.when(mockIterator.hasNext()).thenReturn(false);
		Mockito.when(vmPool.iterator()).thenReturn(mockIterator);

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.createVirtualMachinePool(oneClient)).thenReturn(vmPool);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(defaultToken);
		Assert.assertEquals(0, instances.size());
	}

	@Test
	public void testGetResourcesInfo() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("10.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("5120.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1.0", "128.0", 10));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2.0", "256.0", 5));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4.0", "512.0", 2));
		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}

	@Test
	public void testGetResourcesInfoWithAllUserQuotaSmallerThanGroupQuota() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("4096");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("5.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4096.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1.0", "128.0", 5 / 1));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2.0", "256.0", 5 / 2));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4.0", "512.0", 5 / 4));
		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}

	@Test
	public void testGetResourcesInfoWithAllGroupQuotaSmallerThanUserQuota() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn("5");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("4096");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn("5");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("5.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4096.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1.0", "128.0", 5 / 1));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2.0", "256.0", 5 / 2));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4.0", "512.0", 5 / 4));
		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}

	@Test
	public void testGetResourcesInfoWithSomeUserQuotaSmallerThanGroup() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("4096");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("5.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4096.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1.0", "128.0", 5 / 1));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2.0", "256.0", 5 / 2));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4.0", "512.0", 5 / 4));
		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}

	@Test
	public void testGetResourcesInfoWithSomeUserQuotaSmallerThanGroupAndUsed() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("2");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("1024");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("2");

		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("3");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("4096");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("1152");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("3");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("3.0", resourcesInfo.getCpuIdle()); // 5 - 2
		Assert.assertEquals("2.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("2944.0", resourcesInfo.getMemIdle()); // 4096 -
																	// 1152
		Assert.assertEquals("1152.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1.0", "128.0", 3 / 1));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2.0", "256.0", 3 / 2));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4.0", "512.0", 3 / 4));
		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}

	@Test
	public void testGetResourcesInfoWithValueDefaultOfQuota() {
		String vmsUsed = "8";
		String cpuUsed = "8";
		String memUsed = "1024";
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn(
				String.valueOf(OpenNebulaComputePlugin.VALUE_DEFAULT_QUOTA_OPENNEBULA));
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn(cpuUsed);
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn(
				String.valueOf(OpenNebulaComputePlugin.VALUE_DEFAULT_QUOTA_OPENNEBULA));
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn(vmsUsed);
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn(
				String.valueOf(OpenNebulaComputePlugin.VALUE_DEFAULT_QUOTA_OPENNEBULA));
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn(memUsed);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		int instanceIdle = OpenNebulaComputePlugin.DEFAULT_RESOURCE_MAX_VALUE
				- Integer.parseInt(vmsUsed);
		double cpuIdle = OpenNebulaComputePlugin.DEFAULT_RESOURCE_MAX_VALUE
				- Double.parseDouble(cpuUsed);
		String cpuIdleStr = String.valueOf(cpuIdle);
		Assert.assertEquals(cpuIdleStr, resourcesInfo.getCpuIdle());
		Assert.assertEquals("8.0", resourcesInfo.getCpuInUse());
		double memIdle = OpenNebulaComputePlugin.DEFAULT_RESOURCE_MAX_VALUE
				- Double.parseDouble(memUsed);
		String memIdleStr = String.valueOf(memIdle);
		Assert.assertEquals(memIdleStr, resourcesInfo.getMemIdle());
		Assert.assertEquals("1024.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		String smallCpu = "1.0";
		String smallMem = "128.0";
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, smallCpu, smallMem, calculateCapacity(
				cpuIdle, memIdle, smallCpu, smallMem, instanceIdle)));
		String mediumCpu = "2.0";
		String mediumMem = "256.0";
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, mediumCpu, mediumMem,
				calculateCapacity(cpuIdle, memIdle, mediumCpu, mediumMem, instanceIdle)));
		String largeCpu = "4.0";
		String largeMem = "512.0";
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, largeCpu, largeMem, calculateCapacity(
				cpuIdle, memIdle, largeCpu, largeMem, instanceIdle)));

		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}

	@Test
	public void testGetResourcesInfoWithValueUnlimitedOfQuota() {
		String vmsUsed = "8";
		String cpuUsed = "8";
		String memUsed = "1024";
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn(
				String.valueOf(OpenNebulaComputePlugin.VALUE_UNLIMITED_QUOTA_OPENNEBULA));
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn(cpuUsed);
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn(
				String.valueOf(OpenNebulaComputePlugin.VALUE_UNLIMITED_QUOTA_OPENNEBULA));
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn(vmsUsed);
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn(
				String.valueOf(OpenNebulaComputePlugin.VALUE_UNLIMITED_QUOTA_OPENNEBULA));
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn(memUsed);

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		int instanceIdle = Integer.MAX_VALUE - Integer.parseInt(vmsUsed);
		double cpuIdle = Integer.MAX_VALUE - Double.parseDouble(cpuUsed);
		String cpuIdleStr = String.valueOf(cpuIdle);
		Assert.assertEquals(cpuIdleStr, resourcesInfo.getCpuIdle());
		Assert.assertEquals("8.0", resourcesInfo.getCpuInUse());
		double memIdle = Integer.MAX_VALUE - Double.parseDouble(memUsed);
		String memIdleStr = String.valueOf(memIdle);
		Assert.assertEquals(memIdleStr, resourcesInfo.getMemIdle());
		Assert.assertEquals("1024.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		String smallCpu = "1.0";
		String smallMem = "128.0";
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, smallCpu, smallMem, calculateCapacity(
				cpuIdle, memIdle, smallCpu, smallMem, instanceIdle)));
		String mediumCpu = "2.0";
		String mediumMem = "256.0";
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, mediumCpu, mediumMem,
				calculateCapacity(cpuIdle, memIdle, mediumCpu, mediumMem, instanceIdle)));
		String largeCpu = "4.0";
		String largeMem = "512.0";
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, largeCpu, largeMem, calculateCapacity(
				cpuIdle, memIdle, largeCpu, largeMem, instanceIdle)));

		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}

	private int calculateCapacity(double cpuIdle, double memIdle, String cpuFlavor,
			String memFlavor, int instancesIdle) {
		return Math.min(
				(int) Math.min(cpuIdle / Double.parseDouble(cpuFlavor),
						memIdle / Double.parseDouble(memFlavor)), (int) instancesIdle);
	}

	@Test
	public void testGetResourcesInfoWithUsed() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("2");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("5120");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("256");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn("10");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("2");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("8.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("2.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4864.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("256.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1.0", "128.0", 8));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2.0", "256.0", 4));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4.0", "512.0", 2));
		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}

	@Test
	public void testGetResourcesInfoWithoutQuota() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		User user = Mockito.mock(User.class);
		Group group = Mockito.mock(Group.class);
		Mockito.when(user.xpath("GROUPS/ID")).thenReturn("5");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU")).thenReturn("");
		Mockito.when(user.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("");
		Mockito.when(user.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS")).thenReturn("");
		Mockito.when(user.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		Mockito.when(group.xpath("VM_QUOTA/VM/CPU")).thenReturn("");
		Mockito.when(group.xpath("VM_QUOTA/VM/CPU_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY")).thenReturn("");
		Mockito.when(group.xpath("VM_QUOTA/VM/MEMORY_USED")).thenReturn("0");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS")).thenReturn("");
		Mockito.when(group.xpath("VM_QUOTA/VM/VMS_USED")).thenReturn("0");

		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(accessId, OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.createUser(oneClient, PluginHelper.USERNAME)).thenReturn(user);
		Mockito.when(clientFactory.createGroup(Mockito.any(Client.class), Mockito.anyInt()))
				.thenReturn(group);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo with default values
		Assert.assertEquals(OpenNebulaComputePlugin.DEFAULT_RESOURCE_MAX_VALUE,
				Double.parseDouble(resourcesInfo.getCpuIdle()), 0.0001);
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals(OpenNebulaComputePlugin.DEFAULT_RESOURCE_MAX_VALUE,
				Double.parseDouble(resourcesInfo.getMemIdle()), 0.0001);
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(RequestConstants.SMALL_TERM, "1.0", "128.0",
				OpenNebulaComputePlugin.DEFAULT_RESOURCE_MAX_VALUE / 128));
		flavors.add(new Flavor(RequestConstants.MEDIUM_TERM, "2.0", "256.0",
				OpenNebulaComputePlugin.DEFAULT_RESOURCE_MAX_VALUE / 256));
		flavors.add(new Flavor(RequestConstants.LARGE_TERM, "4.0", "512.0",
				OpenNebulaComputePlugin.DEFAULT_RESOURCE_MAX_VALUE / 512));
		Assert.assertEquals(flavors, resourcesInfo.getFlavors());
	}
	
	@Test
	public void tesGettInstanceState() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.getName()).thenReturn("one-instance");
		Mockito.when(vm.xpath("TEMPLATE/MEMORY")).thenReturn("128.0");
		Mockito.when(vm.xpath("TEMPLATE/CPU")).thenReturn("1.0");
		Mockito.when(vm.xpath("TEMPLATE/DISK/IMAGE")).thenReturn(IMAGE1_NAME);
		Mockito.when(vm.xpath("TEMPLATE/OS/ARCH")).thenReturn("");

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, INSTANCE_ID)).thenReturn(vm);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));

		// getting specific instance
		Mockito.when(vm.lcmStateStr()).thenReturn("Running");
		Assert.assertEquals(InstanceState.RUNNING, 
				computeOpenNebula.getInstance(defaultToken, INSTANCE_ID).getState());
		Mockito.when(vm.lcmStateStr()).thenReturn("Failure");
		Assert.assertEquals(InstanceState.FAILED, 
				computeOpenNebula.getInstance(defaultToken, INSTANCE_ID).getState());
		Mockito.when(vm.lcmStateStr()).thenReturn("Prolog");
		Assert.assertEquals(InstanceState.PENDING, 
				computeOpenNebula.getInstance(defaultToken, INSTANCE_ID).getState());
		Mockito.when(vm.lcmStateStr()).thenReturn("Suspended");
		Assert.assertEquals(InstanceState.SUSPENDED, 
				computeOpenNebula.getInstance(defaultToken, INSTANCE_ID).getState());

	}
}
