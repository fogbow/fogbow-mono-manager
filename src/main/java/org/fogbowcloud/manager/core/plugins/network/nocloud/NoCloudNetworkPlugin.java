package org.fogbowcloud.manager.core.plugins.network.nocloud;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;

public class NoCloudNetworkPlugin implements NetworkPlugin {

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		throw new OCCIException(ErrorType.QUOTA_EXCEEDED, "There is no underlying cloud.");
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		//do nothing		
	}
	
}
