package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.occi.instance.Instance;

public interface BenchmarkingPlugin {

	public static double UNDEFINED_POWER = 1;

	public void run(Instance instance);

	public double getPower(String instanceId);
	
	public void remove(String instanceId);
}
