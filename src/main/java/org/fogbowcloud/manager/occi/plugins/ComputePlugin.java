package org.fogbowcloud.manager.occi.plugins;

import org.apache.http.HttpResponse;
import org.restlet.engine.adapter.HttpRequest;

public interface ComputePlugin {

	public HttpResponse requestInstance(HttpRequest req);
	
}
