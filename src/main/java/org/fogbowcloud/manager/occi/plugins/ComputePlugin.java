package org.fogbowcloud.manager.occi.plugins;

import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.fogbowcloud.manager.occi.core.Category;

public interface ComputePlugin {

	public HttpResponse requestInstance(List<Category> requestResources, Map<String, String> xOCCIAtt);
	
}
