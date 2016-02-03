package org.fogbowcloud.manager.core.plugins.compute.opennebula;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.group.Group;
import org.opennebula.client.image.Image;
import org.opennebula.client.image.ImagePool;
import org.opennebula.client.template.Template;
import org.opennebula.client.template.TemplatePool;
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
		xOCCIAtt.put(OrderAttribute.USER_DATA_ATT.getValue(),
				Base64.encodeBase64URLSafeString("userdata".getBytes(Charsets.UTF_8)));
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " >= 10 && " + mem + " > 500 && " + vCpu + " > 0";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);

		DEFAULT_TEMPLATE = PluginHelper
				.getContentFile("src/test/resources/opennebula/default.template")
				.replaceAll("#NET_ID#", "" + NETWORK_ID)
				.replaceAll("#IMAGE_ID#", IMAGE1_ID)
				.replaceAll("#USERDATA#",
						Base64.encodeBase64URLSafeString("userdata".getBytes(Charsets.UTF_8)))
				.replaceAll("\n", "").replaceAll(" ", "");
	
		SMALL_TEMPLATE = DEFAULT_TEMPLATE.replace("#MEM#", "1000").replace("#CPU#", "1").replace("#DISK_VOLATILE#", "<DISK><SIZE>10</SIZE><TYPE>fs</TYPE></DISK>");
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

	@SuppressWarnings("unchecked")
	@Test
	public void testRequestInstance() throws ClientConfigurationException {
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
		
		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		Image imageTow = Mockito.mock(Image.class);
		Mockito.when(imageTow.xpath("SIZE")).thenReturn("200");
		Mockito.when(imageTow.getName()).thenReturn("image2");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image, imageTow);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.xpath("NAME")).thenReturn(OrderConstants.SMALL_TERM);
		Mockito.when(template.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(template.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(template.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(template.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(template.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool);
		
		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		computeOpenNebula.setFlavors(flavors);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));
		
		// checking the amount of flavors
		Assert.assertEquals(1, computeOpenNebula.getFlavors().size());
	}

	@Test(expected = OCCIException.class)
	public void testRequestInstanceWithoutImageCategory() throws ClientConfigurationException {
		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, null);
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceWithoutFlavor() throws ClientConfigurationException{
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		
		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
		.thenReturn(oneClient);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(new ImagePool(oneClient));
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(new TemplatePool(oneClient));
		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_NAME);
	}

	@SuppressWarnings("unchecked")
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
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(new ImagePool(oneClient));
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(new TemplatePool(oneClient));		

		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		Image imageTow = Mockito.mock(Image.class);
		Mockito.when(imageTow.xpath("SIZE")).thenReturn("200");
		Mockito.when(imageTow.getName()).thenReturn("image2");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image, imageTow);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.xpath("NAME")).thenReturn(OrderConstants.SMALL_TERM);
		Mockito.when(template.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(template.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(template.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(template.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(template.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool);		

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);		
		
		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		computeOpenNebula.setFlavors(flavors );
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(INSTANCE_ID, computeOpenNebula.requestInstance(defaultToken,
				categories, xOCCIAtt, IMAGE1_ID));
		
		// removing the instance
		computeOpenNebula.removeInstance(defaultToken, INSTANCE_ID);
		
		// checking the amount of flavors
		Assert.assertEquals(1, computeOpenNebula.getFlavors().size());
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

	@SuppressWarnings("unchecked")
	@Test
	public void testRemoveInstances() {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);
		VirtualMachinePool firstVMPool = Mockito.mock(VirtualMachinePool.class);
		VirtualMachinePool secondVMPool = Mockito.mock(VirtualMachinePool.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.delete()).thenReturn(new OneResponse(true, "" + INSTANCE_ID));

		Iterator<VirtualMachine> firstMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(firstMockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(firstMockIterator.next()).thenReturn(vm);
		Mockito.when(firstVMPool.iterator()).thenReturn(firstMockIterator);

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
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(new ImagePool(oneClient));
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(new TemplatePool(oneClient));
		
		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		Image imageTow = Mockito.mock(Image.class);
		Mockito.when(imageTow.xpath("SIZE")).thenReturn("200");
		Mockito.when(imageTow.getName()).thenReturn("image2");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image, imageTow);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.xpath("NAME")).thenReturn(OrderConstants.SMALL_TERM);
		Mockito.when(template.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(template.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(template.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(template.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(template.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		Template templateTwo = Mockito.mock(Template.class);
		Mockito.when(templateTwo.xpath("NAME")).thenReturn("medium");
		Mockito.when(templateTwo.xpath("TEMPLATE/MEMORY")).thenReturn("4000");
		Mockito.when(templateTwo.xpath("TEMPLATE/CPU")).thenReturn("4");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn("200");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/SIZE")).thenReturn("200");		
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template, templateTwo);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool);
		
		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		computeOpenNebula.setFlavors(flavors );
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(INSTANCE_ID,
				computeOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt, IMAGE1_ID));

		// removing instances
		computeOpenNebula.removeInstances(defaultToken);

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(defaultToken);
		Assert.assertEquals(0, instances.size());		
		
		// checking the amount of flavors
		Assert.assertEquals(2, computeOpenNebula.getFlavors().size());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetInstanceDefaultArch() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.getName()).thenReturn("one-instance");
		Mockito.when(vm.lcmStateStr()).thenReturn("Running");
		Mockito.when(vm.xpath("TEMPLATE/MEMORY")).thenReturn("1000");
		Mockito.when(vm.xpath("TEMPLATE/CPU")).thenReturn("1");
		Mockito.when(vm.xpath("TEMPLATE/DISK/IMAGE")).thenReturn(IMAGE1_NAME);
		Mockito.when(vm.xpath("TEMPLATE/OS/ARCH")).thenReturn("");

		// mocking clientFactory
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), OPEN_NEBULA_URL))
				.thenReturn(oneClient);
		Mockito.when(clientFactory.allocateVirtualMachine(oneClient, SMALL_TEMPLATE)).thenReturn(
				INSTANCE_ID);
		Mockito.when(clientFactory.createVirtualMachine(oneClient, INSTANCE_ID)).thenReturn(vm);				
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(new ImagePool(oneClient));
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(new TemplatePool(oneClient));
		
		VirtualMachinePool vmPool = Mockito.mock(VirtualMachinePool.class);

		Iterator<VirtualMachine> mockIterator = Mockito.mock(Iterator.class);
		Mockito.when(mockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(mockIterator.next()).thenReturn(vm);
		Mockito.when(vmPool.iterator()).thenReturn(mockIterator);

		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		Image imageTow = Mockito.mock(Image.class);
		Mockito.when(imageTow.xpath("SIZE")).thenReturn("200");
		Mockito.when(imageTow.getName()).thenReturn("image2");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image, imageTow);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.xpath("NAME")).thenReturn(OrderConstants.SMALL_TERM);
		Mockito.when(template.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(template.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(template.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(template.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(template.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		Template templateTwo = Mockito.mock(Template.class);
		Mockito.when(templateTwo.xpath("NAME")).thenReturn("");
		Mockito.when(templateTwo.xpath("TEMPLATE/MEMORY")).thenReturn("4000");
		Mockito.when(templateTwo.xpath("TEMPLATE/CPU")).thenReturn("4");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn("200");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/SIZE")).thenReturn("200");		
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template, templateTwo);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool);
		
		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		computeOpenNebula.setFlavors(flavors );
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(INSTANCE_ID, computeOpenNebula.requestInstance(defaultToken,
				categories, xOCCIAtt, IMAGE1_ID));
		
		// getting specific instance
		Instance instance = computeOpenNebula.getInstance(defaultToken, INSTANCE_ID);
		Assert.assertEquals(INSTANCE_ID, instance.getId());
		Assert.assertEquals("x86", instance.getAttributes().get("occi.compute.architecture"));
		Assert.assertEquals("active", instance.getAttributes().get("occi.compute.state"));
		Assert.assertEquals("Not defined", instance.getAttributes().get("occi.compute.speed"));
		Assert.assertEquals(String.valueOf(1000d/1024d), instance.getAttributes().get("occi.compute.memory"));
		Assert.assertEquals("1", instance.getAttributes().get("occi.compute.cores"));
		Assert.assertEquals("one-instance", instance.getAttributes().get("occi.compute.hostname"));
		Assert.assertEquals(INSTANCE_ID, instance.getAttributes().get("occi.core.id"));

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.generateFlavorResource(OrderConstants.SMALL_TERM));

		for (Resource resource : resources) {
			Assert.assertTrue(instance.getResources().contains(resource));
		}
		Assert.assertTrue(instance.getLinks().isEmpty());
		
		// checking the amount of flavors
		Assert.assertEquals(2, computeOpenNebula.getFlavors().size());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetInstance() throws ClientConfigurationException {
		// mocking opennebula structures
		Client oneClient = Mockito.mock(Client.class);

		VirtualMachine vm = Mockito.mock(VirtualMachine.class);
		Mockito.when(vm.getId()).thenReturn(INSTANCE_ID);
		Mockito.when(vm.getName()).thenReturn("one-instance");
		Mockito.when(vm.lcmStateStr()).thenReturn("Running");
		Mockito.when(vm.xpath("TEMPLATE/MEMORY")).thenReturn("1000.0");
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
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(new ImagePool(oneClient));
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(new TemplatePool(oneClient));
		
		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		Image imageTow = Mockito.mock(Image.class);
		Mockito.when(imageTow.xpath("SIZE")).thenReturn("200");
		Mockito.when(imageTow.getName()).thenReturn("image2");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image, imageTow);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.xpath("NAME")).thenReturn(OrderConstants.SMALL_TERM);
		Mockito.when(template.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(template.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(template.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(template.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(template.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);		
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool);		

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);		
		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		
		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		computeOpenNebula.setFlavors(flavors );
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(INSTANCE_ID, computeOpenNebula.requestInstance(defaultToken,
				categories, xOCCIAtt, IMAGE1_ID));
		
		// getting specific instance
		Instance instance = computeOpenNebula.getInstance(defaultToken, INSTANCE_ID);
		Assert.assertEquals(INSTANCE_ID, instance.getId());
		Assert.assertEquals("x64", instance.getAttributes().get("occi.compute.architecture"));
		Assert.assertEquals("active", instance.getAttributes().get("occi.compute.state"));
		Assert.assertEquals("Not defined", instance.getAttributes().get("occi.compute.speed"));
		Assert.assertEquals(String.valueOf(1000d/1024d), instance.getAttributes().get("occi.compute.memory"));
		Assert.assertEquals("1.0", instance.getAttributes().get("occi.compute.cores"));
		Assert.assertEquals("one-instance", instance.getAttributes().get("occi.compute.hostname"));
		Assert.assertEquals(INSTANCE_ID, instance.getAttributes().get("occi.core.id"));

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.generateFlavorResource(OrderConstants.SMALL_TERM));

		for (Resource resource : resources) {
			Assert.assertTrue(instance.getResources().contains(resource));
		}
		Assert.assertTrue(instance.getLinks().isEmpty());
		
		// checking the amount of flavors
		Assert.assertEquals(1, computeOpenNebula.getFlavors().size());
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, "1.0", "128.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, "2.0", "256.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, "4.0", "512.0", 0));
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, "1.0", "128.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, "2.0", "256.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, "4.0", "512.0", 0));
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
		// getting instance
		computeOpenNebula.getInstance(defaultToken, "instance");
	}

	@SuppressWarnings("unchecked")
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
		
		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		Image imageTow = Mockito.mock(Image.class);
		Mockito.when(imageTow.xpath("SIZE")).thenReturn("200");
		Mockito.when(imageTow.getName()).thenReturn("image2");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image, imageTow);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.xpath("NAME")).thenReturn(OrderConstants.SMALL_TERM);
		Mockito.when(template.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(template.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(template.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(template.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(template.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		Template templateTwo = Mockito.mock(Template.class);
		Mockito.when(templateTwo.xpath("NAME")).thenReturn("");
		Mockito.when(templateTwo.xpath("TEMPLATE/MEMORY")).thenReturn("4000");
		Mockito.when(templateTwo.xpath("TEMPLATE/CPU")).thenReturn("4");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn("200");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/SIZE")).thenReturn("200");		
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template, templateTwo);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool);
		
		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		computeOpenNebula.setFlavors(flavors);
		
		// requesting an instance
		List<Category> categories = new ArrayList<Category>();
		Assert.assertEquals(INSTANCE_ID, computeOpenNebula.requestInstance(defaultToken,
				categories, xOCCIAtt, IMAGE1_ID));

		// getting all instances
		List<Instance> instances = computeOpenNebula.getInstances(defaultToken);
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(INSTANCE_ID, instances.get(0).getId());
		
		// checking the amount of flavors
		Assert.assertEquals(2, computeOpenNebula.getFlavors().size());
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, "1.0", "128.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, "2.0", "256.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, "4.0", "512.0", 0));
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("10.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("5120.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		Assert.assertEquals("10", resourcesInfo.getInstancesIdle());
		Assert.assertEquals("0", resourcesInfo.getInstancesInUse());
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		String smallCpu = "1.0";
		String smallMem = "128.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, smallCpu, smallMem, 0));
		String mediumCpu = "2.0";
		String mediumMem = "256.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, mediumCpu, mediumMem, 0));
		String largeCpu = "4.0";
		String largeMem = "512.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, largeCpu, largeMem, 0));		
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("5.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4096.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		Assert.assertEquals("5", resourcesInfo.getInstancesIdle());
		Assert.assertEquals("0", resourcesInfo.getInstancesInUse());
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		String smallCpu = "1.0";
		String smallMem = "128.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, smallCpu, smallMem, 0));
		String mediumCpu = "2.0";
		String mediumMem = "256.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, mediumCpu, mediumMem, 0));
		String largeCpu = "4.0";
		String largeMem = "512.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, largeCpu, largeMem, 0));		
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("5.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4096.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		Assert.assertEquals("5", resourcesInfo.getInstancesIdle());
		Assert.assertEquals("0", resourcesInfo.getInstancesInUse());
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		String smallCpu = "1.0";
		String smallMem = "128.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, smallCpu, smallMem, 0));
		String mediumCpu = "2.0";
		String mediumMem = "256.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, mediumCpu, mediumMem, 0));
		String largeCpu = "4.0";
		String largeMem = "512.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, largeCpu, largeMem, 0));		
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("5.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("0.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4096.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("0.0", resourcesInfo.getMemInUse());
		Assert.assertEquals("10", resourcesInfo.getInstancesIdle());
		Assert.assertEquals("0", resourcesInfo.getInstancesInUse());
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
		
		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		String smallCpu = "1.0";
		String smallMem = "128.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, smallCpu, smallMem, 0));
		String mediumCpu = "2.0";
		String mediumMem = "256.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, mediumCpu, mediumMem, 0));
		String largeCpu = "4.0";
		String largeMem = "512.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, largeCpu, largeMem, 0));		
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);

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
		Assert.assertEquals("7", resourcesInfo.getInstancesIdle());
		Assert.assertEquals("3", resourcesInfo.getInstancesInUse());
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		String smallCpu = "1.0";
		String smallMem = "128.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, smallCpu, smallMem, 0));
		String mediumCpu = "2.0";
		String mediumMem = "256.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, mediumCpu, mediumMem, 0));
		String largeCpu = "4.0";
		String largeMem = "512.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, largeCpu, largeMem, 0));		
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
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
		Assert.assertEquals(String.valueOf(instanceIdle), resourcesInfo.getInstancesIdle());
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		String smallCpu = "1.0";
		String smallMem = "128.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, smallCpu, smallMem, 0));
		String mediumCpu = "2.0";
		String mediumMem = "256.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, mediumCpu, mediumMem, 0));
		String largeCpu = "4.0";
		String largeMem = "512.0";
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, largeCpu, largeMem, 0));		
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
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
		Assert.assertEquals(String.valueOf(instanceIdle), resourcesInfo.getInstancesIdle());
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
		
		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, "1.0", "128.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, "2.0", "256.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, "4.0", "512.0", 0));
		computeOpenNebula.setFlavors(flavorsComputeOpennebula );
		
		// getting resources info
		Token token = new Token(accessId, PluginHelper.USERNAME, null,
				new HashMap<String, String>());
		ResourcesInfo resourcesInfo = computeOpenNebula.getResourcesInfo(token);

		// checking resourcesInfo
		Assert.assertEquals("8.0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("2.0", resourcesInfo.getCpuInUse());
		Assert.assertEquals("4864.0", resourcesInfo.getMemIdle());
		Assert.assertEquals("256.0", resourcesInfo.getMemInUse());
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

		List<Flavor> flavorsComputeOpennebula = new ArrayList<Flavor>();
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.SMALL_TERM, "1.0", "128.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.MEDIUM_TERM, "2.0", "256.0", 0));
		flavorsComputeOpennebula.add(new Flavor(OrderConstants.LARGE_TERM, "4.0", "512.0", 0));
		computeOpenNebula.setFlavors(flavorsComputeOpennebula);
		
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
	}
	
	@Test
	public void TestRemoveInvalidFlavors() {
		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorOne = new Flavor("1", "1", "100", 0);
		flavors.add(flavorOne);
		Flavor flavorTwo = new Flavor("2", "1", "100", 0);
		flavors.add(flavorTwo);
		Flavor flavorThree = new Flavor("3", "1", "100", 0);
		flavors.add(flavorThree);
		
		computeOpenNebula = new OpenNebulaComputePlugin(properties);
		computeOpenNebula.setFlavors(flavors);
		Assert.assertEquals(flavors.size(), computeOpenNebula.getFlavors().size());
		
		List<Flavor> newFlavors = new ArrayList<Flavor>();
		newFlavors.add(flavorOne);
		newFlavors.add(flavorTwo);
		computeOpenNebula.removeInvalidFlavors(newFlavors);
		
		computeOpenNebula.setFlavors(flavors);
		Assert.assertEquals(newFlavors.size(), computeOpenNebula.getFlavors().size());
	}
	
	@Test
	public void testGetTemplatesInProperties() {
		String valuesTemplate = "value1, value2, value3";
		this.properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				valuesTemplate);
		computeOpenNebula = new OpenNebulaComputePlugin(properties);
		List<String> templatesInProperties = computeOpenNebula.getTemplatesInProperties(properties);
		for (String template : templatesInProperties) {
			if (!valuesTemplate.contains(template)) {
				Assert.fail();
			}
		}
	}

	@Test
	public void testGetFlavorWithoutTemplateTypeStated() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		Token token = new Token("", "", new Date(), new HashMap<String, String>());
		String requirements = RequirementsHelper.GLUE_VCPU_TERM + ">= 2 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + ">= 2000";
		Flavor flavor = computeOpenNebula.getFlavor(token, requirements);
		Assert.assertEquals("2", flavor.getCpu());
		Assert.assertEquals("2000", flavor.getMem());
	}
	
	@Test
	public void testGetFlavorWithoutTemplateTypeAndRequirementsNull() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		Token token = new Token("", "", new Date(), new HashMap<String, String>());
		Flavor flavor = computeOpenNebula.getFlavor(token, null);
		Assert.assertEquals("1", flavor.getCpu());
		Assert.assertEquals("1024", flavor.getMem());
	}	
	
	@Test
	public void testGetFlavorTemplateTypeTemplatesWithoutTamplate() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		Token token = new Token("", "", new Date(), new HashMap<String, String>());
		String requirements = RequirementsHelper.GLUE_VCPU_TERM + ">= 2 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + ">= 2000";
		Flavor flavor = computeOpenNebula.getFlavor(token, requirements);
		Assert.assertEquals("2", flavor.getCpu());
		Assert.assertEquals("2000", flavor.getMem());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGetFlavorTemplateTypeTemplatesAll() {
		Client oneClient = Mockito.mock(Client.class);
		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(Mockito.anyString(), Mockito.anyString())).thenReturn(oneClient);

		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		Image imageTow = Mockito.mock(Image.class);
		Mockito.when(imageTow.xpath("SIZE")).thenReturn("200");
		Mockito.when(imageTow.getName()).thenReturn("image2");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image, imageTow);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.xpath("NAME")).thenReturn("");
		Mockito.when(template.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(template.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(template.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(template.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(template.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		Template templateTwo = Mockito.mock(Template.class);
		Mockito.when(templateTwo.xpath("NAME")).thenReturn("");
		Mockito.when(templateTwo.xpath("TEMPLATE/MEMORY")).thenReturn("4000");
		Mockito.when(templateTwo.xpath("TEMPLATE/CPU")).thenReturn("4");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn("200");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/SIZE")).thenReturn("200");
				
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template, template);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);

		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		Token token = new Token(accessId, "", new Date(), new HashMap<String, String>());
		String requirements = RequirementsHelper.GLUE_VCPU_TERM + ">= 2 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + ">= 2000";
		Flavor flavor = computeOpenNebula.getFlavor(token, requirements);
		Assert.assertEquals("2", flavor.getCpu());
		Assert.assertEquals("2000", flavor.getMem());
		Assert.assertEquals("200", flavor.getDisk());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGetFlavorTemplateTypeTemplatesTemplates() {
		Client oneClient = Mockito.mock(Client.class);
		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(Mockito.anyString(), Mockito.anyString())).thenReturn(oneClient);

		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template templateOne = Mockito.mock(Template.class);
		String nameTamplateOne = "templateOne";
		Mockito.when(templateOne.xpath("NAME")).thenReturn(nameTamplateOne);
		Mockito.when(templateOne.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(templateOne.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(templateOne.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(templateOne.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(templateOne.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		Template templateTwo = Mockito.mock(Template.class);
		String nameTamplateTwo = "templateTwo";
		Mockito.when(templateTwo.xpath("NAME")).thenReturn(nameTamplateTwo);
		Mockito.when(templateTwo.xpath("TEMPLATE/MEMORY")).thenReturn("4000");
		Mockito.when(templateTwo.xpath("TEMPLATE/CPU")).thenReturn("4");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("200");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
				
		Template templateThree = Mockito.mock(Template.class);
		String nameTamplateThree = "templateThree";
		Mockito.when(templateThree.xpath("NAME")).thenReturn(nameTamplateThree);
		Mockito.when(templateThree.xpath("TEMPLATE/MEMORY")).thenReturn("8000");
		Mockito.when(templateThree.xpath("TEMPLATE/CPU")).thenReturn("8");
		Mockito.when(templateThree.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn("400");
		Mockito.when(templateThree.xpath("TEMPLATE/DISK[1]/SIZE")).thenReturn("400");		
		
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, true, true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(templateOne, templateTwo, templateThree);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);

		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES);
		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES, nameTamplateTwo + "," + nameTamplateThree);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		Token token = new Token(accessId, "", new Date(), new HashMap<String, String>());
		String requirements = RequirementsHelper.GLUE_VCPU_TERM + ">= 2 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + ">= 2000";
		Flavor flavor = computeOpenNebula.getFlavor(token, requirements);
		Assert.assertEquals(nameTamplateTwo, flavor.getName());
		Assert.assertEquals("4", flavor.getCpu());
		Assert.assertEquals("4000", flavor.getMem());
		Assert.assertEquals("300", flavor.getDisk());
	}
	
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
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
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
	
	@SuppressWarnings("unchecked")
	@Test
	public void testUpdateFlavors() {
		Client oneClient = Mockito.mock(Client.class);
		// mocking clientFactory
		String accessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactory.createClient(Mockito.anyString(), Mockito.anyString())).thenReturn(oneClient);

		Image image = Mockito.mock(Image.class);
		Mockito.when(image.xpath("SIZE")).thenReturn("100");
		String imageOne = "imageOne";
		Mockito.when(image.getName()).thenReturn(imageOne);

		Image imageTow = Mockito.mock(Image.class);
		Mockito.when(imageTow.xpath("SIZE")).thenReturn("200");
		Mockito.when(imageTow.getName()).thenReturn("image2");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image, imageTow);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		Template template = Mockito.mock(Template.class);
		Mockito.when(template.xpath("NAME")).thenReturn("");
		Mockito.when(template.xpath("TEMPLATE/MEMORY")).thenReturn("2000");
		Mockito.when(template.xpath("TEMPLATE/CPU")).thenReturn("2");
		Mockito.when(template.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn(imageOne);
		Mockito.when(template.xpath("TEMPLATE/DISK[2]/SIZE")).thenReturn("100");
		Mockito.when(template.xpath("TEMPLATE/DISK[2]")).thenReturn("NotNull");		
		
		Template templateTwo = Mockito.mock(Template.class);
		Mockito.when(templateTwo.xpath("NAME")).thenReturn("");
		Mockito.when(templateTwo.xpath("TEMPLATE/MEMORY")).thenReturn("4000");
		Mockito.when(templateTwo.xpath("TEMPLATE/CPU")).thenReturn("4");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/IMAGE")).thenReturn("200");
		Mockito.when(templateTwo.xpath("TEMPLATE/DISK[1]/SIZE")).thenReturn("200");
				
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		Iterator<Template> templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactory.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template, templateTwo);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);

		properties.put(OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES,
				OpenNebulaComputePlugin.OPENNEBULA_TEMPLATES_TYPE_ALL);
		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);

		Token token = new Token(accessId, "", new Date(), new HashMap<String, String>());
		computeOpenNebula.updateFlavors(token);
		
		Assert.assertEquals(2, computeOpenNebula.getFlavors().size());
		
		// New client factory
		OpenNebulaClientFactory clientFactoryTwo = Mockito.mock(OpenNebulaClientFactory.class);
		Mockito.when(clientFactoryTwo.createClient(Mockito.anyString(), Mockito.anyString())).thenReturn(oneClient);		
		Mockito.when(clientFactoryTwo.createImagePool(oneClient)).thenReturn(imagePool);		
		templatePool = Mockito.mock(TemplatePool.class);
		templateMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(clientFactoryTwo.createTemplatePool(oneClient)).thenReturn(templatePool );
		Mockito.when(templateMockIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(templateMockIterator.next()).thenReturn(template, templateTwo);
		Mockito.when(templatePool.iterator()).thenReturn(templateMockIterator);
		
		computeOpenNebula.setClientFactory(clientFactoryTwo);		
		computeOpenNebula.updateFlavors(token);
		
		Assert.assertEquals(2, computeOpenNebula.getFlavors().size());
	}	

	@Test
	public void testGenerateImageTemplate() {
		Map<String, String> templateProperties = new HashMap<String, String>();
		String imageName = "name";
		templateProperties.put("image_name", imageName);
		String imagePath = "path";
		templateProperties.put("image_path", imagePath);
		String imageDiskSize = "size";
		templateProperties.put("image_size", imageDiskSize);
		String imageType = "type";
		templateProperties.put("image_type", imageType);
		
		computeOpenNebula = new OpenNebulaComputePlugin(properties);
		String imageTemplate = computeOpenNebula.generateImageTemplate(templateProperties);
		Assert.assertTrue(imageTemplate.contains(imageName));
		Assert.assertTrue(imageTemplate.contains(imagePath));
		Assert.assertTrue(imageTemplate.contains(imageDiskSize));
		Assert.assertTrue(imageTemplate.contains(imageType));
	}
	
	@Test
	public void testGenerateTemplate() {
		computeOpenNebula = new OpenNebulaComputePlugin(properties);
		Map<String, String> templateProperties = new HashMap<String, String>();
		String sshPublicKey = "sshPublicKey";
		templateProperties.put("ssh-public-key", sshPublicKey);
		String userdata = "Userdata";
		templateProperties.put("userdata", userdata);
		String diskId = "1";
		templateProperties.put("disk-id", diskId);
		String diskSize = "1";
		templateProperties.put("disk-size", diskSize);
		String cpu = "10";
		templateProperties.put("cpu", cpu);
		String mem = "1000";
		templateProperties.put("mem", mem);
		String imageId = "11";
		templateProperties.put("image-id", imageId);
		String imageDisk = "1";
		templateProperties.put("image-disk", imageDisk);
		String template = computeOpenNebula.generateTemplate(templateProperties);
		Assert.assertTrue(template.contains(sshPublicKey));
		Assert.assertTrue(template.contains(userdata));
		Assert.assertTrue(template.contains(diskId));
		Assert.assertTrue(template.contains(diskSize));
		Assert.assertTrue(template.contains(cpu));
		Assert.assertTrue(template.contains(mem));
		Assert.assertTrue(template.contains(imageId));
		Assert.assertTrue(template.contains(imageDisk));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGetImageId() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(Mockito.anyString(), Mockito.anyString()))
				.thenReturn(oneClient);

		Image image = Mockito.mock(Image.class);
		String imageName = "image";
		String id = "1";
		Mockito.when(image.getId()).thenReturn(id);
		Mockito.when(image.getName()).thenReturn(imageName);

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(image);
		OneResponse oneResponse = Mockito.mock(OneResponse.class);
		Mockito.when(oneResponse.isError()).thenReturn(false);
		Mockito.when(imagePool.info()).thenReturn(oneResponse);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		String imageId = computeOpenNebula.getImageId(new Token("0123", "", null,
				new HashMap<String, String>()), imageName);
		Assert.assertEquals(id, imageId);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetImageState() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(Mockito.anyString(), Mockito.anyString()))
				.thenReturn(oneClient);

		Image imageLOCKED = Mockito.mock(Image.class);
		String imageNameLOCKED = "locked";
		Mockito.when(imageLOCKED.getName()).thenReturn(imageNameLOCKED);
		Mockito.when(imageLOCKED.stateString()).thenReturn("LOCKED");

		Image imageREADY = Mockito.mock(Image.class);
		String imageNameREADY = "ready";
		Mockito.when(imageREADY.getName()).thenReturn(imageNameREADY);
		Mockito.when(imageREADY.stateString()).thenReturn("READY");

		Image imageANYTHING = Mockito.mock(Image.class);
		String imageNameANYTHING = "anything";
		Mockito.when(imageANYTHING.getName()).thenReturn(imageNameANYTHING);
		Mockito.when(imageANYTHING.stateString()).thenReturn("ANYTHING");

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageMockIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageMockIterator.hasNext()).thenReturn(true, true, true, false);
		Mockito.when(imageMockIterator.next()).thenReturn(imageLOCKED, imageREADY, imageANYTHING);
		OneResponse oneResponse = Mockito.mock(OneResponse.class);
		Mockito.when(oneResponse.isError()).thenReturn(false);
		Mockito.when(imagePool.info()).thenReturn(oneResponse);
		Mockito.when(imagePool.iterator()).thenReturn(imageMockIterator);

		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		ImageState imageState = computeOpenNebula.getImageState(new Token("0123", "", null,
				new HashMap<String, String>()), imageNameLOCKED);
		Assert.assertEquals(ImageState.PENDING.getValue(), imageState.getValue());

		computeOpenNebula = new OpenNebulaComputePlugin(properties, clientFactory);
		imageState = computeOpenNebula.getImageState(new Token("01234", "", null,
				new HashMap<String, String>()), imageNameREADY);
		Assert.assertEquals(ImageState.ACTIVE.getValue(), imageState.getValue());

		imageState = computeOpenNebula.getImageState(new Token("01235", "", null,
				new HashMap<String, String>()), imageNameANYTHING);
		Assert.assertEquals(ImageState.FAILED.getValue(), imageState.getValue());
	}
}
