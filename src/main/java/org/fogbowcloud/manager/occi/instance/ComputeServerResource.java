package org.fogbowcloud.manager.occi.instance;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
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
		String federationAuthToken = HeaderUtils.getFederationAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());
		
		if (instanceId == null) {
			LOGGER.info("Getting all instances of token :" + federationAuthToken);
			
			List<String> filterCategory = HeaderUtils.getValueHeaderPerName(OCCIHeaders.CATEGORY,
					req.getHeaders());
			List<String> filterAttribute = HeaderUtils.getValueHeaderPerName(
					OCCIHeaders.X_OCCI_ATTRIBUTE, req.getHeaders());
			
			List<Instance> allInstances = new ArrayList<Instance>();
			if (filterCategory.size() != 0 || filterAttribute.size() != 0) {
				allInstances = filterInstances(getInstancesFiltered(application, federationAuthToken),
						filterCategory, filterAttribute);
			} else {
				allInstances = getInstances(application, federationAuthToken);			
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
				Instance instance = application.getInstance(federationAuthToken, instanceId);
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
				
				normalizeURIForBypass(req);
				normalizeHeadersForBypass(req);
				
				application.bypass(req, response); 			
				
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

	public static void normalizeURIForBypass(HttpRequest req) {
		String path = req.getResourceRef().getPath();
		if (path != null && path.contains(org.fogbowcloud.manager.occi.request.Request.SEPARATOR_GLOBAL_ID)) {
			String[] partOfInstanceId = path.split(org.fogbowcloud.manager.occi.request.Request.SEPARATOR_GLOBAL_ID);
			path = partOfInstanceId[0];
		}
		req.getResourceRef().setPath(path);
	}
	
	private static void normalizeHeadersForBypass(HttpRequest req) {
		String localAuthToken = req.getHeaders().getFirstValue(HeaderUtils.normalize(OCCIHeaders.X_LOCAL_AUTH_TOKEN));
		LOGGER.debug("Local Auth Token = " + localAuthToken);
		if (localAuthToken == null) {
			return;
		}
		req.getHeaders().removeFirst(HeaderUtils.normalize(OCCIHeaders.X_FEDERATION_AUTH_TOKEN));
		req.getHeaders().removeFirst(HeaderUtils.normalize(OCCIHeaders.X_LOCAL_AUTH_TOKEN));
		req.getHeaders().add(OCCIHeaders.X_AUTH_TOKEN, localAuthToken);
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

	private List<Instance> getInstancesFiltered(OCCIApplication application, String authToken) {
		List<Instance> allInstances = application.getInstancesFullInfo(authToken);		
		return allInstances;
	}

	private List<Instance> getInstances(OCCIApplication application, String authToken) {
		authToken = normalizeAuthToken(authToken);
		List<Instance> allInstances = application.getInstances(authToken);

		//Adding local instances created out of fogbow
		HttpRequest req = (HttpRequest) getRequest();
		Response response = new Response(req);
		normalizeHeadersForBypass(req);
		application.bypass(req, response);
		for (Instance instance : getInstancesCreatedOutOfFogbow(response, application)) {
			if (!allInstances.contains(instance)){
				allInstances.add(instance);
			}
		}
		return allInstances;
	}

	private String normalizeAuthToken(String authToken) {
		if (authToken.contains("Basic ")) {
			authToken = new String(Base64.decodeBase64(authToken.replace("Basic ", "")));			
		}
		return authToken;
	}

	private List<Instance> getInstancesCreatedOutOfFogbow(Response response,
			OCCIApplication application) {
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
								localInstances.add(new Instance(normalizeInstanceId(tokens[i].trim()
								+ org.fogbowcloud.manager.occi.request.Request.SEPARATOR_GLOBAL_ID
								+ application.getProperties().getProperty(ConfigurationConstants.XMPP_JID_KEY))));
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
		String federationAuthToken = HeaderUtils.getFederationAuthToken(req.getHeaders(), getResponse(), application.getAuthenticationURI());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		
		if (instanceId == null) {
			LOGGER.info("Removing all instances of token :" + federationAuthToken);
			return removeIntances(application, federationAuthToken);
		}
		
		LOGGER.info("Removing instance " + instanceId);		
		return removeInstance(application, federationAuthToken, instanceId);
	}

	private String removeInstance(OCCIApplication application, String federationAuthToken, String instanceId) {
		try {
			application.removeInstance(federationAuthToken, instanceId);
		} catch (OCCIException e) {
			//The request will be bypassed only if the error was not found
			if (e.getStatus().getCode() == HttpStatus.SC_NOT_FOUND){
				HttpRequest req = (HttpRequest) getRequest();
				Response response = new Response(req);
				
				normalizeURIForBypass(req);
				normalizeHeadersForBypass(req);
				
				application.bypass(req, response);
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
		HttpRequest req = (HttpRequest) getRequest();
		Response response = new Response(req);
		normalizeHeadersForBypass(req);
		try {
			application.bypass(req, response);
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