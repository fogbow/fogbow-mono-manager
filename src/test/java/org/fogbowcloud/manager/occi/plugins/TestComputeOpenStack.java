package org.fogbowcloud.manager.occi.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.ComputeApplication;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestComputeOpenStack {

	private static final String CIRROS_IMAGE_TERM = "cadf2e29-7216-4a5e-9364-cf6513d5f1fd";
	private static final String FIRST_INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private static final String FIFTH_INSTANCE_ID = "hla256kh-43ar-67ww-ao90-fa8d456fce9f";
	private static final String FOURTH_INSTANCE_ID = "qwuif8ad-19a3-4afg-1l77-tred90crei0q";
	private static final String THIRD_INSTANCE_ID = "cg2563ee-503c-6abr-54gl-ba8d12hf0pof";
	private static final String SECOND_INSTANCE_ID = "at62f3ad-67ac-56gb-8a55-adbm98cdee9f";

	private static final String URL = "http://localhost:" + PluginHelper.PORT_ENDPOINT;
	private static final String LINUX_X86_TERM = "linuxx86";

	private OpenStackComputePlugin computeOpenStack;
	private PluginHelper pluginHelper;
	List<String> expectedInstanceIds;

	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.COMPUTE_OCCI_URL_KEY, URL);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY, ComputeApplication.INSTANCE_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY, ComputeApplication.OS_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY, ComputeApplication.RESOURCE_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY, ComputeApplication.SMALL_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY, ComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY, ComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + LINUX_X86_TERM, CIRROS_IMAGE_TERM);

		computeOpenStack = new OpenStackComputePlugin(properties);

		// five first generated instance ids
		expectedInstanceIds = new ArrayList<String>();
		expectedInstanceIds.add(FIRST_INSTANCE_ID);
		expectedInstanceIds.add(SECOND_INSTANCE_ID);
		expectedInstanceIds.add(THIRD_INSTANCE_ID);
		expectedInstanceIds.add(FOURTH_INSTANCE_ID);
		expectedInstanceIds.add(FIFTH_INSTANCE_ID);

		pluginHelper = new PluginHelper();
		pluginHelper.initializeComputeComponent(expectedInstanceIds);
	}

	@After
	public void tearDown() throws Exception {
		pluginHelper.disconnectComponent();
	}
	
	@Test
	public void testIfImageResourceWasAdded(){		
		Resource imageResource = ResourceRepository.getInstance().get(LINUX_X86_TERM);
		Assert.assertNotNull(imageResource);
		Assert.assertEquals(LINUX_X86_TERM, imageResource.getCategory().getTerm());
		Assert.assertEquals(RequestConstants.TEMPLATE_OS_SCHEME, imageResource.getCategory().getScheme());
		Assert.assertEquals(RequestConstants.MIXIN_CLASS, imageResource.getCategory().getCatClass());
		Assert.assertTrue(imageResource.getAttributes().isEmpty());
		Assert.assertEquals(LINUX_X86_TERM + " image", imageResource.getTitle());
	}
	
	@Test
	public void testCreatPluginWithMoreThanOneImage(){
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.COMPUTE_OCCI_URL_KEY, URL);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY, ComputeApplication.INSTANCE_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY, ComputeApplication.OS_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY, ComputeApplication.RESOURCE_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY, ComputeApplication.SMALL_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY, ComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY, ComputeApplication.MEDIUM_FLAVOR_TERM);
		//specifying 5 images
		properties.put(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + "image1", "image1");
		properties.put(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + "image2", "image2");
		properties.put(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + "image3", "image3");
		properties.put(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + "image4", "image4");
		properties.put(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + "image5", "image5");

		computeOpenStack = new OpenStackComputePlugin(properties);

		//checking if 5 images were added
		for (int i = 1; i < 6; i++) {
			String imageName = "image" + i;
			Resource imageResource = ResourceRepository.getInstance().get(imageName);
			Assert.assertNotNull(imageResource);
			Assert.assertEquals(imageName, imageResource.getCategory().getTerm());
			Assert.assertEquals(RequestConstants.TEMPLATE_OS_SCHEME, imageResource.getCategory().getScheme());
			Assert.assertEquals(RequestConstants.MIXIN_CLASS, imageResource.getCategory().getCatClass());
			Assert.assertTrue(imageResource.getAttributes().isEmpty());
			Assert.assertEquals(imageName + " image", imageResource.getTitle());			
		}
	}
		
	@Test
	public void testRequestAValidInstance() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));

		Instance instance = computeOpenStack.getInstance(PluginHelper.ACCESS_ID, FIRST_INSTANCE_ID);

		Assert.assertEquals(1, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(), ComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(), ComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(),
				ComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(
				"server-" + FIRST_INSTANCE_ID,
				getAttValueFromDetails(instance.toOCCIMassageFormatDetails(),
						ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}
	
	@Test
	public void testCreatPluginSpecifyingNetwork(){
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.COMPUTE_OCCI_URL_KEY, URL);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY, ComputeApplication.INSTANCE_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY, ComputeApplication.OS_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY, ComputeApplication.RESOURCE_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY, ComputeApplication.SMALL_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY, ComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY, ComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + LINUX_X86_TERM, CIRROS_IMAGE_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_NETWORK_KEY, "net1");

		computeOpenStack = new OpenStackComputePlugin(properties);

		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		
		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));

		Instance instance = computeOpenStack.getInstance(PluginHelper.ACCESS_ID, FIRST_INSTANCE_ID);

		Assert.assertEquals(1, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(), ComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(), ComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(),
				ComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertTrue(instance.toOCCIMassageFormatDetails().contains(
				OCCIHeaders.LINK + ": </network/net1"));
		Assert.assertEquals(
				"server-" + FIRST_INSTANCE_ID,
				getAttValueFromDetails(instance.toOCCIMassageFormatDetails(),
						ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}
	
	@Test
	public void testCreatePluginNotSpecifyingNetwork(){		
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		
		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));

		Instance instance = computeOpenStack.getInstance(PluginHelper.ACCESS_ID, FIRST_INSTANCE_ID);

		Assert.assertEquals(1, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(), ComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(), ComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(getAttValueFromDetails(
				instance.toOCCIMassageFormatDetails(),
				ComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertTrue(instance.toOCCIMassageFormatDetails().contains(
				OCCIHeaders.LINK + ": </network/default"));
		Assert.assertEquals(
				"server-" + FIRST_INSTANCE_ID,
				getAttValueFromDetails(instance.toOCCIMassageFormatDetails(),
						ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}
	
	private String getAttValueFromDetails(String instanceDetails, String attName) {
		StringTokenizer st = new StringTokenizer(instanceDetails, "\n");
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
			if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE) && line.contains(attName)) {
				StringTokenizer st2 = new StringTokenizer(line, "=");
				st2.nextToken(); // attName
				return st2.nextToken().replaceAll("\"", "");
			}
		}
		return null;
	}

	@Test(expected = OCCIException.class)
	public void testRequestWithoutOSCateory() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		computeOpenStack.requestInstance(PluginHelper.ACCESS_ID, categories,
				new HashMap<String, String>());
	}

	@Test
	public void testRequestWithoutSizeCateory() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCICoreAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.CORE_ATTRIBUTE_OCCI, "3");

		computeOpenStack.requestInstance(PluginHelper.ACCESS_ID, categories, xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCIMemAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.MEMORY_ATTRIBUTE_OCCI, "5");

		computeOpenStack.requestInstance(PluginHelper.ACCESS_ID, categories, xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCIArchAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI, "x86");

		computeOpenStack.requestInstance(PluginHelper.ACCESS_ID, categories, xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCISpeedAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.SPEED_ATTRIBUTE_OCCI, "2");

		computeOpenStack.requestInstance(PluginHelper.ACCESS_ID, categories, xOCCIAtt);
	}

	@Test
	public void testSupportedOCCIAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI, "server-test");

		Assert.assertEquals(FIRST_INSTANCE_ID,
				computeOpenStack.requestInstance(PluginHelper.ACCESS_ID, categories, xOCCIAtt));

		String instanceDetails = computeOpenStack.getInstance(PluginHelper.ACCESS_ID,
				FIRST_INSTANCE_ID).toOCCIMassageFormatDetails();
		Assert.assertEquals("server-test",
				getAttValueFromDetails(instanceDetails, ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}

	@Test
	public void testMoreSupportedOCCIAtts() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI, "server-test");
		xOCCIAtt.put("occi.compute.state", "inactive");

		Assert.assertEquals(FIRST_INSTANCE_ID,
				computeOpenStack.requestInstance(PluginHelper.ACCESS_ID, categories, xOCCIAtt));

		String instanceDetails = computeOpenStack.getInstance(PluginHelper.ACCESS_ID,
				FIRST_INSTANCE_ID).toOCCIMassageFormatDetails();
		Assert.assertEquals("server-test",
				getAttValueFromDetails(instanceDetails, ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
		Assert.assertEquals("inactive",
				getAttValueFromDetails(instanceDetails, "occi.compute.state"));
	}

	@Test
	public void testGetAllInstanceIds() {
		List<String> instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(0, instanceLocations.size());

		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));

		// check getting all instance ids
		instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(1, instanceLocations.size());
		Assert.assertEquals(URL + ComputeApplication.TARGET + "/" + FIRST_INSTANCE_ID,
				instanceLocations.get(0));
	}

	@Test
	public void testGetAllManyInstanceIds() {
		List<String> instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(0, instanceLocations.size());

		// requesting default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId, computeOpenStack.requestInstance(
					PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		}

		// check getting all instance ids
		instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(expectedInstanceIds.size(), instanceLocations.size());
		for (String expectedId : expectedInstanceIds) {
			Assert.assertTrue(instanceLocations.contains(URL + ComputeApplication.TARGET + "/"
					+ expectedId));
		}
	}

	private List<String> getInstanceLocations(List<Instance> intances) {
		List<String> locations = new ArrayList<String>();
		for (Instance instance : intances) {
			// String instanceMessage = instance.toOCCIMassageFormatLocation();
			String[] lineTokens = instance.toOCCIMassageFormatLocation().split("Location:");
			locations.add(lineTokens[1].trim());
		}
		return locations;
	}

	@Test
	public void testGetInstanceDetails() {
		List<String> instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(0, instanceLocations.size());

		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));

		// check instance details
		instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(1, instanceLocations.size());
		String instanceDetails = computeOpenStack.getInstance(PluginHelper.ACCESS_ID,
				FIRST_INSTANCE_ID).toOCCIMassageFormatDetails();
		Assert.assertEquals(FIRST_INSTANCE_ID,
				getAttValueFromDetails(instanceDetails, ComputeApplication.ID_CORE_ATTRIBUTE_OCCI));
		Assert.assertEquals(1, Integer.parseInt(getAttValueFromDetails(instanceDetails,
				ComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(getAttValueFromDetails(instanceDetails,
				ComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(getAttValueFromDetails(instanceDetails,
				ComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertEquals("server-" + FIRST_INSTANCE_ID,
				getAttValueFromDetails(instanceDetails, ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}

	@Test
	public void testDeleteAllInstancesEmpty() {
		List<String> instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(0, instanceLocations.size());

		computeOpenStack.removeInstances(PluginHelper.ACCESS_ID);
		instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(0, instanceLocations.size());
	}

	@Test
	public void testDeleteAllManyInstances() {
		List<String> instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(0, instanceLocations.size());

		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId, computeOpenStack.requestInstance(
					PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		}

		// check number of instances
		instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(expectedInstanceIds.size(), instanceLocations.size());

		// removing all instances
		computeOpenStack.removeInstances(PluginHelper.ACCESS_ID);
		instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(0, instanceLocations.size());
	}

	@Test
	public void testDeleteOneInstance() {
		List<String> instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(0, instanceLocations.size());

		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId, computeOpenStack.requestInstance(
					PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		}

		// check number of instances
		instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(expectedInstanceIds.size(), instanceLocations.size());

		// removing one instances
		computeOpenStack.removeInstance(PluginHelper.ACCESS_ID, FIRST_INSTANCE_ID);
		instanceLocations = getInstanceLocations(computeOpenStack
				.getInstances(PluginHelper.ACCESS_ID));
		Assert.assertEquals(expectedInstanceIds.size() - 1, instanceLocations.size());
	}
}
