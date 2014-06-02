package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class ResourceRepository {

	private static final String FOGBOWCLOUD_ENDPOINT = "http://localhost:8182";

	public static List<Resource> getAll() {
		Resource fogbowRequest = new Resource(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.CLASS, RequestAttribute.getValues(), new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/request", "Request new Instances", "");

		// size flavors
		// TODO check actions
		Resource fogbowSmallFlavor = new Resource(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/small",
				"Small Flavor", "");
		Resource fogbowMediumFlavor = new Resource(RequestConstants.MEDIUM_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/medium",
				"Medium Flavor", "");
		Resource fogbowLargeFlavor = new Resource(RequestConstants.LARGE_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/large",
				"Large Flavor", "");
		// image flavors
		Resource fogbowLinuxX86Flavor = new Resource(RequestConstants.LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/"
						+ RequestConstants.LINUX_X86_TERM, "Linux-x86 Image", "");

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(fogbowRequest);
		resources.add(fogbowSmallFlavor);
		resources.add(fogbowMediumFlavor);
		resources.add(fogbowLargeFlavor);
		resources.add(fogbowLinuxX86Flavor);
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
