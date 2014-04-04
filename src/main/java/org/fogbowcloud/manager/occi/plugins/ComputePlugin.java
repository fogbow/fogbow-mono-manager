package org.fogbowcloud.manager.occi.plugins;

import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.fogbowcloud.manager.occi.core.FogbowResource;
import org.restlet.engine.adapter.HttpRequest;

public interface ComputePlugin {

	public HttpResponse requestInstance(List<FogbowResource> requestResources, Map<String, String> xOCCIAtt);
	
}
