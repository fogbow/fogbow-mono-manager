package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class QueryServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(QueryServerResource.class);

	@Get
	public String fetch() {
		LOGGER.debug("Executing the query interface fetch method");
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		checkValidAccept(HeaderUtils.getAccept(req.getHeaders()));
		if (getRequest().getMethod().equals(Method.HEAD)){
			LOGGER.debug("It is a HEAD method request");
			String token = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);
			LOGGER.debug("Auth Token = " + token);
			if (token == null || token.equals("")) {
				HeaderUtils.setResponseHeader(getResponse(), HeaderUtils.WWW_AUTHENTICATE,
						application.getAuthenticationURI());
				getResponse().setStatus(new Status(HttpStatus.SC_UNAUTHORIZED));
			}
			return "";
		} else {
			LOGGER.debug("It is a GET method request");
			req = (HttpRequest) getRequest();
			String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
					application.getAuthenticationURI());
			LOGGER.debug("Auth Token = " + authToken);
			List<Resource> allResources = application.getAllResources(authToken);
			LOGGER.debug("Fogbow resources = " + allResources);
									
			List<String> filterCategory = getCategoryFiltersStr(req.getHeaders());
			Response response = new Response(getRequest());
			application.bypass(getRequest(), response);			
			if (response.getStatus().getCode() == HttpStatus.SC_OK){
				try {
					String localCloudResources = response.getEntity().getText();
					LOGGER.debug("Local cloud resources: " + localCloudResources);
					return generateResponse(allResources, localCloudResources, filterCategory);
				} catch (Exception e) {
					LOGGER.error("Exception while reading local cloud resources ...", e);
				}
			}				

			return generateResponse(allResources, "", filterCategory);
		}		
	}

	public List<String> getCategoryFiltersStr(Series<Header> series) {
		List<String> listCategory = new ArrayList<String>();
		for (Header header : series) {
			if (header.getName().equals("Category")) {
				listCategory.add(header.getValue());
			}
		}
		return listCategory;
	}
	
	private void checkValidAccept(List<String> listAccept) {
		if (listAccept.size() > 0 && !listAccept.contains(MediaType.TEXT_PLAIN.toString())) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
					ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
		}
	}

	private String generateResponse(List<Resource> fogbowResources, String localCloudResources,
			List<String> filterCategories) {		
		
		String response = "";
		for (Resource resource : fogbowResources) {
			response += "Category: " + resource.toHeader() + "\n"; 
		}
		
		//adding local cloud resources
		for (Resource localResource : getResourcesFromStr(localCloudResources)) {
			boolean alreadyExists = false;
			for (Resource fogResource : fogbowResources) {
				if (fogResource.matches(localResource)){
					alreadyExists = true;
					break;
				}
			}
			if (!alreadyExists) {
				response += "Category: " + localResource.toHeader() + "\n";
			}
		}		
		
		if (filterCategories != null && filterCategories.size() != 0) {
			response = filterQuery(filterCategories, response);
		}
		
		return "\n" + response.trim();
	}

	private String filterQuery(List<String> filterCategories, String response) {
		String[] allResources = response.split("\n");
		String newResponse = "";
		for (String category : filterCategories) {
			boolean notFoundCategory = true;
			for (String resource : allResources) {
				String[] featuresCategory = category.split(";");
				String resourceTerm = resource.split(";")[0];
				if (featuresCategory.length != 0 && resourceTerm.contains(featuresCategory[0])) {
					notFoundCategory = false;
					checkSintaxCategory(resource, featuresCategory);
					newResponse += resource + "\n";
				}
			}
			if (notFoundCategory == true) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
			}
		}
		return newResponse;
	}

	private void checkSintaxCategory(String resource, String[] featuresCategory) {
		for (String feature : featuresCategory) {
			if ((feature.contains(OCCIHeaders.SCHEME_CATEGORY) && !resource
					.contains(feature))
					&& ((feature.contains(OCCIHeaders.CLASS_CATEGORY) && !resource
							.contains(feature)))) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
			}
		}
	}
	
	private List<Resource> getResourcesFromStr(String resourcesStr) {
		String[] lines = resourcesStr.split("\n");
		List<Resource> resources = new ArrayList<Resource>();
		for (String line : lines) {
			if (line.contains(OCCIHeaders.CATEGORY)){
				resources.add(new Resource(line.substring(line.indexOf(":") + 1)));
			}
		}		
		return resources;
	}
}
