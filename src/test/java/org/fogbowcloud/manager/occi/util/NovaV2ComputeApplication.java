package org.fogbowcloud.manager.occi.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

public class NovaV2ComputeApplication extends Application {

	public static final String SMALL_FLAVOR = "1";
	public static final String MEDIUM_FLAVOR = "2";
	public static final String LARGE_FLAVOR = "3";
	
	private final String SERVERS_V2_TARGET = "/v2/tenantid/servers";
	private final String FLAVORS_V2_TARGET = "/v2/tenantid/flavors";
	private String testDirPath;
	private int numberOfInstances;
	private Map<String, String> keystoneAccessIdToUser;
	private Map<String, List<String>> userToInstanceId;

	private static final Logger LOGGER = Logger.getLogger(NovaV2ComputeApplication.class);

	public NovaV2ComputeApplication(){
		numberOfInstances = 0;
		keystoneAccessIdToUser= new HashMap<String, String>();
		userToInstanceId = new HashMap<String, List<String>>();
	}
		
	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(SERVERS_V2_TARGET, NovaV2ComputeServer.class);
		router.attach(SERVERS_V2_TARGET + "/{serverid}", NovaV2ComputeServer.class);
		router.attach(FLAVORS_V2_TARGET + "/{flavorid}", FlavorServer.class);
		return router;
	}
	
	public String newInstance(String authToken, String json) {
		checkUserToken(authToken);
		String user = keystoneAccessIdToUser.get(authToken);
		if (userToInstanceId.get(user) == null) {
			userToInstanceId.put(user, new ArrayList<String>());
		}
		try {
			String jsonResponse = PluginHelper.getContentFile(testDirPath + "/response.post." + numberOfInstances);
			userToInstanceId.get(user).add(""+ numberOfInstances);
			numberOfInstances++;
			return jsonResponse;
		} catch (IOException e) {
			LOGGER.warn("Error while creating a new instance", e);
		}
		return null;
	}
	
	public void putTokenAndUser(String accessId, String username) {
		this.keystoneAccessIdToUser.put(accessId, username);
	
	}
	
	public void setTestDirPath(String testDirPath) {
		this.testDirPath = testDirPath;		
	}
	
	public String getTestDirPath() {
		return testDirPath;
	}
	
	public String getInstanceJson(String authToken, String instanceId) {
		checkUserToken(authToken);		
		if (!new File(testDirPath + "/response.getinstance." + instanceId).exists()) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
		try {
			String jsonResponse = PluginHelper.getContentFile(testDirPath
					+ "/response.getinstance." + instanceId);
			return jsonResponse;
		} catch (IOException e) {
			LOGGER.warn("Error while creating a new instance", e);
		}
		return null;
	}
	
	public String getAllInstancesJson(String authToken) {
		checkUserToken(authToken);
		try {
			String jsonResponse = PluginHelper.getContentFile(testDirPath + "/response.getinstances." + numberOfInstances);
			return jsonResponse;
		} catch (IOException e) {
			LOGGER.warn("Error while creating a new instance", e);
		}
		return null;
	}
	
	public void removeAllInstances(String accessId) {
		checkUserToken(accessId);
		String user = keystoneAccessIdToUser.get(accessId);
		List<String> instanceIds = userToInstanceId.get(user);
		for (String instanceId : instanceIds) {
			removeInstance(accessId, instanceId);
		}		
	}
	
	public void removeInstance(String accessId, String instanceId) {
		checkUserToken(accessId);
		checkInstanceId(accessId, instanceId);

		String user = keystoneAccessIdToUser.get(accessId);	
		userToInstanceId.get(user).remove(instanceId);
		numberOfInstances--;
	}

	private void checkInstanceId(String authToken, String instanceId) {
		String user = keystoneAccessIdToUser.get(authToken);

		if (userToInstanceId.get(user) == null || !userToInstanceId.get(user).contains(instanceId)) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}
	
	private void checkUserToken(String accessId) {
		if (keystoneAccessIdToUser.get(accessId) == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}
	
	public static class NovaV2ComputeServer extends ServerResource {
		
		private static final Logger LOGGER = Logger.getLogger(NovaV2ComputeServer.class);

		@Get
		public String fetch() {
			NovaV2ComputeApplication computeApplication = (NovaV2ComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String authToken = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);

			String instanceId = (String) getRequestAttributes().get("serverid");

			if (instanceId == null) {
				LOGGER.info("Getting all instance ids from token :" + authToken);
				return computeApplication.getAllInstancesJson(authToken);
			}
			LOGGER.info("Getting request(" + instanceId + ") of token :" + authToken);
			return computeApplication.getInstanceJson(authToken, instanceId);
		}
		
		@Post
		public StringRepresentation post(Representation entity) {
			if (entity.getMediaType().isCompatible(MediaType.APPLICATION_JSON)) {
				NovaV2ComputeApplication computeApplication = (NovaV2ComputeApplication) getApplication();
				HttpRequest req = (HttpRequest) getRequest();

				String json;
				try {
					json = req.getEntity().getText();
					HeaderUtils.checkJsonContentType(req.getHeaders());
					String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
							"Keystone uri=' http://localhost:5000'");
					return new StringRepresentation(
							computeApplication.newInstance(authToken, json),
							MediaType.APPLICATION_JSON);
				} catch (IOException e) {
					LOGGER.error("Error while post instance.", e);
				}
			}
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
				
		@Delete
		public String remove() {
			NovaV2ComputeApplication computeApplication = (NovaV2ComputeApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
					"Keystone uri=' http://localhost:5000'");
			String instanceId = (String) getRequestAttributes().get("serverid");

			if (instanceId == null) {
				LOGGER.info("Removing all requests of token :" + authToken);
				computeApplication.removeAllInstances(authToken);
				return ResponseConstants.OK;
			}

			LOGGER.info("Removing instance(" + instanceId + ") of token :" + authToken);
			computeApplication.removeInstance(authToken, instanceId);
			return "";
		}

	}
	
	public static class FlavorServer extends ServerResource {
		
		private static final Logger LOGGER = Logger.getLogger(FlavorServer.class);
		
		@Get
		public String fetch() {
			NovaV2ComputeApplication computeApplication = (NovaV2ComputeApplication) getApplication();
			String testDirPath = computeApplication.getTestDirPath();
			
			String flavorid = (String) getRequestAttributes().get("flavorid");
			
			if (flavorid == null || !new File(testDirPath + "/response.getflavor." + flavorid).exists()) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}			
			String flavorJson;
			try {
				flavorJson = PluginHelper.getContentFile(testDirPath + "/response.getflavor." + flavorid);				
				LOGGER.info("Getting request(" + flavorid + "):" + flavorJson);
				return flavorJson;
			} catch (IOException e) {
				LOGGER.error("Error while getting flavor.", e);
			}
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
	}

}
