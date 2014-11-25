package org.fogbowcloud.manager.occi.request;

import java.util.ArrayList;
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
import org.restlet.engine.adapter.ServerCall;
import org.restlet.engine.header.Header;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class RequestServerResource extends ServerResource {

	private static final String OCCI_CORE_TITLE = "occi.core.title";
	private static final String OCCI_CORE_ID = "occi.core.id";
	protected static final String NO_REQUESTS_MESSAGE = "There are not requests.";
	private static final Logger LOGGER = Logger.getLogger(RequestServerResource.class);
	
	@Get
	public StringRepresentation fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String accessId = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String requestId = (String) getRequestAttributes().get("requestId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());
		LOGGER.debug("accept contents:" + acceptContent);
		
		if (requestId == null) {
			LOGGER.info("Getting all requests of token :" + accessId);
			boolean verbose = false;
			try {
				verbose = Boolean.parseBoolean(getQuery().getValues("verbose"));
			} catch (Exception e) {}
			List<Request> requestsFromUser = application.getRequestsFromUser(accessId);
			
			List<String> filterCategory = HeaderUtils.getValueHeaderPerName(OCCIHeaders.CATEGORY,
					req.getHeaders());
			List<String> filterAttribute = HeaderUtils.getValueHeaderPerName(
					OCCIHeaders.X_OCCI_ATTRIBUTE, req.getHeaders());
			
			if (filterCategory.size() != 0 || filterAttribute.size() != 0) {
				requestsFromUser = filterRequests(requestsFromUser, filterCategory, filterAttribute);
			}
			
			if (acceptContent.size() == 0
					|| acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(generateTextPlainResponse(
						requestsFromUser, req, verbose),
						MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				getResponse().setStatus(new Status(HttpStatus.SC_OK));
				return new StringRepresentation(generateURIListResponse(
						requestsFromUser, req, verbose),
						MediaType.TEXT_URI_LIST);
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
						ResponseConstants.ACCEPT_NOT_ACCEPTABLE);				
			}
		}

		LOGGER.info("Getting request(" + requestId + ") of token :" + accessId);
		Request request = application.getRequest(accessId, requestId);		
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			return new StringRepresentation(generateTextPlainResponseOneRequest(request), MediaType.TEXT_PLAIN);				
		}
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
				ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}

	private List<Request> filterRequests(List<Request> requestsFromUser,
			List<String> filterCategory, List<String> filterAttribute) {
		List<Request> requestsFiltrated = new ArrayList<Request>();
		boolean thereIsntCategory = true;
		for (Request request : requestsFromUser) {
			if (filterCategory.size() != 0) {
				for (String valueCategoryFilter : filterCategory) {
					for (Category category : request.getCategories()) {
						if (valueCategoryFilter.contains(category.getTerm())) {
							requestsFiltrated.add(request);
							thereIsntCategory = false;
						}
					}
				}
			}
			if (filterAttribute.size() != 0) {
				for (String valueAttributeFilter : filterAttribute) {
					Map<String, String> mapAttributes = request.getxOCCIAtt();
					for (String keyAttribute : mapAttributes.keySet()) {
						if (valueAttributeFilter.contains(keyAttribute)
								&& valueAttributeFilter.endsWith(HeaderUtils
								.normalizeValueAttributeFilter(mapAttributes
								.get(keyAttribute.trim())))) {
							requestsFiltrated.add(request);
						}
					}
				}
			}
		}
		if (filterCategory.size() != 0 && thereIsntCategory) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
		}
		return requestsFiltrated;
	}
	
	private String generateURIListResponse(List<Request> requests, HttpRequest req, boolean verbose) {
		if (requests == null || requests.isEmpty()) { 
			return "\n";
		}
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		String result = "";
		Iterator<Request> requestIt = requests.iterator();
		while(requestIt.hasNext()){
			Request request = requestIt.next();
			if (!requestEndpoint.endsWith("/")){
				requestEndpoint += requestEndpoint + "/";
			}
			if (verbose) {
				result += requestEndpoint + request.getId() + "; " + "State="
						+ request.getState() + "; " + RequestAttribute.TYPE.getValue() + "="
						+ request.getAttValue(RequestAttribute.TYPE.getValue()) + "; "
						+ RequestAttribute.INSTANCE_ID.getValue() + "="
						+ request.getInstanceId() + "\n";
			}else {			
				result += requestEndpoint + request.getId() + "\n";
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
		String accessId = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
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

	@SuppressWarnings("null")
	@Post
	public StringRepresentation post() {
		LOGGER.info("Posting a new request...");
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		checkValidAccept(HeaderUtils.getAccept(req.getHeaders()));
		
		List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
		LOGGER.debug("Categories: " + categories);
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
		HeaderUtils.checkOCCIContentType(req.getHeaders());		
		
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
		xOCCIAtt = normalizeXOCCIAtt(xOCCIAtt);

		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		
		List<Request> currentRequests = application.createRequests(authToken, categories, xOCCIAtt);
		if (currentRequests != null || !currentRequests.isEmpty()) {
			setStatus(Status.SUCCESS_CREATED);
		}		
		setLocationHeader(currentRequests, req);
		
		return new StringRepresentation(ResponseConstants.OK);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setLocationHeader(List<Request> requests, HttpRequest req) {
		String requestEndpoint = getHostRef(req) + req.getHttpCall().getRequestUri();
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get(
				"org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Series(Header.class);
			getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);
		}

		responseHeaders.add(new Header("Location",
				generateLocationHeader(requests, requestEndpoint)));
	}

	protected String generateLocationHeader(List<Request> requests, String requestEndpoint) {
		String response = "";
		for (Request request : requests) {
			String prefix = requestEndpoint;
			if (!prefix.endsWith("/")){
				prefix += "/";
			}			
			String locationHeader = prefix + request.getId();		
			
			response += locationHeader + ",";
		}
		return response.substring(0, response.length() - 1);
	}	
	
	private void checkValidAccept(List<String> listAccept) {
		if (listAccept.size() > 0
				&& !listAccept.contains(MediaType.TEXT_PLAIN.toString())) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
					ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
		}
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

	protected String generateTextPlainResponse(List<Request> requests, HttpRequest req, boolean verbose) {
		if (requests == null || requests.isEmpty()) { 
			return NO_REQUESTS_MESSAGE;
		}
		String requestEndpoint = getHostRef(req) + req.getHttpCall().getRequestUri();
		String response = "";
		Iterator<Request> requestIt = requests.iterator();
		while(requestIt.hasNext()){			
			Request request = requestIt.next();
			String prefixOCCILocation = "";
			if (requestEndpoint.endsWith("/")){
				prefixOCCILocation += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint;
			}else {
				prefixOCCILocation += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint + "/";
			}
			if (verbose) {
				response += prefixOCCILocation + request.getId() + "; "
						+ RequestAttribute.STATE.getValue() + "=" + request.getState() + "; "
						+ RequestAttribute.TYPE.getValue() + "="
						+ request.getAttValue(RequestAttribute.TYPE.getValue()) + "; "
						+ RequestAttribute.INSTANCE_ID.getValue() + "=" + request.getInstanceId()
						+ "\n";
			}else {			
				response += prefixOCCILocation + request.getId() + "\n";
			}
		}
		return response.length() > 0 ? response.trim() : "\n";
	}

	private String getHostRef(HttpRequest req) {
		OCCIApplication application = (OCCIApplication) getApplication();
		String myIp = application.getProperties().getProperty("my_ip");
		ServerCall httpCall = req.getHttpCall();
		String hostDomain = myIp == null ? httpCall.getHostDomain() : myIp;
		return req.getProtocol().getSchemeName() + "://" + hostDomain + ":" + httpCall.getHostPort();
	}	
}
