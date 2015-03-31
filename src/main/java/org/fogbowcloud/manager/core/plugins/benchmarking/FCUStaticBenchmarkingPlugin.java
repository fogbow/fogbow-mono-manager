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
	public void run(String globalInstanceId, Instance instance) {
		if (instance == null) {
			throw new IllegalArgumentException("Instance must not be null.");
		}
		LOGGER.info("Running benchmarking on instance: " + globalInstanceId);

		double power = UNDEFINED_POWER;
		try {
			String vcpuStr = instance.getAttributes().get("occi.compute.cores");
			String memStr = instance.getAttributes().get("occi.compute.memory");

			LOGGER.debug("Instance " + globalInstanceId + " has " + vcpuStr + " vCPU and " + memStr
					+ " GB of RAM.");
			
			double vcpu = parseDouble(vcpuStr);
			double mem = parseDouble(memStr);
			
			power = ((vcpu / 8d) + (mem / 16d)) / 2;
		} catch (Exception e) {
			LOGGER.error("Error while parsing attribute values to double.", e);
		}
		
		LOGGER.debug("Putting instanceId " + globalInstanceId + " and power " + power);
		instanceToPower.put(globalInstanceId, power);
	}

	private double parseDouble(String str) {
		return Double.parseDouble(str.replaceAll("\"", ""));
	}

	@Override
	public double getPower(String globalInstanceId) {
		LOGGER.debug("Getting power of instance " + globalInstanceId);
		LOGGER.debug("Current instanceToPower=" + instanceToPower);
		if (instanceToPower.get(globalInstanceId) == null) {
			return UNDEFINED_POWER;
		}
		return instanceToPower.get(globalInstanceId);
	}

	@Override
	public void remove(String globalInstanceId) {
		LOGGER.debug("Removing instance: " + globalInstanceId + " from benchmarking map.");
		instanceToPower.remove(globalInstanceId);		
	}
}
