package org.fogbowcloud.manager.occi.request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class RequestServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(RequestServerResource.class);

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = HeaderUtils.getAuthToken(req.getHeaders());
		String requestId = (String) getRequestAttributes().get("requestId");

		if (requestId == null) {
			LOGGER.info("Getting all requests of token :" + userToken);
			return generateResponse(application.getRequestsFromUser(userToken), req);
		}

		LOGGER.info("Getting request(" + requestId + ") of token :" + userToken);
		Request request = application.getRequest(userToken, requestId);
		return request.toHttpMessageFormat();
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = HeaderUtils.getAuthToken(req.getHeaders());
		String requestId = (String) getRequestAttributes().get("requestId");

		if (requestId == null) {
			LOGGER.info("Removing all requests of token :" + userToken);
			application.removeAllRequests(userToken);
			return ResponseConstants.OK;
		}

		LOGGER.info("Removing request(" + requestId + ") of token :" + userToken);
		application.removeRequest(userToken, requestId);
		return ResponseConstants.OK;
	}

	@Post
	public String post() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();

		List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
		HeaderUtils.checkOCCIContentType(req.getHeaders());

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
		xOCCIAtt = normalizeXOCCIAtt(xOCCIAtt);

		String authToken = HeaderUtils.getAuthToken(req.getHeaders());

		List<Request> currentRequests = application.createRequests(authToken, categories, xOCCIAtt);
		return generateResponse(currentRequests, req);
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

		List<Resource> requestResources = ResourceRepository.getAll();
		for (String attributeName : xOCCIAtt.keySet()) {
			boolean supportedAtt = false;
			for (Resource resource : requestResources) {
				if (resource.supportAtt(attributeName)) {
					supportedAtt = true;
					break;
				}
			}
			if (!supportedAtt) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.UNSUPPORTED_ATTRIBUTES);
			}
		}

		return defOCCIAtt;
	}

	protected static void checkRequestType(String enumString) {
		for (int i = 0; i < RequestType.values().length; i++) {
			if (enumString.equals(RequestType.values()[i].getValue())) {
				return;
			}
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
	}
	
	protected static String generateResponse(List<Request> requests, HttpRequest req) {
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		String response = "";
		for (Request request : requests) {
			response += HeaderUtils.X_OCCI_LOCATION + requestEndpoint + "/" + request.getId() + "\n";
		}
		if (response.equals("")) {
			response = "Empty";
		}
		return response;
	}
}
