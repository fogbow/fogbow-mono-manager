package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.json.JSONObject;

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
	private List<Resource> resources = new ArrayList<Resource>();
	
	public static void init(Properties properties) {
		instance = new ResourceRepository(properties);
	}
	
	public static ResourceRepository getInstance() {
		if (instance == null) {
			instance = new ResourceRepository(new Properties());
		}
		return instance;
	}
	
	private ResourceRepository(Properties properties){
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
		
		// Flavors
		
		List<Resource> flavorsResources = new ArrayList<Resource>();
		for (Flavor flavor : getStaticFlavors(properties)) {
			flavorsResources.add(new Resource(flavor.getName(),
					RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS,
					new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/"
							+ flavor.getName() + "/", flavor.getName(), RESOURCE_TPL_OCCI_SCHEME));
		}
		
		if (!flavorsResources.isEmpty()) {
			resources.addAll(flavorsResources);			
		}	
		
		//TODO add actions	
		resources.add(fogbowRequest);
		resources.add(compute);
		resources.add(fogbowUserdata);

		Resource resourceTlp = new Resource(RESOURCE_TPL,
				SCHEMAS_OCCI_INFRASTRUCTURE, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/resource_tpl/",
				"", "");
		Resource osTlp = new Resource(OS_TPL,
				SCHEMAS_OCCI_INFRASTRUCTURE, RequestConstants.MIXIN_CLASS,
				new ArrayList<String>(), new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT + "/os_tpl/",
				"", "");
		
		Resource resource = new Resource("resource", "http://schemas.ogf.org/occi/core#",
				RequestConstants.KIND_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/resource/", "Resource",
				"http://schemas.ogf.org/occi/core#entity");

		List<String> entityAtt = new ArrayList<String>();
		entityAtt.add("occi.core.id");
		entityAtt.add("occi.core.title");
		Resource entity = new Resource("entity", "http://schemas.ogf.org/occi/core#",
				RequestConstants.KIND_CLASS, entityAtt, new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/entity/", "Entity", "");

		List<String> linkAtt = new ArrayList<String>();
		linkAtt.add("occi.core.source");
		linkAtt.add("occi.core.target");
		Resource link = new Resource("link", "http://schemas.ogf.org/occi/core#",
				RequestConstants.KIND_CLASS, linkAtt, new ArrayList<String>(), FOGBOWCLOUD_ENDPOINT
						+ "/link/", "Link", "http://schemas.ogf.org/occi/core#entity");
		
		resources.add(resource);
		resources.add(entity);
		resources.add(link);
		
		resources.add(resourceTlp);
		resources.add(osTlp);
	}
		
	public List<Resource> getAll() {
		return resources;
	}
	
	public static Resource generateFlavorResource(String flavorName) {
		if (flavorName == null || flavorName.isEmpty()) {
			return null;
		}
		return new Resource(flavorName, RequestConstants.TEMPLATE_RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/" + flavorName + "/", flavorName, RESOURCE_TPL_OCCI_SCHEME);
	}
	
	public void addImageResource(String imageName){
		Resource imageResource = createImageResource(imageName);
		if (!resources.contains(imageResource)){
			LOGGER.debug("Adding image resource: " + imageResource.toHeader());
			resources.add(imageResource);
		}
	}

	public static Resource createImageResource(String imageName) {
		Resource imageResource = new Resource(imageName, RequestConstants.TEMPLATE_OS_SCHEME,
				RequestConstants.MIXIN_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				FOGBOWCLOUD_ENDPOINT + "/" + imageName + "/", imageName + " image", OS_TPL_OCCI_SCHEME);
		return imageResource;
	}
	
	
	public void addTemplateResource(String imageName){
		Resource imageResource = createImageResource(imageName);
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
	
	public static List<Flavor> getStaticFlavors(Properties properties) {
		List<Flavor> flavors = new ArrayList<Flavor>();
		if (properties == null) {
			return flavors;
		}
		for (Object objectKey: properties.keySet()) {
			String key = objectKey.toString();
			if (key.startsWith(ConfigurationConstants.PREFIX_FLAVORS)) {
				String value = (String) properties.get(key);
				String cpu = getAttValue("cpu", value);
				String mem = getAttValue("mem", value);				
				flavors.add(new Flavor(key.replace(ConfigurationConstants.PREFIX_FLAVORS, ""), cpu, mem, "0"));
			}			
		}
		return flavors;
	}
	
	public static String getAttValue(String attName, String flavorSpec) {		
		try {
			JSONObject root = new JSONObject(flavorSpec);
			return root.getString(attName);
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * To be used only by tests
	 */	
	protected void reset(){
		instance = null;
	}
}
