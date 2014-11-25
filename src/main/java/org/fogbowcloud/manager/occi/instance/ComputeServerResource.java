package org.fogbowcloud.manager.occi.instance;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.Request;
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
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());
		
		if (instanceId == null) {
			LOGGER.info("Getting all instances of token :" + authToken);
			
			List<String> filterCategory = HeaderUtils.getValueHeaderPerName(OCCIHeaders.CATEGORY,
					req.getHeaders());
			List<String> filterAttribute = HeaderUtils.getValueHeaderPerName(
					OCCIHeaders.X_OCCI_ATTRIBUTE, req.getHeaders());
			
			List<Instance> allInstances = new ArrayList<Instance>();
			if (filterCategory.size() != 0 || filterAttribute.size() != 0) {
				allInstances = filterInstances(getInstancesFiltrated(application, authToken),
						filterCategory, filterAttribute);
			} else {
				allInstances = getInstances(application, authToken);			
			}
			
			if (acceptContent.size() == 0
					|| acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(
						generateResponse(allInstances), MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				return new StringRepresentation(generateURIListResponse(
						allInstances, req), MediaType.TEXT_URI_LIST);
			}
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
					ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
			
		}

		LOGGER.info("Getting instance " + instanceId);
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			try {
				Instance instance = application.getInstance(authToken, instanceId);
				try {
					LOGGER.info("Instance " + instance);
					LOGGER.debug("Instance id: " + instance.getId());
					LOGGER.debug("Instance attributes: " + instance.getAttributes());
					LOGGER.debug("Instance links: " + instance.getLinks());
					LOGGER.debug("Instance resources: " + instance.getResources());
					LOGGER.debug("Instance OCCI format " + instance.toOCCIMessageFormatDetails());
				} catch (Throwable e) {
					LOGGER.warn("", e);
				}
				
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
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
				ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}
	
	private List<Instance> filterInstances(List<Instance> allInstances,
			List<String> filterCategory, List<String> filterAttribute) {
		List<Instance> instancesFiltrated = new ArrayList<Instance>();
		boolean thereIsntCategory = true;
		for (Instance instance: allInstances) {
			if (filterCategory.size() != 0) {
				for (String valueCategoryFilter : filterCategory) {
					for (Resource resource : instance.getResources()) {
						if (valueCategoryFilter.contains(resource.getCategory().getTerm())
								&& valueCategoryFilter.contains(resource.getCategory().getScheme())) {
							instancesFiltrated.add(instance);
							thereIsntCategory = false;
						}
					}
				}
			}
			if (filterAttribute.size() != 0) {
				for (String valueAttributeFilter : filterAttribute) {
					Map<String, String> mapAttributes = instance.getAttributes();
					for (String keyAttribute : mapAttributes.keySet()) {
						if (valueAttributeFilter.contains(keyAttribute)
								&& valueAttributeFilter.endsWith(HeaderUtils
								.normalizeValueAttributeFilter(mapAttributes.get(
								keyAttribute).trim()))) {
							instancesFiltrated.add(instance);
						}
					}
				}
			}
		}
		if (filterCategory.size() != 0 && thereIsntCategory) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
		}
		return instancesFiltrated;
	}

	private List<Instance> getInstancesFiltrated(OCCIApplication application, String authToken) {
		List<Instance> allInstances = application.getFullInstances(authToken);		
		return allInstances;
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