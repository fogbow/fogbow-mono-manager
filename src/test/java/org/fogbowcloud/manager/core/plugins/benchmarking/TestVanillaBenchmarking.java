package org.fogbowcloud.manager.core.plugins.benchmarking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.benchmarking.VanillaBenchmarkingPlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Resource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestVanillaBenchmarking {

	private static final double ACCEPTABLE_ERROR = 0.00;
	Map<String, String> instanceAttributes;
	VanillaBenchmarkingPlugin benchmarking;
	
	@Before
	public void setUp(){
		instanceAttributes = new HashMap<String, String>();
		instanceAttributes.put("occi.compute.memory", "2");
		instanceAttributes.put("occi.compute.cores", "2");
		benchmarking = new VanillaBenchmarkingPlugin(null);
	}

	@Test
	public void testGetPowerInvalidInstanceId() {
		Instance instance = new Instance("instanceId", new ArrayList<Resource>(),
				new HashMap<String, String>(), new ArrayList<Link>(), InstanceState.RUNNING);

		benchmarking.run("instanceId@memberId", instance);
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER,
				benchmarking.getPower("invalidId"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testRunInstanceWithouAttributes() {
		Instance instance = new Instance("instanceId", new ArrayList<Resource>(),
				new HashMap<String, String>(), new ArrayList<Link>(), InstanceState.RUNNING);

		benchmarking.run("instanceId@memberId", instance);
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER,
				benchmarking.getPower("instanceId@memberId"), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testRunNullInstance() {
		benchmarking.run(null, null);
	}
	
	@Test
	public void testRunAndGetPower() {
		Instance instance = new Instance("instanceId", new ArrayList<Resource>(),
				instanceAttributes, new ArrayList<Link>(), InstanceState.RUNNING);

		benchmarking.run("instanceId@memberId", instance);
		Assert.assertEquals(((2 / 8d) + (2 / 16d)) / 2, benchmarking.getPower("instanceId@memberId"),
				ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testRunAndGetPowerTwoInstances() {
		// 2 vcpus and 2 Gb mem
		Instance instance1 = new Instance("instanceId1", new ArrayList<Resource>(),
				instanceAttributes, new ArrayList<Link>(), InstanceState.RUNNING);
		double instancePower1 = ((2 / 8d) + (2 / 16d)) / 2;

 		// updating instance attributes (4 vcpus and 8 Gb mem)
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("occi.compute.memory", "8");
		attributes.put("occi.compute.cores", "4");
		Instance instance2 = new Instance("instanceId2", new ArrayList<Resource>(),
				attributes, new ArrayList<Link>(), InstanceState.RUNNING);
		double instancePower2 = ((4 / 8d) + (8 / 16d)) / 2;

		// running benchmarking
		benchmarking.run("instanceId1@memberId", instance1);
		benchmarking.run("instanceId2@memberId", instance2);
		
		// checking instance powers
		Assert.assertEquals(instancePower1, benchmarking.getPower("instanceId1@memberId"),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(instancePower2, benchmarking.getPower("instanceId2@memberId"),
				ACCEPTABLE_ERROR);
		Assert.assertTrue(benchmarking.getPower(instance2.getId()) > benchmarking
				.getPower("instanceId1@memberId"));
	}
	
	@Test
	public void testRunGetAndRemoveTwoInstances() {
		// 2 vcpus and 2 Gb mem
		Instance instance1 = new Instance("instanceId1", new ArrayList<Resource>(),
				instanceAttributes, new ArrayList<Link>(), InstanceState.RUNNING);
		double instancePower1 = ((2 / 8d) + (2 / 16d)) / 2;

 		// updating instance attributes (4 vcpus and 8 Gb mem)
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("occi.compute.memory", "8");
		attributes.put("occi.compute.cores", "4");
		Instance instance2 = new Instance("instanceId2", new ArrayList<Resource>(),
				attributes, new ArrayList<Link>(), InstanceState.RUNNING);
		double instancePower2 = ((4 / 8d) + (8 / 16d)) / 2;

		// running benchmarking
		benchmarking.run("instanceId1@memberId", instance1);
		benchmarking.run("instanceId2@memberId", instance2);
		
		// checking instance powers
		Assert.assertEquals(instancePower1, benchmarking.getPower("instanceId1@memberId"),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(instancePower2, benchmarking.getPower("instanceId2@memberId"),
				ACCEPTABLE_ERROR);
		Assert.assertTrue(benchmarking.getPower("instanceId2@memberId") > benchmarking
				.getPower("instanceId1@memberId"));

		// removing instance1
		benchmarking.remove("instanceId1@memberId");
		
		// checking instance powers
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER, benchmarking.getPower("instanceId1@memberId"),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(instancePower2, benchmarking.getPower("instanceId2@memberId"),
				ACCEPTABLE_ERROR);
		
		// removing instance2
		benchmarking.remove("instanceId2@memberId");
		
		// checking instance powers
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER, benchmarking.getPower("instanceId1@memberId"),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER, benchmarking.getPower("instanceId2@memberId"),
				ACCEPTABLE_ERROR);
	}

}
