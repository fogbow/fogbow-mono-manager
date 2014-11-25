package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
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
		Series<Header> headers = req.getHeaders();
		checkValidAccept(HeaderUtils.getAccept(headers));
		if (getRequest().getMethod().equals(Method.HEAD)){
			LOGGER.debug("It is a HEAD method request");
			String token = headers.getValues(OCCIHeaders.X_AUTH_TOKEN);
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
			String authToken = HeaderUtils.getAuthToken(headers, getResponse(),
					application.getAuthenticationURI());
			LOGGER.debug("Auth Token = " + authToken);
			List<Resource> allResources = application.getAllResources(authToken);
			LOGGER.debug("Fogbow resources = " + allResources);
									
			List<String> filterCategory = HeaderUtils.getValueHeaderPerName(OCCIHeaders.CATEGORY,
					headers);
			headers.removeAll(OCCIHeaders.CATEGORY);
			
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
			response = filterQuery(filterCategories, response, true);				
		}
		
		return "\n" + response.trim();
	}

	private String filterQuery(List<String> filterCategories, String response,
			boolean addResourceFiltrated) {
		String[] allResourcesStr = response.split("\n");
		String newResponse = "";
		List<String> listFindCategoryRelatedTo = new ArrayList<String>();
		for (String filterCategory : filterCategories) {
			for (String resourceStr : allResourcesStr) {
				String[] featuresFilterCategory = filterCategory.split(";");
				String resourceTerm = resourceStr.split(";")[0];
				if (featuresFilterCategory.length != 0
						&& resourceTerm.endsWith(featuresFilterCategory[0]) && addResourceFiltrated) {
					checkSchemeAndClass(resourceStr, featuresFilterCategory);
					newResponse += resourceStr + "\n";
				} else {
					String referenceFilterCategory = normalizeRelFilterCategory(featuresFilterCategory);
					String[] featuresResource = resourceStr.split(";");
					for (String feature : featuresResource) {
						if (feature.contains("rel=") && feature.contains(referenceFilterCategory)) {
							newResponse += resourceStr + "\n";
							listFindCategoryRelatedTo.add(resourceStr);
						}
					}
				}
			}
		}
		try {
			if (listFindCategoryRelatedTo.size() != 0) {
				newResponse += filterQuery(listFindCategoryRelatedTo, response, false);
			}
		} catch (Exception e) {}
		if (newResponse.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
		}
		return newResponse;
	}

	private String normalizeRelFilterCategory(String[] featuresFilterCategory) {
		try {			
			String[] partsOfTerm = featuresFilterCategory[0].trim().split(":");
			String scheme = featuresFilterCategory[1].trim().split("=")[1].replace("\"", "");
			String term;
			if (partsOfTerm.length > 1) {
				term = partsOfTerm[1].trim();
			} else {
				term = partsOfTerm[0].trim();
			}
			return (scheme + term).trim();		
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
		}
	}

	private void checkSchemeAndClass(String resource, String[] featuresCategory) {
		for (String feature : featuresCategory) {
			feature = feature.trim();
			if ((feature.contains(OCCIHeaders.SCHEME_CATEGORY) && !resource
					.contains(feature))
					|| ((feature.contains(OCCIHeaders.CLASS_CATEGORY) && !resource
							.contains(feature)))) {
				System.out.println(resource);
				System.out.println(feature);
				System.out.println((feature.contains(OCCIHeaders.SCHEME_CATEGORY)));
				System.out.println(resource.contains(feature));
				System.out.println((feature.contains(OCCIHeaders.CLASS_CATEGORY)));
				System.out.println(resource.contains(feature));
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
