package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.model.ManagerItem;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.xmpp.packet.IQ;

public class ManagerFacade {
	
	private static final Logger LOGGER = Logger.getLogger(ManagerFacade.class);
	protected static final long PERIOD = 50;
	
	private final Timer timer = new Timer();
	private List<ManagerItem> members = new LinkedList<ManagerItem>();
	private RequestRepository requests = new RequestRepository();
	
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private Properties properties;
	
	public ManagerFacade(Properties properties) throws Exception {
		this.properties = properties;
		this.computePlugin = (ComputePlugin) createInstance(
				"compute_class", properties);
		this.identityPlugin = (IdentityPlugin) createInstance(
				"identity_class", properties);
	}
	
	private static Object createInstance(String propName, Properties properties)
			throws Exception {
		return Class.forName(properties.getProperty(propName))
				.getConstructor(Properties.class).newInstance(properties);
	}

	@SuppressWarnings("unchecked")
	public ArrayList<ManagerItem> getItemsFromIQ(
			IQ responseFromWhoIsAliveIQ) {
		Element queryElement = responseFromWhoIsAliveIQ.getElement().element(
				"query");
		Iterator<Element> itemIterator = queryElement.elementIterator("item");
		ArrayList<ManagerItem> aliveItems = new ArrayList<ManagerItem>();

		while (itemIterator.hasNext()) {
			Element itemEl = (Element) itemIterator.next();
			Attribute id = itemEl.attribute("id");
			Element statusEl = itemEl.element("status");
			String cpuIdle = statusEl.element("cpu-idle").getText();
			String cpuInUse = statusEl.element("cpu-inuse").getText();
			String memIdle = statusEl.element("mem-idle").getText();
			String memInUse = statusEl.element("mem-inuse").getText();
			ResourcesInfo resources = new ResourcesInfo(id.getValue(), cpuIdle,
					cpuInUse, memIdle, memInUse);
			ManagerItem item = new ManagerItem(resources);
			aliveItems.add(item);
		}
		updateMembers(aliveItems);
		return aliveItems;
	}

	public void updateMembers(List<ManagerItem> members) {
		if (members == null) {
			throw new IllegalArgumentException();
		}
		this.members = members;
	}

	public List<ManagerItem> getMembers() {
		return members;
	}
	
	public ResourcesInfo getResourcesInfo() {
		String token = identityPlugin.getToken(properties.getProperty("federation.user.name"), 
				properties.getProperty("federation.user.password"));
		return computePlugin.getResourcesInfo(token);
	}

	public String getUser(String authToken) {
		return identityPlugin.getUser(authToken);
	}

	public List<Request> getRequestsFromUser(String authToken) {
		checkUserToken(authToken);
		String user = getUser(authToken);
		return requests.getByUser(user);
	}
	
	private void checkUserToken(String authToken) {
		if (!identityPlugin.isValidToken(authToken)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}

	public void removeAllRequests(String authToken) {
		checkUserToken(authToken);
		String user = identityPlugin.getUser(authToken);
		requests.removeByUser(user);
	}

	public void removeRequest(String authToken, String requestId) {
		checkUserToken(authToken);
		checkRequestId(authToken, requestId);
		requests.remove(requestId);
	}
	
	private void checkRequestId(String authToken, String requestId) {
		String user = identityPlugin.getUser(authToken);
		if (requests.get(user, requestId) == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}
	
	public List<Instance> getInstances(String authToken) {
		checkUserToken(authToken);
		//TODO check other manager
		
		return this.computePlugin.getInstances(authToken);
	}

	public Instance getInstance(String authToken, String instanceId) {
		checkUserToken(authToken);
		//TODO check other manager
		
		return this.computePlugin.getInstance(authToken, instanceId);
	}

	public void removeInstances(String authToken) {
		checkUserToken(authToken);
		//TODO check other manager
		
		this.computePlugin.removeInstances(authToken);
	}

	public void removeInstance(String authToken, String instanceId) {
		checkUserToken(authToken);
		//TODO check other manager
		
		this.computePlugin.removeInstance(authToken, instanceId);
	}
	
	public Request getRequest(String authToken, String requestId) {
		checkUserToken(authToken);
		checkRequestId(authToken, requestId);
		return requests.get(requestId);
	}
	
	public List<Request> createRequests(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		checkUserToken(authToken);
		String user = getUser(authToken);

		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT
				.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, authToken, "", RequestState.OPEN, categories,
					xOCCIAtt);
			currentRequests.add(request);
			requests.addRequest(user, request);
		}
		return currentRequests;
	}

	private void submitRemoteRequest(Request request) {
		// TODO Auto-generated method stub

	}

	private void submitLocalRequest(Request request) {
		// Removing all xOCCI Attribute specific to request type
		Map<String, String> xOCCIAtt = request.getxOCCIAtt();
		for (String keyAttributes : RequestAttribute.getValues()) {
			xOCCIAtt.remove(keyAttributes);
		}
		String instanceLocation = computePlugin.requestInstance(request.getAuthToken(),
				request.getCategories(), xOCCIAtt);
		instanceLocation = instanceLocation.replace(HeaderUtils.X_OCCI_LOCATION, "").trim();
		request.setInstanceId(instanceLocation);
		request.setState(RequestState.FULFILLED);
	}
	
	private void submitRequests() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				checkAndSubmitOpenRequests();
			}
		}, 0, PERIOD);
	}

	private void checkAndSubmitOpenRequests() {
		for (Request request : requests.get(RequestState.OPEN)) {
			// TODO before submit request we have to check
			try {
				submitLocalRequest(request);
				request.setState(RequestState.FULFILLED);
			} catch (OCCIException e) {
				if (e.getStatus().equals(ErrorType.BAD_REQUEST)
						&& e.getMessage().contains(
								ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
					submitRemoteRequest(request); // FIXME submit more than
													// one at same time
				} else {
					// TODO set state to fail?
					request.setState(RequestState.FAILED);
//					throw e;
				}
			}
		}
	}
}
