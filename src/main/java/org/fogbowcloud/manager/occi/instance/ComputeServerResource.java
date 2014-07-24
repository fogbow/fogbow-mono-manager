package org.fogbowcloud.manager.occi.instance;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class ComputeServerResource extends ServerResource {

	protected static final String NO_INSTANCES_MESSAGE = "There are not instances.";
	private static final Logger LOGGER = Logger.getLogger(ComputeServerResource.class);

	@Get
	public StringRepresentation fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());
		
		if (instanceId == null) {
			LOGGER.info("Getting all instances of token :" + authToken);
			if (acceptContent.size() == 0
					|| acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(
						generateResponse(application.getInstances(authToken)), MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				return new StringRepresentation(generateURIListResponse(
						application.getInstances(authToken), req), MediaType.TEXT_URI_LIST);
			} else {
				throw new OCCIException(ErrorType.METHOD_NOT_ALLOWED,
						ResponseConstants.METHOD_NOT_SUPPORTED);
			}
		}

		LOGGER.info("Getting instance " + instanceId);
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			return new StringRepresentation(application.getInstance(authToken, instanceId).toOCCIMessageFormatDetails(), MediaType.TEXT_PLAIN);				
		}
		throw new OCCIException(ErrorType.METHOD_NOT_ALLOWED,
				ResponseConstants.METHOD_NOT_SUPPORTED);
	}

	private String generateURIListResponse(List<Instance> instances, HttpRequest req) {
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		Iterator<Instance> instanceIt = instances.iterator();
		String result = "";		
		while(instanceIt.hasNext()){
			if (requestEndpoint.endsWith("/")){
				result += requestEndpoint + instanceIt.next().getId() + "\n";
			} else {
				result += requestEndpoint + "/" + instanceIt.next().getId() + "\n";
			}
		}
		return result.length() > 0 ? result.trim() : "\n";
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
	public StringRepresentation post() {
		OCCIApplication application = (OCCIApplication) getApplication();
		Response response = application.bypass(getRequest());
		return configureResponse(response);
	}

	private StringRepresentation configureResponse(Response response) {
		// setting headers to response
		Series<org.restlet.engine.header.Header> responseHeaders = (Series<org.restlet.engine.header.Header>) response.getAttributes().get("org.restlet.http.headers");
		if (responseHeaders != null) {
			getResponseAttributes().put("org.restlet.http.headers", responseHeaders);
		}

		getResponse().setStatus(response.getStatus());
		String content = "";
		try {
			content = response.getEntity().getText();
		} catch (IOException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
		
		return new StringRepresentation(content, response.getEntity().getMediaType());
	}

	protected static String generateResponse(List<Instance> instances) {
		if (instances == null || instances.isEmpty()) {
			return NO_INSTANCES_MESSAGE;
		}
		String response = "";
		Iterator<Instance> instanceIt = instances.iterator();
		while(instanceIt.hasNext()){
			response += instanceIt.next().toOCCIMessageFormatLocation();
			if (instanceIt.hasNext()){
				response += "\n";
			}
		}
		return response;
	}
}
