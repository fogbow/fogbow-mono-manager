package org.fogbowcloud.manager.occi.instance;

import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class ComputeServerResource extends ServerResource {

	protected static final String NO_INSTANCES_MESSAGE = "There are not instances.";
	private static final Logger LOGGER = Logger.getLogger(ComputeServerResource.class);

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		if (instanceId == null) {
			LOGGER.info("Getting all instances of token :" + authToken);
			return generateResponse(application.getInstances(authToken));
		}

		LOGGER.info("Getting instance " + instanceId);

		return application.getInstance(authToken, instanceId).toOCCIMassageFormatDetails();
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		if (instanceId == null) {
			LOGGER.info("Removing all instances of token :" + authToken);
			application.removeInstances(authToken);
			return ResponseConstants.OK;
		}		
		LOGGER.info("Removing instance " + instanceId);
		
		application.removeInstance(authToken, instanceId);
		return ResponseConstants.OK;			
	}

	@Post
	public String post() {
		return null;
	}

	protected static String generateResponse(List<Instance> instances) {
		if (instances == null || instances.isEmpty()) {
			return NO_INSTANCES_MESSAGE;
		}
		String response = "";
		Iterator<Instance> instanceIt = instances.iterator();
		while(instanceIt.hasNext()){
			response += instanceIt.next().toOCCIMassageFormatLocation();
			if (instanceIt.hasNext()){
				response += "\n";
			}
		}
		return response;
	}
}
