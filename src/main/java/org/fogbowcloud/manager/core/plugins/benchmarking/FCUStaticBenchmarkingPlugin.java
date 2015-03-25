package org.fogbowcloud.manager.core.plugins.benchmarking;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.instance.Instance;

public class FCUStaticBenchmarkingPlugin implements BenchmarkingPlugin {
	
	Map<String, Double> instanceToPower = new HashMap<String, Double>();
	
	private static final Logger LOGGER = Logger.getLogger(FCUStaticBenchmarkingPlugin.class);
	
	public FCUStaticBenchmarkingPlugin(Properties properties) {
	}

	@Override
	public void run(Instance instance) {
		if (instance == null) {
			throw new IllegalArgumentException("Instance must not be null.");
		}
		LOGGER.info("Running benchmarking on instance: " + instance.getId());

		double power = UNDEFINED_POWER;
		try {
			String vcpuStr = instance.getAttributes().get("occi.compute.cores");
			String memStr = instance.getAttributes().get("occi.compute.memory");

			LOGGER.debug("Instance " + instance.getId() + " has " + vcpuStr + " vcpu and " + memStr
					+ " Gb of memrory.");
			
			double vcpu = Double.parseDouble(vcpuStr.replaceAll("\"", ""));
			double mem = Double.parseDouble(memStr.replaceAll("\"", ""));
			power = ((vcpu / 8d) + (mem / 16d)) / 2;
		} catch (Exception e) {
			LOGGER.error("Error while parsing attribute values to double.", e);
		}
		LOGGER.debug("Putting instanceId " + instance.getId() + " and power " + power);
		instanceToPower.put(instance.getId(), power);
	}

	@Override
	public double getPower(String instanceId) {
		LOGGER.debug("Getting power of instance " + instanceId);
		LOGGER.debug("Current instanceToPower=" + instanceToPower);
		if (instanceToPower.get(instanceId) == null) {
			return UNDEFINED_POWER;
		}
		return instanceToPower.get(instanceId);
	}

	@Override
	public void remove(String instanceId) {
		LOGGER.debug("Removing instance: " + instanceId + " from benchmarking map.");
		instanceToPower.remove(instanceId);		
	}
}
