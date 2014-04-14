package org.fogbowcloud.manager.occi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

public class ComputeApplication extends Application {

	public static final String TARGET = "/compute";

	private Map<String, String> userToInstanceId;
	private Map<String, String> instanceIdToDetails;
	private static InstanceIdGenerator idGenerator;
	private Map<String, String> keystoneTokenToUser;

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(TARGET, ComputeServer.class);
		router.attach(TARGET + "/{instanceid}", ComputeServer.class);
		return router;
	}

	public List<String> getAllInstanceIds(String authToken) {
		// TODO Auto-generated method stub
		return null;

	}

	public String getInstanceDetails(String userToken, String instanceId) {
		// TODO Auto-generated method stub
		return null;
	}

	protected void setIdGenerator(InstanceIdGenerator idGenerator) {
		this.idGenerator = this.idGenerator;
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
//			String instanceId = String.valueOf(idGenerator.generateId());
//			
//			ComputeApplication application = (ComputeApplication) getApplication();
//			HttpRequest req = (HttpRequest) getRequest();
//
//			List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
//			HeaderUtils.checkOCCIContentType(req.getHeaders());
//
//			Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
//			xOCCIAtt = normalizeXOCCIAtt(xOCCIAtt);
//
//			String authToken = HeaderUtils.getAuthToken(req.getHeaders());
//			Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT
//					.getValue()));
//
//			LOGGER.info("Request " + instanceCount + " instances");
//
//			List<Request> currentRequestUnits = new ArrayList<Request>();
//			for (int i = 0; i < instanceCount; i++) {
//				currentRequestUnits.add(application.newRequest(authToken, categories, xOCCIAtt));
//			}
//			return HeaderUtils.generateResponseId(currentRequestUnits, req);
//			
//			
			return "";
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

	public void removeAllInstances(String userToken) {
		// TODO Auto-generated method stub
		
	}

	public void removeInstance(String userToken, String requestId) {
		// TODO Auto-generated method stub
		
	}
}
