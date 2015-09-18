package org.fogbowcloud.manager.core.plugins.prioritization;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.prioritization.fcfs.FCFSPrioritizationPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class TwoFoldPrioritizationPlugin implements PrioritizationPlugin {

	protected static final String REMOTE_PRIORITIZATION_PLUGIN_CLASS = "remote_prioritization_plugin_class";
	protected static final String LOCAL_PRIORITIZATION_PLUGIN_CLASS = "local_prioritization_plugin_class";
	private PrioritizationPlugin localPrioritizationPlugin;
	private PrioritizationPlugin remotePrioritizationPlugin;
	
	private static final Logger LOGGER = Logger.getLogger(TwoFoldPrioritizationPlugin.class);
	
	public TwoFoldPrioritizationPlugin(Properties properties, AccountingPlugin accountingPlugin) {
		try {
			localPrioritizationPlugin = (PrioritizationPlugin) createInstanceWithAccoutingPlugin(
					LOCAL_PRIORITIZATION_PLUGIN_CLASS, properties, accountingPlugin);
		} catch (Exception e) {
			LOGGER.warn("A valid local prioritization plugin was not specified in properties. "
					+ "Using the default one.",	e);
			localPrioritizationPlugin = new FCFSPrioritizationPlugin(properties, accountingPlugin);
		}
		
		try {
			remotePrioritizationPlugin = (PrioritizationPlugin) createInstanceWithAccoutingPlugin(
					REMOTE_PRIORITIZATION_PLUGIN_CLASS, properties, accountingPlugin);
		} catch (Exception e) {
			LOGGER.warn("A valid remote prioritization plugin was not specified in properties. "
					+ "Using the default one.",	e);
			remotePrioritizationPlugin = new FCFSPrioritizationPlugin(properties, accountingPlugin);
		}
	}
	
	private static Object createInstanceWithAccoutingPlugin(
			String propName, Properties properties,
			AccountingPlugin accoutingPlugin) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class, AccountingPlugin.class)
				.newInstance(properties, accoutingPlugin);
	}

	@Override
	public Request takeFrom(Request newRequest, List<Request> requestsWithInstance) {
		List<Request> localRequests = new ArrayList<Request>();
		List<Request> servedRequests = new ArrayList<Request>();
		for (Request currentRequest : requestsWithInstance) {
			if (currentRequest.isLocal()) {
				localRequests.add(currentRequest);
			} else {
				servedRequests.add(currentRequest);
			}
		}
		Request localPreemption = localPrioritizationPlugin.takeFrom(newRequest, localRequests);
		return (localPreemption != null) ? localPreemption : 
			remotePrioritizationPlugin.takeFrom(newRequest, servedRequests);
	}
	
	protected PrioritizationPlugin getLocalPrioritizationPlugin() {
		return localPrioritizationPlugin;
	}
	
	protected PrioritizationPlugin getRemotePrioritizationPlugin() {
		return remotePrioritizationPlugin;
	}
}
