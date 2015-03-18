package org.fogbowcloud.manager.core.plugins.benchmarking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.benchmarking.FCUStaticBenchmarkingPlugin;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestFCUStaticBenchmarking {

	private static final double ACCEPTABLE_ERROR = 0.00;
	Map<String, String> instanceAttributes;
	FCUStaticBenchmarkingPlugin benchmarking;
	
	@Before
	public void setUp(){
		instanceAttributes = new HashMap<String, String>();
		instanceAttributes.put("occi.compute.memory", "2");
		instanceAttributes.put("occi.compute.cores", "2");		
		benchmarking = new FCUStaticBenchmarkingPlugin(null);
	}

	@Test
	public void testGetPowerInvalidInstanceId() {
		Instance instance = new Instance("instanceId", new ArrayList<Resource>(),
				new HashMap<String, String>(), new ArrayList<Link>());

		benchmarking.run(instance);
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER,
				benchmarking.getPower("invalidId"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testRunInstanceWithouAttributes() {
		Instance instance = new Instance("instanceId", new ArrayList<Resource>(),
				new HashMap<String, String>(), new ArrayList<Link>());

		benchmarking.run(instance);
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER,
				benchmarking.getPower(instance.getId()), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testRunNullInstance() {
		benchmarking.run(null);
	}
	
	@Test
	public void testRunAndGetPower() {
		Instance instance = new Instance("instanceId", new ArrayList<Resource>(),
				instanceAttributes, new ArrayList<Link>());

		benchmarking.run(instance);
		Assert.assertEquals(((2 / 8d) + (2 / 16d)) / 2, benchmarking.getPower(instance.getId()),
				ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testRunAndGetPowerTwoInstances() {
		// 2 vcpus and 2 Gb mem
		Instance instance1 = new Instance("instanceId1", new ArrayList<Resource>(),
				instanceAttributes, new ArrayList<Link>());
		double instancePower1 = ((2 / 8d) + (2 / 16d)) / 2;

 		// updating instance attributes (4 vcpus and 8 Gb mem)
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("occi.compute.memory", "8");
		attributes.put("occi.compute.cores", "4");
		Instance instance2 = new Instance("instanceId2", new ArrayList<Resource>(),
				attributes, new ArrayList<Link>());
		double instancePower2 = ((4 / 8d) + (8 / 16d)) / 2;

		// running benchmarking
		benchmarking.run(instance1);
		benchmarking.run(instance2);
		
		// checking instance powers
		Assert.assertEquals(instancePower1, benchmarking.getPower(instance1.getId()),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(instancePower2, benchmarking.getPower(instance2.getId()),
				ACCEPTABLE_ERROR);
		Assert.assertTrue(benchmarking.getPower(instance2.getId()) > benchmarking
				.getPower(instance1.getId()));
	}
	
	@Test
	public void testRunGetAndRemoveTwoInstances() {
		// 2 vcpus and 2 Gb mem
		Instance instance1 = new Instance("instanceId1", new ArrayList<Resource>(),
				instanceAttributes, new ArrayList<Link>());
		double instancePower1 = ((2 / 8d) + (2 / 16d)) / 2;

 		// updating instance attributes (4 vcpus and 8 Gb mem)
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("occi.compute.memory", "8");
		attributes.put("occi.compute.cores", "4");
		Instance instance2 = new Instance("instanceId2", new ArrayList<Resource>(),
				attributes, new ArrayList<Link>());
		double instancePower2 = ((4 / 8d) + (8 / 16d)) / 2;

		// running benchmarking
		benchmarking.run(instance1);
		benchmarking.run(instance2);
		
		// checking instance powers
		Assert.assertEquals(instancePower1, benchmarking.getPower(instance1.getId()),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(instancePower2, benchmarking.getPower(instance2.getId()),
				ACCEPTABLE_ERROR);
		Assert.assertTrue(benchmarking.getPower(instance2.getId()) > benchmarking
				.getPower(instance1.getId()));

		// removing instance1
		benchmarking.remove(instance1.getId());
		
		// checking instance powers
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER, benchmarking.getPower(instance1.getId()),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(instancePower2, benchmarking.getPower(instance2.getId()),
				ACCEPTABLE_ERROR);
		
		// removing instance2
		benchmarking.remove(instance2.getId());
		
		// checking instance powers
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER, benchmarking.getPower(instance1.getId()),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER, benchmarking.getPower(instance2.getId()),
				ACCEPTABLE_ERROR);

	}

}
