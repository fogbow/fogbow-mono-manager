package org.fogbowcloud.manager.occi.request;

import java.util.HashMap;
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
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class RequestServerResource extends ServerResource {

	private static final String OCCI_CORE_TITLE = "occi.core.title";
	private static final String OCCI_CORE_ID = "occi.core.id";
	protected static final String NO_REQUESTS_MESSAGE = "There are not requests.";
	private static final Logger LOGGER = Logger.getLogger(RequestServerResource.class);
	
	@Get
	public StringRepresentation fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String accessId = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		String requestId = (String) getRequestAttributes().get("requestId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());
		LOGGER.debug("accept contents:" + acceptContent);
		
		if (requestId == null) {
			LOGGER.info("Getting all requests of token :" + accessId);
			if (acceptContent.size() == 0
					|| acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(generateTextPlainResponse(
						application.getRequestsFromUser(accessId), req), MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				getResponse().setStatus(new Status(HttpStatus.SC_OK));
				return new StringRepresentation(generateURIListResponse(
						application.getRequestsFromUser(accessId), req), MediaType.TEXT_URI_LIST);
			} else {
				throw new OCCIException(ErrorType.METHOD_NOT_ALLOWED,
						ResponseConstants.METHOD_NOT_SUPPORTED);
			}
		}

		LOGGER.info("Getting request(" + requestId + ") of token :" + accessId);
		Request request = application.getRequest(accessId, requestId);		
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			return new StringRepresentation(generateTextPlainResponseOneRequest(request), MediaType.TEXT_PLAIN);				
		}
		throw new OCCIException(ErrorType.METHOD_NOT_ALLOWED,
				ResponseConstants.METHOD_NOT_SUPPORTED);
	}
	
	private String generateURIListResponse(List<Request> requests, HttpRequest req) {
		if (requests == null || requests.isEmpty()) { 
			return "\n";
		}
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		String result = "";
		Iterator<Request> requestIt = requests.iterator();
		while(requestIt.hasNext()){
			if (requestEndpoint.endsWith("/")){
				result += requestEndpoint + requestIt.next().getId() + "\n";
			} else {
				result += requestEndpoint + "/" + requestIt.next().getId() + "\n";
			}
		}
		return result.length() > 0 ? result.trim() : "\n";
	}

	private String generateTextPlainResponseOneRequest(Request request) {
		LOGGER.debug("Generating response to request: " + request);
		String requestOCCIFormat = "\n";
		for (Category category : request.getCategories()) {
			LOGGER.debug("Category of request: " + request);
			LOGGER.debug("Resource exists? "
					+ (ResourceRepository.getInstance().get(category.getTerm()) != null));
			LOGGER.debug("Resource to header: "
					+ ResourceRepository.getInstance().get(category.getTerm()).toHeader());
			try {
				requestOCCIFormat += "Category: "
						+ ResourceRepository.getInstance().get(category.getTerm()).toHeader()
						+ "\n";
			} catch (Exception e) {
				LOGGER.error(e);
			}
		}	
		
		Map<String, String> attToOutput = new HashMap<String, String>();		
		attToOutput.put(OCCI_CORE_ID, request.getId());
		if (request.getAttValue(OCCI_CORE_TITLE) != null){
			attToOutput.put(OCCI_CORE_TITLE, request.getAttValue(OCCI_CORE_TITLE));	
		}
		for (String attributeName : RequestAttribute.getValues()) {
			if (request.getAttValue(attributeName) == null){
				attToOutput.put(attributeName, "Not defined");	
			} else {
				attToOutput.put(attributeName, request.getAttValue(attributeName));
			}
		}
		
		attToOutput.put(RequestAttribute.STATE.getValue(), request.getState().getValue());
		attToOutput.put(RequestAttribute.INSTANCE_ID.getValue(), request.getInstanceId());
		
		for (String attName : attToOutput.keySet()) {
			requestOCCIFormat += OCCIHeaders.X_OCCI_ATTRIBUTE + ": " + attName + "=\""
					+ attToOutput.get(attName) + "\" \n";	
		}
			
		return "\n" + requestOCCIFormat.trim();
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String accessId = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		String requestId = (String) getRequestAttributes().get("requestId");

		if (requestId == null) {
			LOGGER.info("Removing all requests of token :" + accessId);
			application.removeAllRequests(accessId);
			return ResponseConstants.OK;
		}

		LOGGER.info("Removing request(" + requestId + ") of token :" + accessId);
		application.removeRequest(accessId, requestId);
		return ResponseConstants.OK;
	}

	@Post
	public String post() {
		LOGGER.info("Posting a new request...");
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();

		List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
		LOGGER.debug("Categories: " + categories);
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
		HeaderUtils.checkOCCIContentType(req.getHeaders());

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
		xOCCIAtt = normalizeXOCCIAtt(xOCCIAtt);

		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		authToken = authToken.replace("{-}", "\n");
		authToken = authToken.replace("{--}", " ");
		
		List<Request> currentRequests = application.createRequests(authToken, categories, xOCCIAtt);
		return generateTextPlainResponse(currentRequests, req);
	}

	public static Map<String, String> normalizeXOCCIAtt(Map<String, String> xOCCIAtt) {
		Map<String, String> defOCCIAtt = new HashMap<String, String>();
		defOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestConstants.DEFAULT_TYPE);
		defOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				RequestConstants.DEFAULT_INSTANCE_COUNT.toString());

		defOCCIAtt.putAll(xOCCIAtt);

		checkRequestType(defOCCIAtt.get(RequestAttribute.TYPE.getValue()));
		HeaderUtils.checkDateValue(defOCCIAtt.get(RequestAttribute.VALID_FROM.getValue()));
		HeaderUtils.checkDateValue(defOCCIAtt.get(RequestAttribute.VALID_UNTIL.getValue()));
		HeaderUtils.checkIntegerValue(defOCCIAtt.get(RequestAttribute.INSTANCE_COUNT.getValue()));
		
		LOGGER.debug("Checking if all attributes are supported. OCCI attributes: " + defOCCIAtt);

		List<Resource> requestResources = ResourceRepository.getInstance().getAll();
		for (String attributeName : xOCCIAtt.keySet()) {
			boolean supportedAtt = false;
			for (Resource resource : requestResources) {
				if (resource.supportAtt(attributeName) || isOCCIAttribute(attributeName)) {
					supportedAtt = true;
					break;
				}
			}
			if (!supportedAtt) {
				LOGGER.debug("The attribute " + attributeName + " is not supported.");
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.UNSUPPORTED_ATTRIBUTES);
			}
		}
		return defOCCIAtt;
	}

	private static boolean isOCCIAttribute(String attributeName) {
		return attributeName.equals(OCCI_CORE_ID) || attributeName.equals(OCCI_CORE_TITLE);
	}

	protected static void checkRequestType(String enumString) {
		for (int i = 0; i < RequestType.values().length; i++) {
			if (enumString.equals(RequestType.values()[i].getValue())) {
				return;
			}
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
	}

	protected static String generateTextPlainResponse(List<Request> requests, HttpRequest req) {
		if (requests == null || requests.isEmpty()) { 
			return NO_REQUESTS_MESSAGE;
		}
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		String response = "";
		Iterator<Request> requestIt = requests.iterator();
		while(requestIt.hasNext()){
			if (requestEndpoint.endsWith("/")){
				response += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint + requestIt.next().getId();
			} else {
				response += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint + "/" + requestIt.next().getId();
			}
			if (requestIt.hasNext()){
				response += "\n";
			}
		}
		return response;
	}
}
