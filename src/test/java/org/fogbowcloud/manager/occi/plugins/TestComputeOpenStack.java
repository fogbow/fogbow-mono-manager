package org.fogbowcloud.manager.occi.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.fogbowcloud.manager.occi.core.Category;
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

	private final String firstExpectedInstanceId = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private final String fifthExpectedInstanceId = "hla256kh-43ar-67ww-ao90-fa8d456fce9f";
	private final String fourthExpectedInstanceId = "qwuif8ad-19a3-4afg-1l77-tred90crei0q";
	private final String thirdExpectedInstanceId = "cg2563ee-503c-6abr-54gl-ba8d12hf0pof";
	private final String secondExpectedInstanceId = "at62f3ad-67ac-56gb-8a55-adbm98cdee9f";
	private ComputeOpenStackPlugin computeOpenStack;
	private PluginHelper pluginHelper;
	List<String> expectedInstanceIds;

	private final String COMPUTE_END_POINT = "http://localhost:" + PluginHelper.PORT_ENDPOINT
			+ ComputeApplication.TARGET;

	@Before
	public void setUp() throws Exception {
		computeOpenStack = new ComputeOpenStackPlugin(COMPUTE_END_POINT);

		// five first generated instance ids
		List<String> expectedInstanceIds = new ArrayList<String>();
		expectedInstanceIds.add(firstExpectedInstanceId);
		expectedInstanceIds.add(secondExpectedInstanceId);
		expectedInstanceIds.add(thirdExpectedInstanceId);
		expectedInstanceIds.add(fourthExpectedInstanceId);
		expectedInstanceIds.add(fifthExpectedInstanceId);

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

		Assert.assertEquals(firstExpectedInstanceId,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));

		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				firstExpectedInstanceId);
		// String instanceDetails =
		// "Category: compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; title=\"Compute Resource\"; rel=\"http://schemas.ogf.org/occi/core#resource\"; location=\"http://localhost:8787/compute/\"; attributes=\"occi.compute.architecture occi.compute.state{immutable} occi.compute.speed occi.compute.memory occi.compute.cores occi.compute.hostname\"; actions=\"http://schemas.ogf.org/occi/infrastructure/compute/action#start http://schemas.ogf.org/occi/infrastructure/compute/action#stop http://schemas.ogf.org/occi/infrastructure/compute/action#restart http://schemas.ogf.org/occi/infrastructure/compute/action#suspend"
		// + "\n"
		// + "X-OCCI-Attribute: org.openstack.compute.console.vnc=\"N/A\"" +
		// "\n" +
		// "X-OCCI-Attribute: occi.compute.architecture=\"x86\"" + "\n" +
		// "X-OCCI-Attribute: occi.compute.state=\"inactive\"" + "\n" +
		// "X-OCCI-Attribute: occi.compute.speed=\"0.0\"+ \"\n" +
		// "X-OCCI-Attribute: org.openstack.compute.state=\"building\""+ "\n" +
		// "X-OCCI-Attribute: occi.compute.memory=\"0.0625\""+ "\n" +
		// "X-OCCI-Attribute: occi.compute.cores=\"1\""+ "\n";
		Assert.assertEquals(1,
				Integer.parseInt(getAttValueFromDetails(instanceDetails, "occi.compute.cores")));
		Assert.assertEquals(2,
				Integer.parseInt(getAttValueFromDetails(instanceDetails, "occi.compute.memory")));
		Assert.assertEquals(64, Integer.parseInt(getAttValueFromDetails(instanceDetails,
				"occi.compute.architectute")));
		Assert.assertEquals("server-" + firstExpectedInstanceId,
				getAttValueFromDetails(instanceDetails, "occi.compute.hostname"));
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
		computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>());
	}

	@Test(expected = OCCIException.class)
	public void testRequestWithoutSizeCateory() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>());
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCIAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put("occi.compute.invalidname", "value");

		computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testNotSupportedOCCICoreAtt() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put("occi.compute.cores", "3");

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
		xOCCIAtt.put("occi.compute.memory", "5");

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
		xOCCIAtt.put("occi.compute.architecture", "x86");

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
		xOCCIAtt.put("occi.compute.speed", "2");

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
		xOCCIAtt.put("occi.compute.hostname", "server-test");

		Assert.assertEquals(firstExpectedInstanceId,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt));

		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				firstExpectedInstanceId);
		Assert.assertEquals("server-test",
				getAttValueFromDetails(instanceDetails, "occi.compute.hostname"));
	}

	@Test
	public void testSupportedOCCIAttInvalidValue() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put("occi.compute.hostname", "server-test");
		xOCCIAtt.put("occi.compute.state", "anyvalue");

		Assert.assertEquals(firstExpectedInstanceId,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt));

		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				firstExpectedInstanceId);
		Assert.assertEquals("server-test",
				getAttValueFromDetails(instanceDetails, "occi.compute.hostname"));
		Assert.assertEquals("inactive",
				getAttValueFromDetails(instanceDetails, "occi.compute.state"));
		Assert.assertEquals("error",
				getAttValueFromDetails(instanceDetails, "org.openstack.compute.state"));
	}

	@Test
	public void testMoreSupportedOCCIAtts() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put("occi.compute.hostname", "server-test");
		xOCCIAtt.put("occi.compute.state", "inactive");

		Assert.assertEquals(firstExpectedInstanceId,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, xOCCIAtt));

		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				firstExpectedInstanceId);
		Assert.assertEquals("server-test",
				getAttValueFromDetails(instanceDetails, "occi.compute.hostname"));
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
		Assert.assertEquals(firstExpectedInstanceId,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));

		// check getting all instance ids
		List<String> instanceIds = getInstanceLocations(computeOpenStack
				.getInstancesFromUser(PluginHelper.AUTH_TOKEN));
		Assert.assertEquals(1, instanceIds.size());
		Assert.assertEquals(firstExpectedInstanceId, instanceIds.get(0));
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
			Assert.assertEquals(instanceId,
					computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));
		}

		// check getting all instance ids
		List<String> instanceIds = getInstanceLocations(computeOpenStack
				.getInstancesFromUser(PluginHelper.AUTH_TOKEN));
		Assert.assertEquals(expectedInstanceIds.size(), instanceIds.size());
		for (String instanceId : instanceIds) {
			Assert.assertTrue(expectedInstanceIds.contains(instanceId));
		}
	}

	private List<String> getInstanceLocations(String instancesFromUser) {
		StringTokenizer st = new StringTokenizer(instancesFromUser, ":");
		List<String> locations = new ArrayList<String>();
		while (st.hasMoreTokens()) {
			st.nextToken(); // X-OCCI-Location
			locations.add(st.nextToken().trim());
		}
		return locations;
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
		Assert.assertEquals(firstExpectedInstanceId,
				computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));

		// check instance details
		Assert.assertEquals(
				1,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());
		String instanceDetails = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN,
				firstExpectedInstanceId);
		Assert.assertEquals(firstExpectedInstanceId,
				getAttValueFromDetails(instanceDetails, "occi.core.id"));
		Assert.assertEquals(1,
				Integer.parseInt(getAttValueFromDetails(instanceDetails, "occi.compute.cores")));
		Assert.assertEquals(2,
				Integer.parseInt(getAttValueFromDetails(instanceDetails, "occi.compute.memory")));
		Assert.assertEquals(64, Integer.parseInt(getAttValueFromDetails(instanceDetails,
				"occi.compute.architectute")));
		Assert.assertEquals("server-" + firstExpectedInstanceId,
				getAttValueFromDetails(instanceDetails, "occi.compute.hostname"));
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
			Assert.assertEquals(instanceId,
					computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));
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
			Assert.assertEquals(instanceId,
					computeOpenStack.requestInstance(PluginHelper.AUTH_TOKEN, categories, new HashMap<String, String>()));
		}

		// check number of instances
		List<String> instanceIds = getInstanceLocations(computeOpenStack
				.getInstancesFromUser(PluginHelper.AUTH_TOKEN));
		Assert.assertEquals(expectedInstanceIds.size(), instanceIds.size());

		// removing one instances
		computeOpenStack.removeInstance(PluginHelper.AUTH_TOKEN, firstExpectedInstanceId);
		Assert.assertEquals(
				expectedInstanceIds.size() - 1,
				getInstanceLocations(computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN))
						.size());
	}
}
