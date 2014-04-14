package org.fogbowcloud.manager.occi.model;

import java.awt.Image;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.plugins.openstack.ComputeOpenStackPlugin;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

public class ComputeApplication extends Application {

	public static final String TARGET = "/compute";

	private Map<String, List<String>> userToInstanceId;
	private Map<String, String> instanceIdToDetails;
	private static InstanceIdGenerator idGenerator;
	private Map<String, String> keystoneTokenToUser;

	public ComputeApplication() {
		userToInstanceId = new HashMap<String, List<String>>();
		instanceIdToDetails = new HashMap<String, String>();
		idGenerator = new InstanceIdGenerator();
		keystoneTokenToUser = new HashMap<String, String>();
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(TARGET, ComputeServer.class);
		router.attach(TARGET + "/{instanceid}", ComputeServer.class);
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
			Map<String, String> xOCCIAtt) {
		checkUserToken(authToken);
		String user = keystoneTokenToUser.get(authToken);
		if (userToInstanceId.get(user) == null) {
			userToInstanceId.put(user, new ArrayList<String>());
		}		
		checkRules(categories, xOCCIAtt);

		//TODO put right attributes
		
		String instanceId = idGenerator.generateId();
		userToInstanceId.get(user).add(instanceId);
		String details = mountDetails(categories, xOCCIAtt);
		instanceIdToDetails.put(instanceId, details);
		return instanceId;
	}

	private void checkRules(List<Category> categories, Map<String, String> xOCCIAtt) {
		boolean OSFound = false;
		for (Category category : categories) {
			if (category.getScheme().equals(ComputeOpenStackPlugin.OS_SCHEME)){
				OSFound = true;
				break;
			}
		}
		if (!OSFound){
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_OS_TEMPLATE);
		}
		List<String> imutableAtt = new ArrayList<String>();
		imutableAtt.add("occi.compute.cores");
		imutableAtt.add("occi.compute.memory");
		imutableAtt.add("occi.compute.architecture");
		imutableAtt.add("occi.compute.speed");
		
		for (String attName : xOCCIAtt.keySet()) {
			if(imutableAtt.contains(attName)){
				throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.UNSUPPORTED_ATTRIBUTES);
			}
		}
	}

	private String mountDetails(List<Category> categories, Map<String, String> xOCCIAtt) {
		StringBuilder st = new StringBuilder();
		for (Category category : categories) {
			st.append(category.toHeader() + "\n");
		}
		for (String attName : xOCCIAtt.keySet()) {
			st.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": " + attName + "=" + "\"" +xOCCIAtt.get(attName) + "\"");
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

	public static class ComputeServer extends ServerResource {

		private static final Logger LOGGER = Logger.getLogger(ComputeServer.class);

		@Get
		public String fetch() {
			ComputeApplication computeApplication = (ComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String userToken = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);

			String instanceId = (String) getRequestAttributes().get("instanceid");

			if (instanceId == null) {
				LOGGER.info("Getting all instance ids from token :" + userToken);
				return HeaderUtils.generateResponseInstanceLocations(
						computeApplication.getAllInstanceIds(userToken), req);
			}
			LOGGER.info("Getting request(" + instanceId + ") of token :" + userToken);
			return computeApplication.getInstanceDetails(userToken, instanceId);
		}

		@Post
		public String post() {
			ComputeApplication application = (ComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();

			List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
			HeaderUtils.checkOCCIContentType(req.getHeaders());
			Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
			String authToken = HeaderUtils.getAuthToken(req.getHeaders());

			String computeEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
			String instanceId = application.newInstance(authToken, categories, xOCCIAtt);
			return HeaderUtils.X_OCCI_LOCATION + computeEndpoint + "/" + instanceId;
		}

		@Delete
		public String remove() {
			ComputeApplication computeApplication = (ComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String userToken = HeaderUtils.getAuthToken(req.getHeaders());
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

	public class InstanceIdGenerator {
		public String generateId() {
			return String.valueOf(UUID.randomUUID());
		}
	}
}
