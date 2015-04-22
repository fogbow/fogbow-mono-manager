package org.fogbowcloud.manager.core.plugins.imagestorage.fixed;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.Token;

public class StaticImageStoragePlugin implements ImageStoragePlugin {

	private static final String PROP_STATIC_IMAGE_PREFIX = "image_storage_static_";
	
	private Map<String, String> globalToLocalIds = new HashMap<String, String>();
	
	public StaticImageStoragePlugin(Properties properties, ComputePlugin computePlugin) {
		fillStaticStorage(properties);
	}
	
	private void fillStaticStorage(Properties properties) {
		for (Object propName : properties.keySet()) {
			String propNameStr = (String) propName;
			if (propNameStr.startsWith(PROP_STATIC_IMAGE_PREFIX)) {
				String globalImageId = propNameStr.substring(PROP_STATIC_IMAGE_PREFIX.length());
				globalToLocalIds.put(globalImageId, properties.getProperty(propNameStr));
				ResourceRepository.getInstance().addImageResource(globalImageId);
			}
		}
	}
	
	@Override
	public String getLocalId(Token token, String globalId) {
		String localId = globalToLocalIds.get(globalId);
		if (localId != null) {
			return localId;
		}
		return null;
	}

}
