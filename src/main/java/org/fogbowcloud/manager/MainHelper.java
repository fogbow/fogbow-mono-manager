package org.fogbowcloud.manager;

import java.util.Properties;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;

public class MainHelper {

	private static final int MINIMUM_XMPP_TIMEOUT = 4000;
	protected static final int EXIT_ERROR_CODE = 128;
	protected static final int DEFAULT_XMPP_TIMEOUT = 15000; // 15 segundos
	protected static final int DEFAULT_HTTP_PORT = 8182;
	protected static final int DEFAULT_HTTPS_PORT = 8183;
	protected static final boolean DEFAULT_HTTPS_ENABLED = false;
	protected static final int DEFAULT_REQUEST_HEADER_SIZE = 1024*1024;
	protected static final int DEFAULT_RESPONSE_HEADER_SIZE = 1024*1024;
	
	public static long getXMPPTimeout(Properties properties) {
		String timeoutStr = properties.getProperty(ConfigurationConstants.XMPP_TIMEOUT);
		long timeout = 0L;
		if (!timeoutStr.isEmpty() && timeoutStr != null) {
			try {
				timeout = Long.parseLong(timeoutStr);
				if (timeout < MINIMUM_XMPP_TIMEOUT) {
					throw new Error("Timeout is so small.");
				}
			} catch (Exception e) {
				throw new Error("Could not get timeout.", e);
			}
		} else {
			timeout = DEFAULT_XMPP_TIMEOUT;
		}
		return timeout;
	}	

	public static Object getIdentityPluginByPrefix(Properties properties, String prefix)
			throws Exception {
		Properties pluginProperties = new Properties();
		for (Object keyObj : properties.keySet()) {
			String key = keyObj.toString();
			pluginProperties.put(key, properties.get(key));
			if (key.startsWith(prefix)) {
				String newKey = key.replace(prefix, "");
				pluginProperties.put(newKey, properties.get(key));
			}
		}
		return createInstance(prefix + ConfigurationConstants.IDENTITY_CLASS_KEY, pluginProperties);
	}

	protected static Object createInstance(String propName, Properties properties) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class)
				.newInstance(properties);
	}
	
	protected static Object createInstanceWithComputePlugin(String propName, 
			Properties properties, ComputePlugin computePlugin) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class, ComputePlugin.class)
				.newInstance(properties, computePlugin);
	}
	
	protected static Object createInstanceWithBenchmarkingPlugin(
			String propName, Properties properties,
			BenchmarkingPlugin benchmarkingPlugin) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class, BenchmarkingPlugin.class)
				.newInstance(properties, benchmarkingPlugin);
	}
	
	public static Object createInstanceWithAccountingPlugin(
			String propName, Properties properties,
			AccountingPlugin accoutingPlugin) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class, AccountingPlugin.class)
				.newInstance(properties, accoutingPlugin);
	}	

	protected static void configureLog4j() {
		ConsoleAppender console = new ConsoleAppender();
		console.setThreshold(org.apache.log4j.Level.OFF);
		console.activateOptions();
		Logger.getRootLogger().addAppender(console);
	}	
	
}
