package org.fogbowcloud.manager.occi.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;

public interface ComputePlugin {

	public String requestInstance(List<Category> categories, Map<String, String> xOCCIAtt);

	public List<String> getInstancesFromUser(String username);
}
