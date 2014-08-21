package org.fogbowcloud.manager.occi.instance;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpStatus;
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
import org.restlet.resource.ServerResource;

public class ComputeServerResource extends ServerResource {

	protected static final String NO_INSTANCES_MESSAGE = "There are not instances.";
	private static final Logger LOGGER = Logger.getLogger(ComputeServerResource.class);
	
	@Get
	public StringRepresentation fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(), application.getAuthenticationURI());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());
		
		if (instanceId == null) {
			LOGGER.info("Getting all instances of token :" + authToken);
			List<Instance> allInstances = getInstances(application, authToken);
			
			if (acceptContent.size() == 0
					|| acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(
						generateResponse(allInstances), MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				return new StringRepresentation(generateURIListResponse(
						allInstances, req), MediaType.TEXT_URI_LIST);
			}
			throw new OCCIException(ErrorType.METHOD_NOT_ALLOWED,
				ResponseConstants.METHOD_NOT_SUPPORTED);
			
		}

		LOGGER.info("Getting instance " + instanceId);
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			try {
				Instance instance = application.getInstance(authToken, instanceId);
				return new StringRepresentation(instance.toOCCIMessageFormatDetails(),
						MediaType.TEXT_PLAIN);
			} catch (OCCIException e) {
				Response response = new Response(getRequest());
				application.bypass(getRequest(), response);
				// if it is a local instance created out of fogbow
				if (response.getStatus().getCode() == HttpStatus.SC_OK) {
					try {
						return new StringRepresentation(response.getEntity().getText(),
								MediaType.TEXT_PLAIN);
					} catch (Exception e1) { }
				}
				throw e;
			}
		}
		throw new OCCIException(ErrorType.METHOD_NOT_ALLOWED,
				ResponseConstants.METHOD_NOT_SUPPORTED);
	}
	
	private List<Instance> getInstances(OCCIApplication application, String authToken) {
		List<Instance> allInstances = application.getInstances(authToken);

		//Adding local instances created out of fogbow
		Response response = new Response(getRequest());
		application.bypass(getRequest(), response);
		for (Instance instance : getInstancesCreatedOutOfFogbow(response)) {
			if (!allInstances.contains(instance)){
				allInstances.add(instance);
			}
		}
		return allInstances;
	}

	private List<Instance> getInstancesCreatedOutOfFogbow(Response response) {
		List<Instance> localInstances = new ArrayList<Instance>();
		if (response.getStatus().getCode() == HttpStatus.SC_OK){
			try {
				String instanceLocations = response.getEntity().getText();
				LOGGER.debug("Cloud Instances Location: " + instanceLocations);
				if (instanceLocations != null && !"".equals(instanceLocations)){
					if (instanceLocations.contains(HeaderUtils.X_OCCI_LOCATION_PREFIX)) {
						String[] tokens = instanceLocations.trim().split(HeaderUtils.X_OCCI_LOCATION_PREFIX);
						for (int i = 0; i < tokens.length; i++) {
							if (!tokens[i].equals("")) {
								localInstances.add(new Instance(normalizeInstanceId(tokens[i].trim())));
							}
						}
					}
				}
			} catch (Exception e) {
				LOGGER.error("Exception while getting instance locations from private cloud ...", e);
			}
		}
		return localInstances;
	}

	private String normalizeInstanceId(String instanceLocation) {
		if (!instanceLocation.contains("/")) {
			return instanceLocation;
		}
		String[] splitInstanceId = instanceLocation.split("/");
		return splitInstanceId[splitInstanceId.length - 1];
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
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(), application.getAuthenticationURI());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		
		if (instanceId == null) {
			LOGGER.info("Removing all instances of token :" + authToken);
			return removeIntances(application, authToken);
		}
		LOGGER.info("Removing instance " + instanceId);		
		return removeInstance(application, authToken, instanceId);
	}

	private String removeInstance(OCCIApplication application, String authToken, String instanceId) {
		try {
			application.removeInstance(authToken, instanceId);
		} catch (OCCIException e) {
			//The request will be bypassed only if the error was not found
			if (e.getStatus().getCode() == HttpStatus.SC_NOT_FOUND){
				Response response = new Response(getRequest());
				application.bypass(getRequest(), response);
				//if it is a local instance created outside fogbow
				if (response.getStatus().getCode() == HttpStatus.SC_OK){
					return ResponseConstants.OK;
				}
			}
			throw e;
		}
		return ResponseConstants.OK;
	}

	private String removeIntances(OCCIApplication application, String authToken) {
		application.removeInstances(authToken);
		//Removing local cloud instances for the token
		Response response = new Response(getRequest());
		try {
			application.bypass(getRequest(), response);
		} catch (OCCIException e) { }
		return ResponseConstants.OK;
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