package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.FogbowResource;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.request.RequestUnit;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class RequestResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = HeaderUtils.getToken(req.getHeaders());
		String requestId = (String) getRequestAttributes().get("requestid");
		
		if (requestId == null) {
			return HeaderUtils.generateResponseId(application.getRequestsFromUser(userToken), req);
		}else{
			RequestUnit requestUnit = application.getRequestDetails(userToken, requestId);
			return requestUnit.toHttMessageFormat();
		}
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = HeaderUtils.getToken(req.getHeaders());
		String requestId = (String) getRequestAttributes().get("requestid");
		
		if (requestId == null) {
			application.removeAllRequests(userToken);
			return ResponseConstants.OK;
		} else {
			application.removeRequest(userToken, requestId);
			return ResponseConstants.OK;
		}
	}

	@Post
	public String post() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();

		List<FogbowResource> requestResources = getRequestResources(req.getHeaders());
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());

		String userToken = HeaderUtils.getToken(req.getHeaders());

		// check
		// HeaderUtils.checkFogbowHeaders(req.getHeaders());

		HeaderUtils.checkOCCIContentType(req.getHeaders());

		HeaderUtils.checkXOCCIAtt(xOCCIAtt);

		xOCCIAtt = HeaderUtils.addDefaultValuesOnXOCCIAtt(xOCCIAtt);

		int numberOfInstances = HeaderUtils.getNumberOfInstances(req.getHeaders());

		List<RequestUnit> currentRequestUnits = new ArrayList<RequestUnit>();
		for (int i = 0; i < numberOfInstances; i++) {
			currentRequestUnits.add(application.newRequest(userToken, requestResources, xOCCIAtt));
		}
		return HeaderUtils.generateResponseId(currentRequestUnits, req);
	}

	public static List<FogbowResource> getRequestResources(Series<Header> headers) {
		List<FogbowResource> possibleResources = HeaderUtils.getPossibleResources();
		List<Category> requestCategories = HeaderUtils.getListCategory(headers);

		boolean isValidFogbowRequest = false;
		List<FogbowResource> requestResources = new ArrayList<FogbowResource>();
		for (Category requestCategory : requestCategories) {
			for (FogbowResource fogbowResource : possibleResources) {				
				if (fogbowResource.matches(requestCategory)) {
					requestResources.add(fogbowResource);
					break; // TODO get out this for?
				}
			}
			if(requestCategory.getTerm().equals(FogbowResourceConstants.TERM)){
				isValidFogbowRequest = true;
			}
		}
		if (requestCategories.size() != requestResources.size() || !isValidFogbowRequest) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		Map<String, String> attributes = HeaderUtils.getXOCCIAtributes(headers);
		for (String attributeName : attributes.keySet()) {
			boolean supportedAtt = false;
			for (FogbowResource fogbowResource : requestResources) {
				if (fogbowResource.supportAtt(attributeName)) {
					supportedAtt = true;
					break;
				}
			}
			if (!supportedAtt) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.UNSUPPORTED_ATTRIBUTES);
			}
		}
		return requestResources;
	}
}
