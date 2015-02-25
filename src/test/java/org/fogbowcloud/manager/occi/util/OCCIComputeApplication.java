package org.fogbowcloud.manager.occi.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackOCCIComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

public class OCCIComputeApplication extends Application {

	public static final String COMPUTE_TARGET = "/compute/";
	public static final String QUERY_INTERFACE_TARGET = "/-/";
	public static final String CORE_ATTRIBUTE_OCCI = "occi.compute.cores";
	public static final String MEMORY_ATTRIBUTE_OCCI = "occi.compute.memory";
	public static final String ARCHITECTURE_ATTRIBUTE_OCCI = "occi.compute.architecture";
	public static final String SPEED_ATTRIBUTE_OCCI = "occi.compute.speed";
	public static final String HOSTNAME_ATTRIBUTE_OCCI = "occi.compute.hostname";
	public static final String ID_CORE_ATTRIBUTE_OCCI = "occi.core.id";

	public static final String SMALL_FLAVOR_TERM = "m1-small";
	public static final String MEDIUM_FLAVOR_TERM = "m1-medium";
	public static final String LARGE_FLAVOR_TERM = "m1-large";
	public static final String INSTANCE_SCHEME = "http://schemas.openstack.org/compute/instance#";
	public static final String OS_SCHEME = "http://schemas.openstack.org/template/os#";
	public static final String RESOURCE_SCHEME = "http://schemas.openstack.org/template/resource#";
	
	private Map<String, List<String>> userToInstanceId;
	private Map<String, String> instanceIdToDetails;
	private InstanceIdGenerator idGenerator;
	private Map<String, String> keystoneTokenToUser;
	private static List<Resource> resources;
	
	static{
		//Faking compute resources
		resources = new ArrayList<Resource>();
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
		
		Resource compute = new Resource("compute", "http://schemas.ogf.org/occi/infrastructure#",
				RequestConstants.KIND_CLASS, computeAttributes, computeActions, "http://localhost:8787/compute/",
				"Compute Resource", "http://schemas.ogf.org/occi/core#resource");
		resources.add(compute);
		
		resources.add(new Resource(SMALL_FLAVOR_TERM, RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				"http://localhost:8787/m1-small/", "Flavor: m1.small ",
				"http://schemas.ogf.org/occi/infrastructure#resource_tpl"));

		resources.add(new Resource(MEDIUM_FLAVOR_TERM, RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				"http://localhost:8787/m1-medium/", "Flavor: m1.medium ",
				"http://schemas.ogf.org/occi/infrastructure#resource_tpl"));
		
		resources.add(new Resource(LARGE_FLAVOR_TERM, RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				"http://localhost:8787/m1-large/", "Flavor: m1.large ",
				"http://schemas.ogf.org/occi/infrastructure#resource_tpl"));
		
		List<String> networkAttributes = new ArrayList<String>();
		networkAttributes.add("occi.network.label");
		networkAttributes.add("occi.network.state{immutable}");
		networkAttributes.add("occi.network.vlan");
				
		List<String> networkActions = new ArrayList<String>();
		networkActions.add("http://schemas.ogf.org/occi/infrastructure/network/action#up");
		networkActions.add("http://schemas.ogf.org/occi/infrastructure/network/action#down");

		Resource network = new Resource("netowrk", "http://schemas.ogf.org/occi/infrastructure#",
				RequestConstants.KIND_CLASS, networkAttributes, networkActions, "http://localhost:8787/network/",
				"Network Resource", "http://schemas.ogf.org/occi/core#resource");
		resources.add(network);
		
		resources.add(new Resource("os_tpl", "http://schemas.ogf.org/occi/infrastructure#",
				RequestConstants.MIXIN_CLASS, new ArrayList<String>(), new ArrayList<String>(),
				"http://localhost:8787/os_tpl/", "", ""));

		List<String> restartAttributes = new ArrayList<String>();
		restartAttributes.add("method");
		
		resources.add(new Resource("restart", "http://schemas.ogf.org/occi/infrastructure/compute/action#",
				"action", restartAttributes, new ArrayList<String>(),
				"", "Restart a compute resource", ""));
	}

	private Map<String, Map<String, String>> termToAttributes;

	public OCCIComputeApplication() {
		userToInstanceId = new HashMap<String, List<String>>();
		instanceIdToDetails = new HashMap<String, String>();
		idGenerator = new InstanceIdGenerator();
		keystoneTokenToUser = new HashMap<String, String>();
		termToAttributes = new HashMap<String, Map<String, String>>();
				
		normalizeDefaultAttributes();
		
	}

	private void normalizeDefaultAttributes() {
		Map<String, String> attributesToValueSmall = new HashMap<String, String>();
		attributesToValueSmall.put(CORE_ATTRIBUTE_OCCI, "1");
		attributesToValueSmall.put(MEMORY_ATTRIBUTE_OCCI, "2");
		attributesToValueSmall.put(SPEED_ATTRIBUTE_OCCI, "0");
		this.termToAttributes.put(SMALL_FLAVOR_TERM , attributesToValueSmall);

		Map<String, String> attributesToValueMedium = new HashMap<String, String>();
		attributesToValueMedium.put(CORE_ATTRIBUTE_OCCI, "2");
		attributesToValueMedium.put(MEMORY_ATTRIBUTE_OCCI, "2520");
		attributesToValueMedium.put(SPEED_ATTRIBUTE_OCCI, "0");
		this.termToAttributes.put(MEDIUM_FLAVOR_TERM, attributesToValueMedium);

		Map<String, String> attributesToValueLarge = new HashMap<String, String>();
		attributesToValueLarge.put(CORE_ATTRIBUTE_OCCI, "3");
		attributesToValueLarge.put(MEMORY_ATTRIBUTE_OCCI, "3520");
		attributesToValueLarge.put(SPEED_ATTRIBUTE_OCCI, "0");
		this.termToAttributes.put(LARGE_FLAVOR_TERM, attributesToValueLarge);
		
		Map<String, String> attributesToValueUbuntu = new HashMap<String, String>();
		attributesToValueUbuntu.put(ARCHITECTURE_ATTRIBUTE_OCCI, "64");
		this.termToAttributes.put("cadf2e29-7216-4a5e-9364-cf6513d5f1fd", attributesToValueUbuntu);		
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(COMPUTE_TARGET, ComputeServer.class);
		router.attach(COMPUTE_TARGET + "{instanceid}", ComputeServer.class);
		router.attach(QUERY_INTERFACE_TARGET, QueryServer.class);
		return router;
	}

	public List<String> getAllInstanceIds(String authToken) {
		checkUserToken(authToken);
		String user = keystoneTokenToUser.get(authToken);
		return userToInstanceId.get(user);
	}

	public String getInstanceDetails(String authToken, String instanceId) {
		checkUserToken(authToken);
		checkInstanceId(authToken, instanceId);
		return instanceIdToDetails.get(instanceId);
	}

	protected void setIdGenerator(InstanceIdGenerator idGenerator) {
		this.idGenerator = idGenerator;
	}

	public void putTokenAndUser(String authToken, String user) {
		this.keystoneTokenToUser.put(authToken, user);
	}

	public void removeAllInstances(String authToken) {
		checkUserToken(authToken);
		String user = keystoneTokenToUser.get(authToken);

		if (userToInstanceId.get(user) != null) {
			for (String instanceId : userToInstanceId.get(user)) {
				instanceIdToDetails.remove(instanceId);
			}
			userToInstanceId.remove(user);
		}
	}

	public String newInstance(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt, String link) {
		checkUserToken(authToken);
		String user = keystoneTokenToUser.get(authToken);
		if (userToInstanceId.get(user) == null) {
			userToInstanceId.put(user, new ArrayList<String>());
		}

		checkRules(categories, xOCCIAtt);
		for (Category category : categories) {
			Map<String, String> attributesPerTerm = termToAttributes.get(category.getTerm());
			if (attributesPerTerm != null) {
				xOCCIAtt.putAll(attributesPerTerm);
			}
		}
		//default machine size
		if (!xOCCIAtt.containsKey(CORE_ATTRIBUTE_OCCI)){
			xOCCIAtt.putAll(termToAttributes.get("m1-small"));
		}

		String instanceId = idGenerator.generateId();
		
		if(!xOCCIAtt.containsKey(HOSTNAME_ATTRIBUTE_OCCI)){
			xOCCIAtt.put(HOSTNAME_ATTRIBUTE_OCCI, "server-" + instanceId);
		}
		xOCCIAtt.put(ID_CORE_ATTRIBUTE_OCCI, instanceId);

		userToInstanceId.get(user).add(instanceId);
		String details = mountDetails(categories, xOCCIAtt, link);
		instanceIdToDetails.put(instanceId, details);
		return instanceId;
	}

	private void checkRules(List<Category> categories, Map<String, String> xOCCIAtt) {
		boolean OSFound = false;
		for (Category category : categories) {
			if (category.getScheme().equals(OpenStackOCCIComputePlugin.getOSScheme())) {
				OSFound = true;
				break;
			}
		}
		if (!OSFound) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_OS_TEMPLATE);
		}
		List<String> imutableAtt = new ArrayList<String>();
		imutableAtt.add(ID_CORE_ATTRIBUTE_OCCI);
		imutableAtt.add(CORE_ATTRIBUTE_OCCI);
		imutableAtt.add(MEMORY_ATTRIBUTE_OCCI);
		imutableAtt.add(ARCHITECTURE_ATTRIBUTE_OCCI);
		imutableAtt.add(SPEED_ATTRIBUTE_OCCI);
		for (String attName : xOCCIAtt.keySet()) {
			if (imutableAtt.contains(attName)) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.UNSUPPORTED_ATTRIBUTES);
			}
		}
	}

	private String mountDetails(List<Category> categories, Map<String, String> xOCCIAtt, String link) {
		StringBuilder st = new StringBuilder();
		for (Category category : categories) {
			st.append(category.toHeader() + "\n");
		}
		
		if (link != null && !"".equals(link)){
			st.append(OCCIHeaders.LINK + ": " + link  + "\n");
		} else {
			st.append(OCCIHeaders.LINK + ": " + "</network/default/>; "
					+ "rel=\"http://schemas.ogf.org/occi/infrastructure#network\"; "
					+ "category=\"http://schemas.ogf.org/occi/infrastructure#networkinterface\"; \n");
		}
		
		for (String attName : xOCCIAtt.keySet()) {
			st.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": " + attName + "=" + "\""
					+ xOCCIAtt.get(attName) + "\"" + "\n");
		}
		return st.toString();
	}

	public void removeInstance(String authToken, String instanceId) {
		checkUserToken(authToken);
		checkInstanceId(authToken, instanceId);

		String user = keystoneTokenToUser.get(authToken);

		userToInstanceId.get(user).remove(instanceId);
		instanceIdToDetails.remove(instanceId);
	}

	private void checkInstanceId(String authToken, String instanceId) {
		String user = keystoneTokenToUser.get(authToken);

		if (userToInstanceId.get(user) == null || !userToInstanceId.get(user).contains(instanceId)) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	private void checkUserToken(String userToken) {
		if (keystoneTokenToUser.get(userToken) == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}

	public static List<Resource> getResources() {
		return resources;
	}
	
	public static class ComputeServer extends ServerResource {

		private static final Logger LOGGER = Logger.getLogger(ComputeServer.class);

		@Get
		public String fetch() {
			OCCIComputeApplication computeApplication = (OCCIComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String userToken = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);

			String instanceId = (String) getRequestAttributes().get("instanceid");

			if (instanceId == null) {
				LOGGER.info("Getting all instance ids from token :" + userToken);
				return generateResponse(
						computeApplication.getAllInstanceIds(userToken), req);
			}
			LOGGER.info("Getting request(" + instanceId + ") of token :" + userToken);
			return computeApplication.getInstanceDetails(userToken, instanceId);
		}
		
		private static String generateResponse(List<String> instances, HttpRequest req) {
			String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
			String result = "";
			if(instances != null){				
				for (String location : instances) {
					if (requestEndpoint.endsWith("/")){
						result += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint + location + "\n";
					} else {
						result += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint + "/" + location + "\n";			
					}
				}
			}
			return result.length() > 0 ? result.trim() : "\n";
		}

		@Post
		public StringRepresentation post() {
			OCCIComputeApplication application = (OCCIComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();

			List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
			HeaderUtils.checkOCCIContentType(req.getHeaders());
			Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
			String authToken = getAuthToken(req);
			String link = HeaderUtils.getLink(req.getHeaders());
			
			String computeEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
			String instanceId = application.newInstance(authToken, categories, xOCCIAtt, link);
			getResponse().setLocationRef(computeEndpoint + instanceId);
			return new StringRepresentation(ResponseConstants.OK, MediaType.TEXT_PLAIN);
		}
		
		private static String getAuthToken(HttpRequest req){
			return req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);
		}
	

		@Delete
		public String remove() {
			OCCIComputeApplication computeApplication = (OCCIComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String userToken = getAuthToken(req);
			String instanceId = (String) getRequestAttributes().get("instanceid");

			if (instanceId == null) {
				LOGGER.info("Removing all requests of token :" + userToken);
				computeApplication.removeAllInstances(userToken);
				return ResponseConstants.OK;
			}

			LOGGER.info("Removing instance(" + instanceId + ") of token :" + userToken);
			computeApplication.removeInstance(userToken, instanceId);
			return ResponseConstants.OK;
		}
	}
	
	public static class QueryServer extends ServerResource {
		
		private static final Logger LOGGER = Logger.getLogger(QueryServer.class);
		
		@Get
		public String fetch() {
			HttpRequest req = (HttpRequest) getRequest();
			String userToken = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);
			LOGGER.info("Getting query resource with token :" + userToken);			
			return generateQueryResonse(OCCIComputeApplication.getResources());
		}

		private String generateQueryResonse(List<Resource> resources) {
			String response = "";
			for (Resource resource : resources) {
				response += "Category: " + resource.toHeader() + "\n"; 
			}
			return "\n" + response.trim();
		}
	}
	
	public class InstanceIdGenerator {
		public String generateId() {
			return String.valueOf(UUID.randomUUID());
		}
	}
}
