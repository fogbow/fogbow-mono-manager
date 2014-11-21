package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class ResourceRepository {
	
	protected static final String OS_TPL = "os_tpl";
	protected static final String SCHEMAS_OCCI_INFRASTRUCTURE = "http://schemas.ogf.org/occi/infrastructure#";
	protected static final String RESOURCE_TPL = "resource_tpl";
	private static final Logger LOGGER = Logger.getLogger(ResourceRepository.class);
	private static final String OS_TPL_OCCI_SCHEME = "http://schemas.ogf.org/occi/infrastructure#os_tpl";
	private static final String RESOURCE_TPL_OCCI_SCHEME = "http://schemas.ogf.org/occi/infrastructure#resource_tpl";
	private static final String RESOURCE_OCCI_SCHEME = "http://schemas.ogf.org/occi/core#resource";
	private static ResourceRepository instance;
	private static final String FOGBOWCLOUD_ENDPOINT = "http://localhost:8182";
	List<Resource> resources = new ArrayList<Resource>();
	
	public static ResourceRepository getInstance(){
		if (instance == null) {
			instance = new ResourceRepository();
		}
		return instance;
	}
	
	private ResourceRepository(){
		//kind resources
		Resource fogbowRequest = new Resource(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS, RequestAttribute.getValues(), new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/" + RequestConstants.TERM + "/", "Request new Instances",
				RESOURCE_OCCI_SCHEME);
		
		//TODO implement properties of attributes. For example, {immutable}
		List<String> computeAttributes = new ArrayList<String>();
		computeAttributes.add("occi.compute.architecture");
		computeAttributes.add("occi.compute.state{immutable}");
		computeAttributes.add("occi.compute.speed");
		computeAttributes.add("occi.compute.memory");
		computeAttributes.add("occi.compute.cores");
		computeAttributes.add("occi.compute.hostname");
		
		List<String> computeActions = new ArrayList<String>();
		computeActions.add("http://schemas.ogf.org/occi/infrastructure/compute/action#start");
		computeActions.add("http://schemas.ogf.org/occi/infrastructure/compute/action#stop");
		computeActions.add("http://schemas.ogf.org/occi/infrastructure/compute/action#restart");
		computeActions.add("http://schemas.ogf.org/occi/infrastructure/compute/action#suspend");

		Resource compute = new Resource("compute", SCHEMAS_OCCI_INFRASTRUCTURE,
				RequestConstants.KIND_CLASS, computeAttributes, computeActions, FOGBOWCLOUD_ENDPOINT + "/" + "compute/",
				"Compute Resource", RESOURCE_OCCI_SCHEME);

		// size flavors
		Resource fogbowSmallFlavor = new Resource(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/"
						+ RequestConstants.SMALL_TERM + "/", "Small Flavor",
				RESOURCE_TPL_OCCI_SCHEME);
		Resource fogbowMediumFlavor = new Resource(RequestConstants.MEDIUM_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/"
						+ RequestConstants.MEDIUM_TERM + "/", "Medium Flavor",
				RESOURCE_TPL_OCCI_SCHEME);
		Resource fogbowLargeFlavor = new Resource(RequestConstants.LARGE_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/"
						+ RequestConstants.LARGE_TERM + "/", "Large Flavor",
				RESOURCE_TPL_OCCI_SCHEME);
		//userdata
		Resource fogbowUserdata = new Resource(RequestConstants.USER_DATA_TERM,
				RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/"
						+ RequestConstants.USER_DATA_TERM + "/", "", "");
						
		//public-key
		List<String> publicKeyAttributes = new ArrayList<String>();
		publicKeyAttributes.add(RequestAttribute.DATA_PUBLIC_KEY.getValue());
		Resource fogbowPublicKey = new Resource(RequestConstants.PUBLIC_KEY_TERM,
				RequestConstants.CREDENTIALS_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
				publicKeyAttributes, new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/"
						+ RequestConstants.PUBLIC_KEY_TERM + "/", "", "");		
		resources.add(fogbowPublicKey);
		
		//TODO add actions	
		resources.add(fogbowRequest);
		resources.add(compute);
		resources.add(fogbowSmallFlavor);
		resources.add(fogbowMediumFlavor);
		resources.add(fogbowLargeFlavor);
		resources.add(fogbowUserdata);

		Resource resourceTlp = new Resource(RESOURCE_TPL,
				SCHEMAS_OCCI_INFRASTRUCTURE, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/resource_tpl/",
				"", "");
		Resource osTlp = new Resource(OS_TPL,
				SCHEMAS_OCCI_INFRASTRUCTURE, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/os_tpl/",
				"", "");
		
		resources.add(resourceTlp);
		resources.add(osTlp);
	}
		
	public List<Resource> getAll() {
		return resources;
	}
	
	public void addImageResource(String imageName){
		Resource imageResource = new Resource(imageName, RequestConstants.TEMPLATE_OS_SCHEME,
				RequestConstants.MIXIN_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/" + imageName + "/", imageName + " image", OS_TPL_OCCI_SCHEME);
		if (!resources.contains(imageResource)){
			LOGGER.debug("Adding image resource: " + imageResource.toHeader());
			resources.add(imageResource);
		}
	}

	public List<Resource> get(List<Category> categories) {
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

	public Resource get(String term) {
		List<Resource> allResources = getAll();
		for (Resource resource : allResources) {
			if (resource.getCategory().getTerm().equals(term)) {
				return resource;
			}
		}
		return null;
	}
	
	/**
	 * To be used only by tests
	 */	
	protected void reset(){
		instance = null;
	}
}
