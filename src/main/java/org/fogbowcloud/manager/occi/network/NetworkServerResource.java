package org.fogbowcloud.manager.occi.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.bouncycastle.util.IPAddress;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.restlet.data.Header;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.adapter.ServerCall;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class NetworkServerResource extends ServerResource {

	protected static final String NO_INSTANCES_MESSAGE = "There are no network instances.";
	protected static final String FED_NETWORK_PREFIX = "federated_network_";
	
	private static final String DEFAULT_INSTANCE_COUNT = "1";
	
	private static final Logger LOGGER = Logger.getLogger(NetworkServerResource.class);

	private NetworkDataStore networkDB;

	protected void doInit() throws ResourceException {

		Properties properties = ((OCCIApplication) getApplication()).getProperties();
		String networkDataStoreUrl = properties
				.getProperty(ConfigurationConstants.NETWORK_DATA_STORE_URL);
		networkDB = new NetworkDataStore(networkDataStoreUrl);

	};
	
	@Get
	public StringRepresentation fetch() {

		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest request = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(request.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		List<String> acceptContent = HeaderUtils.getAccept(request.getHeaders());

		String userId = application.getUserId(normalizeAuthToken(federationAuthToken));
		if (userId == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		String networkId = (String) getRequestAttributes().get("networkId");

		if (networkId == null) {

			LOGGER.info("Getting all instance(network) of token : [" + federationAuthToken + "]");

			List<Instance> allInstances = getInstances(application, federationAuthToken);
			LOGGER.debug("There are " + allInstances.size() + " networks related to auth_token " + federationAuthToken);
			
			//Verify federated networks.
			List<FedNetworkState> fedPostNotworks = networkDB.getAllByUser(userId);
			LOGGER.debug("There are " + fedPostNotworks.size() + " federated networks owened by user id " + userId);

			// replacing real instance id by fed_instance_id
			for (FedNetworkState currentNetwork : fedPostNotworks) {
				
				allInstances.add(new Instance(currentNetwork.getFedInstanceId()));

				String globalId = currentNetwork.getGlobalInstanceId();
				if (globalId == null || globalId.isEmpty()) {
					try {
						Order relatedOrder = application.getOrder(federationAuthToken, currentNetwork.getOrderId());
						if (relatedOrder.getState().in(OrderState.FULFILLED)) {
							globalId = relatedOrder.getGlobalInstanceId();
						}					
					} catch (Exception e) {
						LOGGER.error("Error while getting the relatedOrder of this instance.", e);
					}
				}

				if (globalId != null && allInstances.contains(new Instance(globalId))) {
					allInstances.remove(new Instance(globalId));
				}
			}
			
			if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(generateResponse(allInstances), MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				return new StringRepresentation(generateURIListResponse(allInstances, request),
						MediaType.TEXT_URI_LIST);
			}
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);

		} else {

			LOGGER.info(
					"Getting instance(network) with id [" + networkId + "] of token : [" + federationAuthToken + "]");


			Instance instance = null;

			if(networkId.startsWith(FED_NETWORK_PREFIX)){

				
				//String networkIdNormalized = 
				
				FedNetworkState fedNetworkState = networkDB.getByFedNetworkId(networkId, userId);

				if (fedNetworkState == null) {
					throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
				}
				
				Order relatedOrder = application.getOrder(federationAuthToken, fedNetworkState.getOrderId());
				
				if (!relatedOrder.getState().in(OrderState.FULFILLED)) {
					// instance is not ready for using yet
					instance = new Instance(networkId);
					instance.addAttribute(OCCIConstants.NETWORK_STATE, OCCIConstants.NetworkState.INACTIVE.getValue());
					instance.addAttribute(OCCIConstants.NETWORK_ADDRESS, fedNetworkState.getAddress());
					instance.addAttribute(OCCIConstants.NETWORK_ALLOCATION, fedNetworkState.getAllocation());
					instance.addAttribute(OCCIConstants.NETWORK_GATEWAY, fedNetworkState.getGateway());
					instance.addAttribute(OCCIConstants.NETWORK_LABEL, "Not defined");
					instance.addAttribute(OCCIConstants.NETWORK_VLAN, "Not defined");
					
				}else{

					instance = application.getInstance(federationAuthToken, relatedOrder.getGlobalInstanceId(),
							OrderConstants.NETWORK_TERM);
					
					//New instance to replace de fogbowID for federated id.
					instance = new Instance(networkId, instance.getResources(), instance.getAttributes(), 
							instance.getLinks(), instance.getState());

				}
				
				
				

			}else{
				if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {

					instance = application.getInstance(federationAuthToken, networkId,
							OrderConstants.NETWORK_TERM);
				}else{
					throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
				}
			}

			try {

				LOGGER.info("Instance " + instance);
				LOGGER.debug("Instance id: " + instance.getId());
				LOGGER.debug("Instance attributes: " + instance.getAttributes());
				LOGGER.debug("Instance links: " + instance.getLinks());
				LOGGER.debug("Instance resources: " + instance.getResources());
				LOGGER.debug("Instance OCCI format " + instance.toOCCIMessageFormatDetails());

				return new StringRepresentation(instance.toOCCIMessageFormatDetails(), MediaType.TEXT_PLAIN);

			} catch (OCCIException e) {
				throw e;
			}

		}
	}
	
	@Post
	public StringRepresentation post() {
		LOGGER.info("Posting a new compute...");

		OCCIApplication application = (OCCIApplication) getApplication();

		HttpRequest req = (HttpRequest) getRequest();
		String acceptType = getNetworkPostAccept(HeaderUtils.getAccept(req.getHeaders()));
		
		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String userId = application.getUserId(normalizeAuthToken(federationAuthToken));

		List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
		LOGGER.debug("Categories: " + categories);

		HeaderUtils.checkOCCIContentType(req.getHeaders());

		List<Resource> resources = ResourceRepository.getInstance().get(categories);
		if (resources.size() != categories.size()) {
			LOGGER.debug("Some categories was not found in available resources! Resources " + resources.size()
					+ " and categories " + categories.size());
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		boolean networkWasFound = false;
		for (Category category : categories) {
			if (category.getTerm().equals(OrderConstants.NETWORK_TERM)) {
				networkWasFound = true;
				break;
			}
		}

		if (!networkWasFound) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
		
		String address = xOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS);
		String gateway = xOCCIAtt.get(OCCIConstants.NETWORK_GATEWAY);
		String allocation = xOCCIAtt.get(OCCIConstants.NETWORK_ALLOCATION);
		
		this.checkOCCIAtt(address, gateway, allocation);
		
		List<Category> orderCategories = new ArrayList<Category>();
		orderCategories
				.add(new Category(OrderConstants.TERM, OrderConstants.SCHEME, OrderConstants.KIND_CLASS));

		Map<String, String> orderXOCCIAtt = new HashMap<String, String>();

		orderXOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(), DEFAULT_INSTANCE_COUNT);
		orderXOCCIAtt.put(OrderAttribute.TYPE.getValue(), OrderConstants.DEFAULT_TYPE);
		orderXOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		orderXOCCIAtt.putAll(xOCCIAtt);
		
		Instance instance = new Instance(FED_NETWORK_PREFIX + UUID.randomUUID().toString());
		
		List<Order> newOrder = application.createOrders(federationAuthToken, orderCategories, orderXOCCIAtt);

		if (newOrder != null && !newOrder.isEmpty()) {
			setStatus(Status.SUCCESS_CREATED);
		}else{
			throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, ResponseConstants.ORDER_NOT_CREATED);
		}

		Order relatedOrder = newOrder.get(0);
		
		FedNetworkState fedNetworkState = new FedNetworkState(instance.getId(),
				relatedOrder.getId(), "", userId, 
				address, allocation, gateway);
		networkDB.insert(fedNetworkState);

		if (acceptType.equals(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			String requestEndpoint = getHostRef(req) + req.getHttpCall().getRequestUri();
			return new StringRepresentation(HeaderUtils.X_OCCI_LOCATION_PREFIX+generateLocationHeader(instance, requestEndpoint)
					, new MediaType(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		}

		setLocationHeader(instance, req);
		return new StringRepresentation(ResponseConstants.OK);
	}
	

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String networkId = (String) getRequestAttributes().get("networkId");

		String userId = application.getUserId(normalizeAuthToken(federationAuthToken));
		if (userId == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		if (networkId == null) {
			LOGGER.info("Removing all instances(network) of token :" + federationAuthToken);
			networkDB.deleteAllFromUser(userId);
			return removeIntances(application, federationAuthToken);			
		}

		if (networkId.startsWith(FED_NETWORK_PREFIX)) {
			LOGGER.info("Removing federated instance " + networkId);
						
			FedNetworkState fedNetworkState = networkDB.getByFedNetworkId(networkId, userId);
			
			if (fedNetworkState == null) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			
			try {				
				Order relatedOrder = application.getOrder(federationAuthToken, fedNetworkState.getOrderId());
				
				application.removeInstance(federationAuthToken, relatedOrder.getGlobalInstanceId(), OrderConstants.NETWORK_TERM);
				application.removeOrder(federationAuthToken, relatedOrder.getId());
				networkDB.deleteByIntanceId(networkId, userId);				
			} catch (Exception e) {
				LOGGER.warn("Error while removing resources related to federated network " + networkId + ".", e);
				if(e instanceof OCCIException){
					throw (OCCIException) e;
				}else{
					return "Error while removing resources related to federated network " + networkId + ".";
				}
			}
			return ResponseConstants.OK;
		}
		
		LOGGER.info("Removing instance(network) " + networkId);
		application.removeInstance(federationAuthToken, networkId, OrderConstants.NETWORK_TERM);
		return ResponseConstants.OK;
	}

	private List<Instance> getInstances(OCCIApplication application, String authToken) {
		authToken = normalizeAuthToken(authToken);
		List<Instance> allInstances = application.getInstances(authToken, OrderConstants.NETWORK_TERM);
		return allInstances;
	}

	private String generateResponse(List<Instance> instances) {
		if (instances == null || instances.isEmpty()) {
			return NO_INSTANCES_MESSAGE;
		}
		String response = "";
		Iterator<Instance> instanceIt = instances.iterator();
		while (instanceIt.hasNext()) {
			response += instanceIt.next().toOCCIMessageFormatLocation();
			if (instanceIt.hasNext()) {
				response += "\n";
			}
		}
		return response;
	}

	private String generateURIListResponse(List<Instance> instances, HttpRequest req) {
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		Iterator<Instance> instanceIt = instances.iterator();
		String result = "";
		while (instanceIt.hasNext()) {
			if (requestEndpoint.endsWith("/")) {
				result += requestEndpoint + instanceIt.next().getId() + "\n";
			} else {
				result += requestEndpoint + "/" + instanceIt.next().getId() + "\n";
			}
		}
		return result.length() > 0 ? result.trim() : "\n";
	}

	private String removeIntances(OCCIApplication application, String authToken) {
		application.removeInstances(authToken, OrderConstants.NETWORK_TERM);
		return ResponseConstants.OK;
	}

	private String normalizeAuthToken(String authToken) {
		if (authToken.contains("Basic ")) {
			authToken = new String(Base64.decodeBase64(authToken.replace("Basic ", "")));
		}
		return authToken;
	}
	
	private String getNetworkPostAccept(List<String> listAccept) {
		if (listAccept.size() > 0) {
			if (listAccept.get(0).contains(MediaType.TEXT_PLAIN.toString())) {
				return MediaType.TEXT_PLAIN.toString();
			} else if (listAccept.get(0).contains(OCCIHeaders.OCCI_CONTENT_TYPE)) {
				return OCCIHeaders.OCCI_CONTENT_TYPE;
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
			}
		} else {
			return "";
		}
	}
	
	public String getHostRef(HttpRequest req) {
		OCCIApplication application = (OCCIApplication) getApplication();
		String myIp = application.getProperties().getProperty("my_ip");
		ServerCall httpCall = req.getHttpCall();
		String hostDomain = myIp == null ? httpCall.getHostDomain() : myIp;
		return req.getProtocol().getSchemeName() + "://" + hostDomain + ":" + httpCall.getHostPort();
	}
	
	private String generateLocationHeader(Instance instance, String requestEndpoint) {
		String prefix = requestEndpoint;
		if (!prefix.endsWith("/")) {
			prefix += "/";
		}
		return prefix + instance.getId();
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setLocationHeader(Instance instance, HttpRequest req) {
		String requestEndpoint = getHostRef(req) + req.getHttpCall().getRequestUri();
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Series(Header.class);
			getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);
		}

		responseHeaders.add(new Header("Location", generateLocationHeader(instance, requestEndpoint)));
	}
	
	private void checkOCCIAtt(String address, String gateway, String allocation){
		
		if(address == null || !validateIpCidr(address)){
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.NETWORK_ADDRESS_INVALID_VALUE);
		}
		
		if(gateway == null || !validateIp(gateway)){
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.NETWORK_GATEWAY_INVALID_VALUE);
		}
		
		if(allocation == null || 
				(!OCCIConstants.NetworkAllocation.DYNAMIC.getValue().equals(allocation) 
						&& !OCCIConstants.NetworkAllocation.STATIC.getValue().equals(allocation))
				){
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.NETWORK_ALLOCATION_INVALID_VALUE);
		}
	}
	
	private boolean validateIpCidr(String value){
		return IPAddress.isValidIPv4WithNetmask(value) || IPAddress.isValidIPv6WithNetmask(value);
	}
	
	private boolean validateIp(String value){
		return IPAddress.isValidIPv4(value) || IPAddress.isValidIPv6(value);
	}
}
