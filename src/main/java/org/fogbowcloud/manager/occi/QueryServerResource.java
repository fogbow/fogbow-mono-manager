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
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class QueryServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(QueryServerResource.class);

	@Get
	public StringRepresentation fetch() {
		LOGGER.debug("Executing the query interface fetch method");
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		Series<Header> headers = req.getHeaders();
		List<String> listAccept = HeaderUtils.getAccept(headers);
		String acceptType = getAccept(listAccept);
		
		if (getRequest().getMethod().equals(Method.HEAD)){
			LOGGER.debug("It is a HEAD method request");
			String token = headers.getValues(OCCIHeaders.X_AUTH_TOKEN);
			LOGGER.debug("Auth Token = " + token);
			if (token == null || token.equals("")) {
				HeaderUtils.setResponseHeader(getResponse(), HeaderUtils.WWW_AUTHENTICATE,
						application.getAuthenticationURI());
				getResponse().setStatus(new Status(HttpStatus.SC_UNAUTHORIZED));
			}
			return new StringRepresentation("");
		} else {
			LOGGER.debug("It is a GET method request");
			String authToken = HeaderUtils.getAuthToken(headers, getResponse(),
					application.getAuthenticationURI());
			LOGGER.debug("Auth Token = " + authToken);
			List<Resource> allResources = application.getAllResources(authToken);
			LOGGER.debug("Fogbow resources = " + allResources);
									
			List<String> filterCategory = HeaderUtils.getValueHeaderPerName(OCCIHeaders.CATEGORY,
					headers);
			headers.removeAll(OCCIHeaders.CATEGORY);

			Response response = new Response(getRequest());

			normalizeRequest();
			application.bypass(getRequest(), response);

			if (response.getStatus().getCode() == HttpStatus.SC_OK) {
				try {
					String localCloudResources = response.getEntity().getText();
					LOGGER.debug("Local cloud resources: " + localCloudResources);
					return generateResponse(allResources, localCloudResources, filterCategory,
							acceptType);
				} catch (Exception e) {
					LOGGER.error("Exception while reading local cloud resources ...", e);
				}
			}		

			return generateResponse(allResources, "", filterCategory, acceptType);
		}		
	}
	
	@SuppressWarnings("unchecked")
	private void normalizeRequest() {
		Series<org.restlet.engine.header.Header> requestHeaders = (Series<org.restlet.engine.header.Header>) getRequest()
				.getAttributes().get("org.restlet.http.headers");
		requestHeaders.removeAll("Accept");
		requestHeaders.add(new Header("Accept", "text/plain"));
	}
	
	private String getAccept(List<String> listAccept) {
		if (listAccept.size() > 0) {
			if (listAccept.get(0).contains(MediaType.TEXT_PLAIN.toString())) {
				return MediaType.TEXT_PLAIN.toString();
			} else if (listAccept.get(0).contains(OCCIHeaders.OCCI_CONTENT_TYPE)) {
				return OCCIHeaders.OCCI_CONTENT_TYPE;
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
						ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
			}
		} else {
			return "";
		}
	}
	
	private StringRepresentation generateResponse(List<Resource> fogbowResources, String localCloudResources,
			List<String> filterCategories, String acceptType) {		
		
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
		
		if (acceptType.equals(OCCIHeaders.OCCI_ACCEPT)) {
			setLocationHeader(response.trim().replace("\n", ", ").replace("Category: ", ""));
			return new StringRepresentation(ResponseConstants.OK, new MediaType(
					OCCIHeaders.OCCI_ACCEPT));
		}				
		
		return new StringRepresentation("\n" + response.trim()); 
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
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setLocationHeader(String response) {
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get(
				"org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Series(Header.class);
			getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);
		}		
		
		responseHeaders.add(new Header("Category", response));
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
