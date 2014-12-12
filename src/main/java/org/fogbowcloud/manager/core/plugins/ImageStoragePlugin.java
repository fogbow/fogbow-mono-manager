package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.occi.core.Token;

public interface ImageStoragePlugin {

	public String getImage(Token token, String globalId);	
	
}
