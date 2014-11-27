package org.fogbowcloud.manager.core.plugins.egi;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;

public class EgiImageStoragePlugin implements ImageStoragePlugin{

	private Properties properties;
	private ComputePlugin computePlugin;
	
	public EgiImageStoragePlugin(Properties properties) {
		this.properties = properties;
	}
	
	public EgiImageStoragePlugin(Properties properties, ComputePlugin computePlugin) {
		this(properties);
		this.computePlugin = computePlugin;
	}
	
	@Override
	public String getImage(String globalId) {
		return null;
	}
}
