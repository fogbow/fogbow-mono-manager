package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class ResourceRepository {
	
	public static List<Resource> getAll() {
		Resource fogbowRequest = new Resource(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.CLASS,
				RequestAttribute.getValues(), new ArrayList<String>(), "$EndPoint/request",
				"Request new Instances", "");

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(fogbowRequest);
		return resources;		
	}
	
	public static List<Resource> get(List<Category> categories) {
		List<Resource> allResources = getAll();
		List<Resource> requestResources = new ArrayList<Resource>();
		for (Category requestCategory : categories) {			
			for (Resource resource : allResources) {				
				if (resource.matches(requestCategory)) {
					requestResources.add(resource);
					break;
				}
			}
		}	
		return requestResources;
	}
	
	public static Resource get(String term) {
		List<Resource> allResources = getAll();
		for (Resource resource : allResources) {				
			if (resource.getCategory().getTerm().equals(term)) {
				return resource;
			}
		}
		return null;		
	}
}
