package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.FogbowResource;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.request.RequestUnit;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class RequestResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = HeaderUtils.getToken(req.getHeaders());

		return HeaderUtils.generateResponseId(
				application.getRequestsFromUser(userToken), req);
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = HeaderUtils.getToken(req.getHeaders());
		application.removeAllRequests(userToken);
		return "OK";
	}

	@Post
	public String post() {		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		
		List<FogbowResource> requestResources = getRequestResources(req);
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
			
		String userToken = HeaderUtils.getToken(req.getHeaders());
		
		//check
//		HeaderUtils.checkFogbowHeaders(req.getHeaders()); 
		
		HeaderUtils.checkOCCIContentType(req.getHeaders());
				
		HeaderUtils.checkXOCCIAtt(xOCCIAtt);
		
		xOCCIAtt = HeaderUtils.addDefaultValuesOnXOCCIAtt(xOCCIAtt);
		
		int numberOfInstances = HeaderUtils.getNumberOfInstances(req.getHeaders());
		
		List<RequestUnit> currentRequestUnits = new ArrayList<RequestUnit>();
		for (int i = 0; i < numberOfInstances; i++) {			
			currentRequestUnits.add(application.newRequest(userToken, 
					requestResources, xOCCIAtt));
		}
		return HeaderUtils.generateResponseId(currentRequestUnits, req);
	}
	
	private static List<FogbowResource> getRequestResources(HttpRequest req) {
		List<FogbowResource> possibleResources = HeaderUtils.getPossibleResources();
		List<Category> requestCategories = HeaderUtils.getListCategory(req.getHeaders());

		List<FogbowResource> requestResources = new ArrayList<FogbowResource>();
		for (Category requestCategory : requestCategories) {
			for (FogbowResource fogbowResource : possibleResources) {
				if (fogbowResource.matches(requestCategory)) {
					requestResources.add(fogbowResource);
					break; // TODO get out this for?
				}
			}
		}
		if (requestCategories.size() != requestResources.size()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Sintax Error.");
		}

		Map<String, String> attributes = HeaderUtils.getXOCCIAtributes(req.getHeaders());
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
						"There are unspported attributes in the request.");
			}
		}
		return requestResources;
	}	
}
