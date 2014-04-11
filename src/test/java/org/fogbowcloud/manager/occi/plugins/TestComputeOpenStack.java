package org.fogbowcloud.manager.occi.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.model.ComputeApplication;
import org.fogbowcloud.manager.occi.model.InstanceState;
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
	
		//five first generated instance ids
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
				computeOpenStack.requestInstance(categories, new HashMap<String, String>()));

		InstanceState instance = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN, firstExpectedInstanceId);
		Assert.assertEquals(1, Integer.parseInt(instance.getAttValue("occi.compute.cores")));
		Assert.assertEquals(2, Integer.parseInt(instance.getAttValue("occi.compute.memory")));
		Assert.assertEquals(64, Integer.parseInt(instance.getAttValue("occi.compute.architectute")));
		Assert.assertEquals("server-" + firstExpectedInstanceId,
				Integer.parseInt(instance.getAttValue("occi.compute.hostname")));
	}

	@Test(expected = OCCIException.class)
	public void testRequestWithoutOSCateory() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		computeOpenStack.requestInstance(categories, new HashMap<String, String>());
	}

	@Test(expected = OCCIException.class)
	public void testRequestWithoutSizeCateory() {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		computeOpenStack.requestInstance(categories, new HashMap<String, String>());
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

		computeOpenStack.requestInstance(categories, xOCCIAtt);
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

		computeOpenStack.requestInstance(categories, xOCCIAtt);
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

		computeOpenStack.requestInstance(categories, xOCCIAtt);
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

		computeOpenStack.requestInstance(categories, xOCCIAtt);
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

		computeOpenStack.requestInstance(categories, xOCCIAtt);
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
				computeOpenStack.requestInstance(categories, xOCCIAtt));

		InstanceState vm = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN, firstExpectedInstanceId);
		Assert.assertEquals("server-test", vm.getAttValue("occi.compute.hostname"));
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
				computeOpenStack.requestInstance(categories, xOCCIAtt));

		InstanceState instance = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN, firstExpectedInstanceId);
		Assert.assertEquals("server-test", instance.getAttValue("occi.compute.hostname"));
		Assert.assertEquals("inactive", instance.getAttValue("occi.compute.state"));
		Assert.assertEquals("error", instance.getAttValue("org.openstack.compute.state"));
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
				computeOpenStack.requestInstance(categories, xOCCIAtt));

		InstanceState vm = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN, firstExpectedInstanceId);
		Assert.assertEquals("server-test", vm.getAttValue("occi.compute.hostname"));
		Assert.assertEquals("inactive", vm.getAttValue("occi.compute.state"));
	}

	@Test
	public void testGetAllInstanceIds() {
		Assert.assertEquals(0, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());

		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(firstExpectedInstanceId,
				computeOpenStack.requestInstance(categories, new HashMap<String, String>()));

		// check getting all instance ids
		List<String> instanceIds = computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN);
		Assert.assertEquals(1, instanceIds.size());
		Assert.assertEquals(firstExpectedInstanceId, instanceIds.get(0));
	}

	@Test
	public void testGetAllManyInstanceIds() {
		Assert.assertEquals(0, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());

		// requesting default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));

		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId,
					computeOpenStack.requestInstance(categories, new HashMap<String, String>()));
		}

		// check getting all instance ids
		List<String> instanceIds = computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN);
		Assert.assertEquals(expectedInstanceIds.size(), instanceIds.size());
		for (String instanceId : instanceIds) {
			Assert.assertTrue(expectedInstanceIds.contains(instanceId));
		}
	}

	@Test
	public void testGetInstanceDetails() {
		Assert.assertEquals(0, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());

		// requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(firstExpectedInstanceId,
				computeOpenStack.requestInstance(categories, new HashMap<String, String>()));

		// check instance details
		Assert.assertEquals(1, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());
		InstanceState instance = computeOpenStack.getInstanceDetails(PluginHelper.AUTH_TOKEN, firstExpectedInstanceId);
		Assert.assertEquals(firstExpectedInstanceId, instance.getAttValue("occi.core.id"));
		Assert.assertEquals(1, Integer.parseInt(instance.getAttValue("occi.compute.cores")));
		Assert.assertEquals(2, Integer.parseInt(instance.getAttValue("occi.compute.memory")));
		Assert.assertEquals(64, Integer.parseInt(instance.getAttValue("occi.compute.architectute")));
		Assert.assertEquals("server-" + firstExpectedInstanceId,
				Integer.parseInt(instance.getAttValue("occi.compute.hostname")));
	}

	@Test
	public void testDeleteAllInstancesEmpty() {
		Assert.assertEquals(0, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());
		computeOpenStack.removeAllInstances(PluginHelper.AUTH_TOKEN);
		Assert.assertEquals(0, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());
	}
	
	@Test
	public void testDeleteAllManyInstances() {
		Assert.assertEquals(0, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());

		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId,
					computeOpenStack.requestInstance(categories, new HashMap<String, String>()));
		}

		// check number of instances
		List<String> instanceIds = computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN);
		Assert.assertEquals(expectedInstanceIds.size(), instanceIds.size());
				
		//removing all instances
		computeOpenStack.removeAllInstances(PluginHelper.AUTH_TOKEN);
		Assert.assertEquals(0, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());
	}

	@Test
	public void testDeleteOneInstance() {
		Assert.assertEquals(0, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());

		// requesting default instances
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(RequestConstants.UBUNTU64_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS));
		for (String instanceId : expectedInstanceIds) {
			Assert.assertEquals(instanceId,
					computeOpenStack.requestInstance(categories, new HashMap<String, String>()));
		}

		// check number of instances
		List<String> instanceIds = computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN);
		Assert.assertEquals(expectedInstanceIds.size(), instanceIds.size());
				
		//removing one instances
		computeOpenStack.removeInstance(PluginHelper.AUTH_TOKEN, firstExpectedInstanceId);
		Assert.assertEquals(expectedInstanceIds.size() -1, computeOpenStack.getInstancesFromUser(PluginHelper.AUTH_TOKEN).size());		
	}
}
