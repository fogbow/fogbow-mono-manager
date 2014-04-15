package org.fogbowcloud.manager.occi.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ComputeApplication;
import org.fogbowcloud.manager.occi.model.PluginHelper;
import org.fogbowcloud.manager.occi.plugins.openstack.ComputeOpenStackPlugin;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestComputeOpenStack {

	private final String FIRST_EXPECTED_INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private final String FIFTH_EXPECTED_INSTANCE_ID = "hla256kh-43ar-67ww-ao90-fa8d456fce9f";
	private final String FOURTH_EXPECTED_INSTANCE_ID = "qwuif8ad-19a3-4afg-1l77-tred90crei0q";
	private final String THIRD_EXPECTED_INSTANCE_ID = "cg2563ee-503c-6abr-54gl-ba8d12hf0pof";
	private final String SECOND_EXPECTED_INSTANCE_ID = "at62f3ad-67ac-56gb-8a55-adbm98cdee9f";
	private final String COMPUTE_END_POINT = "http://localhost:" + PluginHelper.PORT_ENDPOINT
			+ ComputeApplication.TARGET;
	private final String PREFIX_RESPONSE_LOCATION = HeaderUtils.X_OCCI_LOCATION
			+ COMPUTE_END_POINT + "/";

	
	private ComputeOpenStackPlugin computeOpenStack;
	private PluginHelper pluginHelper;
	List<String> expectedInstanceIds;

	@Before
	public void setUp() throws Exception {
		computeOpenStack = new ComputeOpenStackPlugin(COMPUTE_END_POINT);

		// five first generated instance ids
		expectedInstanceIds = new ArrayList<String>();
		expectedInstanceIds.add(FIRST_EXPECTED_INSTANCE_ID);
		expectedInstanceIds.add(SECOND_EXPECTED_INSTANCE_ID);
		expectedInstanceIds.add(THIRD_EXPECTED_INSTANCE_ID);
		expectedInstanceIds.add(FOURTH_EXPECTED_INSTANCE_ID);
		expectedInstanceIds.add(FIFTH_EXPECTED_INSTANCE_ID);

		pluginHelper = new PluginHelper();
		pluginHelper.initializeComputeComponent(expectedInstanceIds);
	}

	@After
	public void tearDown() throws Exception {
		pluginHelper.disconnectComponent();
	}

	@Test
	public void testRequestAValidInstance() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Assert.assertEquals(PREFIX_RESPONSE_LOCATION + FIRST_EXPECTED_INSTANCE_ID,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories,
						new HashMap<String, String>()));

		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				FIRST_EXPECTED_INSTANCE_ID);
		
		Assert.assertEquals(1,
				Integer.parseInt(getAttValueFromDetails(instanceDetails, ComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2,
				Integer.parseInt(getAttValueFromDetails(instanceDetails, ComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(getAttValueFromDetails(instanceDetails,
				ComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertEquals("server-" + FIRST_EXPECTED_INSTANCE_ID,
				getAttValueFromDetails(instanceDetails, ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
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
		computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories,
				new HashMap<String, String>());
	}

	@Test
	public void testRequestWithoutSizeCateory() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Assert.assertEquals(PREFIX_RESPONSE_LOCATION + FIRST_EXPECTED_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCICoreAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.CORE_ATTRIBUTE_OCCI, "3");

		computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCIMemAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.MEMORY_ATTRIBUTE_OCCI, "5");

		computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCIArchAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI, "x86");

		computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCISpeedAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.SPEED_ATTRIBUTE_OCCI, "2");

		computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt);
	}

	@Test
	public void testSupportedOCCIAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI, "server-test");

		Assert.assertEquals(PREFIX_RESPONSE_LOCATION + FIRST_EXPECTED_INSTANCE_ID,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt));

		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				FIRST_EXPECTED_INSTANCE_ID);
		Assert.assertEquals("server-test",
				getAttValueFromDetails(instanceDetails, ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}

	@Test
	public void testMoreSupportedOCCIAtts() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI, "server-test");
		xOCCIAtt.put("occi.compute.state", "inactive");

		Assert.assertEquals(PREFIX_RESPONSE_LOCATION +  FIRST_EXPECTED_INSTANCE_ID,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt));

		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				FIRST_EXPECTED_INSTANCE_ID);
		Assert.assertEquals("server-test",
				getAttValueFromDetails(instanceDetails, ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
		Assert.assertEquals("inactive",
				getAttValueFromDetails(instanceDetails, "occi.compute.state"));
	}

	@Test
	public void testGetAllInstanceIds() {
		Assert.assertEquals(
				0,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());

		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(PREFIX_RESPONSE_LOCATION + FIRST_EXPECTED_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));

		// check getting all instance ids
		List<String> instanceIds = getInstanceLocations(computeOpenStack
				.getInstancesFromUser(PluginHelper.AUTH_TOKEN));
		Assert.assertEquals(1, instanceIds.size());
		Assert.assertEquals(COMPUTE_END_POINT + "/" + FIRST_EXPECTED_INSTANCE_ID, instanceIds.get(0));
	}

	@Test
	public void testGetAllManyInstanceIds() {
		Assert.assertEquals(
				0,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());

		// requesting default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(PREFIX_RESPONSE_LOCATION + instanceId, computeOpenStack.requestInstance(
					PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));
		}

		// check getting all instance ids
		List<String> instanceIds = getInstanceLocations(computeOpenStack
				.getInstancesFromUser(PluginHelper.AUTH_TOKEN));
		Assert.assertEquals(expectedInstanceIds.size(), instanceIds.size());
		System.out.println(instanceIds);
		for (String expectedId : expectedInstanceIds) {
			System.out.println(COMPUTE_END_POINT + expectedId);
			Assert.assertTrue(instanceIds.contains(COMPUTE_END_POINT + "/" + expectedId));
		}
	}

	private List<String> getInstanceLocations(String instancesFromUser) {
		if(instancesFromUser.contains(HeaderUtils.X_OCCI_LOCATION)){
			String[] tokens = instancesFromUser.split("\n");
			List<String> locations = new ArrayList<String>();
			for (int i = 0; i < tokens.length; i++) {
				String[] lineTokens = tokens[i].split("Location:");
				locations.add(lineTokens[1].trim());
			}
			return locations;			
		}
		return new ArrayList<String>();
	}

	@Test
	public void testGetInstanceDetails() {
		Assert.assertEquals(
				0,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());

		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(PREFIX_RESPONSE_LOCATION + FIRST_EXPECTED_INSTANCE_ID, computeOpenStack.requestInstance(
				PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));

		// check instance details
		Assert.assertEquals(
				1,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());
		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				FIRST_EXPECTED_INSTANCE_ID);
		Assert.assertEquals(FIRST_EXPECTED_INSTANCE_ID,
				getAttValueFromDetails(instanceDetails, ComputeApplication.ID_CORE_ATTRIBUTE_OCCI));
		Assert.assertEquals(1,
				Integer.parseInt(getAttValueFromDetails(instanceDetails, ComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2,
				Integer.parseInt(getAttValueFromDetails(instanceDetails, ComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(getAttValueFromDetails(instanceDetails,
				ComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertEquals("server-" + FIRST_EXPECTED_INSTANCE_ID,
				getAttValueFromDetails(instanceDetails, ComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}

	@Test
	public void testDeleteAllInstancesEmpty() {
		Assert.assertEquals(
				0,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());
		computeOpenStack.removeAllInstances(PluginHelper.AUTH_TOKEN);
		Assert.assertEquals(
				0,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());
	}

	@Test
	public void testDeleteAllManyInstances() {
		Assert.assertEquals(
				0,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());

		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(PREFIX_RESPONSE_LOCATION  + instanceId, computeOpenStack.requestInstance(
					PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));
		}

		// check number of instances
		List<String> instanceIds = getInstanceLocations(computeOpenStack
				.getInstancesFromUser(PluginHelper.AUTH_TOKEN));
		Assert.assertEquals(expectedInstanceIds.size(), instanceIds.size());

		// removing all instances
		computeOpenStack.removeAllInstances(PluginHelper.AUTH_TOKEN);
		Assert.assertEquals(
				0,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());
	}

	@Test
	public void testDeleteOneInstance() {
		Assert.assertEquals(
				0,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());

		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(PREFIX_RESPONSE_LOCATION + instanceId, computeOpenStack.requestInstance(
					PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));
		}

		// check number of instances
		List<String> instanceIds = getInstanceLocations(computeOpenStack
				.getInstancesFromUser(PluginHelper.AUTH_TOKEN));
		Assert.assertEquals(expectedInstanceIds.size(), instanceIds.size());

		// removing one instances
		computeOpenStack.removeInstance(PluginHelper.AUTH_TOKEN, FIRST_EXPECTED_INSTANCE_ID);
		Assert.assertEquals(
				expectedInstanceIds.size() - 1,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());
	}
}
