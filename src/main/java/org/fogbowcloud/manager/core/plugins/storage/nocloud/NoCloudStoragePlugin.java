package org.fogbowcloud.manager.core.plugins.storage.nocloud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;

public class NoCloudStoragePlugin implements StoragePlugin {
	
	public NoCloudStoragePlugin(Properties properties) {}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		throw new OCCIException(ErrorType.QUOTA_EXCEEDED, "There is no underlying cloud.");
	}

	@Override
	public List<Instance> getInstances(Token token) {
		return new ArrayList<Instance>();
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		//do nothing
	}

	@Override
	public void removeInstances(Token token) {
		//do nothing
	}

}
