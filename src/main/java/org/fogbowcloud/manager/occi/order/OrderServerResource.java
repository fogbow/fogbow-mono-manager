package org.fogbowcloud.manager.occi.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.bouncycastle.util.IPAddress;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.network.FedNetworkState;
import org.fogbowcloud.manager.occi.network.NetworkDataStore;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.adapter.ServerCall;
import org.restlet.engine.header.Header;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class OrderServerResource extends ServerResource {

	private static final String OCCI_CORE_TITLE = "occi.core.title";
	private static final String OCCI_CORE_ID = "occi.core.id";
	protected static final String NO_ORDERS_MESSAGE = "There are not orders.";
	protected static final String FED_NETWORK_PREFIX = "federated_network_";
	
	private static final Logger LOGGER = Logger.getLogger(OrderServerResource.class);
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
		HttpRequest req = (HttpRequest) getRequest();
		String federationAccessToken = HeaderUtils.getAuthToken(
				req.getHeaders(), getResponse(), application.getAuthenticationURI());
		String OrderId = (String) getRequestAttributes().get("orderId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());
		LOGGER.debug("accept contents:" + acceptContent);
		
		if (OrderId == null) {
			LOGGER.info("Getting all orders of token :" + federationAccessToken);
			boolean verbose = false;
			try {
				verbose = Boolean.parseBoolean(getQuery().getValues("verbose"));
			} catch (Exception e) {}
			List<Order> ordersFromUser = application.getOrdersFromUser(federationAccessToken);
			
			List<String> filterCategory = HeaderUtils.getValueHeaderPerName(OCCIHeaders.CATEGORY,
					req.getHeaders());
			List<String> filterAttribute = HeaderUtils.getValueHeaderPerName(
					OCCIHeaders.X_OCCI_ATTRIBUTE, req.getHeaders());
			
			if (filterCategory.size() != 0 || filterAttribute.size() != 0) {
				ordersFromUser = filterOrders(ordersFromUser, filterCategory, filterAttribute);
			}
			
			if (acceptContent.size() == 0
					|| acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(generateTextPlainResponse(
						ordersFromUser, req, verbose),
						MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				getResponse().setStatus(new Status(HttpStatus.SC_OK));
				return new StringRepresentation(generateURIListResponse(
						ordersFromUser, req, verbose),
						MediaType.TEXT_URI_LIST);
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
						ResponseConstants.ACCEPT_NOT_ACCEPTABLE);				
			}
		}

		LOGGER.info("Getting order(" + OrderId + ") of token :" + federationAccessToken);
		Order order = application.getOrder(federationAccessToken, OrderId);		
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			return new StringRepresentation(generateTextPlainResponseOneOrder(order), MediaType.TEXT_PLAIN);				
		}
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
				ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}

	private List<Order> filterOrders(List<Order> ordersFromUser,
			List<String> filterCategory, List<String> filterAttribute) {
		List<Order> ordersFiltrated = new ArrayList<Order>();
		boolean thereIsntCategory = true;
		for (Order order : ordersFromUser) {
			if (filterCategory.size() != 0) {
				for (String valueCategoryFilter : filterCategory) {
					for (Category category : order.getCategories()) {
						if (valueCategoryFilter.contains(category.getTerm())) {
							ordersFiltrated.add(order);
							thereIsntCategory = false;
						}
					}
				}
			}
			if (filterAttribute.size() != 0) {
				for (String valueAttributeFilter : filterAttribute) {
					Map<String, String> mapAttributes = order.getxOCCIAtt();
					for (String keyAttribute : mapAttributes.keySet()) {
						if (valueAttributeFilter.contains(keyAttribute)
								&& valueAttributeFilter.endsWith(HeaderUtils
								.normalizeValueAttributeFilter(mapAttributes
								.get(keyAttribute.trim())))) {
							ordersFiltrated.add(order);
						}
					}
				}
			}
		}
		if (filterCategory.size() != 0 && thereIsntCategory) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
		}
		return ordersFiltrated;
	}
	
	private String generateURIListResponse(List<Order> orders, HttpRequest req, boolean verbose) {
		if (orders == null || orders.isEmpty()) { 
			return "\n";
		}
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		String result = "";
		Iterator<Order> orderIt = orders.iterator();
		while(orderIt.hasNext()){
			Order order = orderIt.next();
			if (!requestEndpoint.endsWith("/")){
				requestEndpoint += requestEndpoint + "/";
			}
			if (verbose) {
				String providingMemberId = (order.getProvidingMemberId() == null) ? "None"
						: order.getProvidingMemberId();

				result += requestEndpoint + order.getId() + "; " + "State="
						+ order.getState() + "; " + OrderAttribute.TYPE.getValue() + "="
						+ order.getAttValue(OrderAttribute.TYPE.getValue()) + "; "
						+ OrderAttribute.REQUESTING_MEMBER.getValue() + "=" + order.getRequestingMemberId() + "; "
						+ OrderAttribute.PROVIDING_MEMBER.getValue() + "=" + providingMemberId + "; "
						+ OrderAttribute.INSTANCE_ID.getValue() + "="
						+ order.getGlobalInstanceId() + "\n";
						
			}else {			
				result += requestEndpoint + order.getId() + "\n";
			}
		}
		return result.length() > 0 ? result.trim() : "\n";
	}
	
	private String generateTextPlainResponseOneOrder(Order order) {
		LOGGER.debug("Generating response to order: " + order);
		String orderOCCIFormat = "\n";
		for (Category category : order.getCategories()) {
			LOGGER.debug("Category of order: " + order);
			Resource resource = ResourceRepository.getInstance().get(category.getTerm());
			if (resource == null && category.getScheme().equals(OrderConstants.TEMPLATE_OS_SCHEME)) {
				resource = ResourceRepository.createImageResource(category.getTerm());
			}
			LOGGER.debug("Resource exists? " + (resource != null));
			if (resource == null) {
				continue;
			}
			LOGGER.debug("Resource to header: " + resource.toHeader());
			try {
				orderOCCIFormat += "Category: " + resource.toHeader() + "\n";
			} catch (Exception e) {
				LOGGER.error(e);
			}
		}	
		
		Map<String, String> attToOutput = new HashMap<String, String>();		
		attToOutput.put(OCCI_CORE_ID, order.getId());
		if (order.getAttValue(OCCI_CORE_TITLE) != null){
			attToOutput.put(OCCI_CORE_TITLE, order.getAttValue(OCCI_CORE_TITLE));	
		}
		for (String attributeName : OrderAttribute.getValues()) {
			if (order.getAttValue(attributeName) == null){
				if(OrderAttribute.NETWORK_ID.getValue().equals(attributeName)){
					attToOutput.put(attributeName, "Network default");
				}else{
					attToOutput.put(attributeName, "Not defined");	
				}
			} else {
				
				if(OrderAttribute.NETWORK_ID.getValue().equals(attributeName) &&
						order.getAttValue(attributeName).isEmpty()){
					attToOutput.put(attributeName, "Network default");
				}else{
					attToOutput.put(attributeName, order.getAttValue(attributeName));
				}
			}
		}
		
		for (String attributeName : OCCIConstants.getValues()) {
			if (order.getAttValue(attributeName) == null){
				attToOutput.put(attributeName, "Not defined");	
			} else {
				attToOutput.put(attributeName, order.getAttValue(attributeName));
			}
		}
		
		attToOutput.put(OrderAttribute.STATE.getValue(), order.getState().getValue());
		attToOutput.put(OrderAttribute.REQUESTING_MEMBER.getValue(), order.getRequestingMemberId());
		if (order.getProvidingMemberId() == null) {
			attToOutput.put(OrderAttribute.PROVIDING_MEMBER.getValue(), "None");
		} else {
			attToOutput.put(OrderAttribute.PROVIDING_MEMBER.getValue(), order.getProvidingMemberId());
		}
		attToOutput.put(OrderAttribute.INSTANCE_ID.getValue(), order.getGlobalInstanceId());		
		
		for (String attName : attToOutput.keySet()) {
			orderOCCIFormat += OCCIHeaders.X_OCCI_ATTRIBUTE + ": " + attName + "=\""
					+ attToOutput.get(attName) + "\" \n";	
		}
			
		return "\n" + orderOCCIFormat.trim();
	}

	@Delete
	public String remove() {		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAccessToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String orderId = (String) getRequestAttributes().get("orderId");

		if (orderId == null) {
			LOGGER.info("Removing all orders of token :" + federationAccessToken);
			application.removeAllOrders(federationAccessToken);
			return ResponseConstants.OK;
		}

		LOGGER.info("Removing order(" + orderId + ") of token :" + federationAccessToken);
		application.removeOrder(federationAccessToken, orderId);
		return ResponseConstants.OK;
	}

	@SuppressWarnings("null")
	@Post
	public StringRepresentation post() {
		LOGGER.info("Posting a new order...");
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String acceptType = getAccept(HeaderUtils.getAccept(req.getHeaders()));
		
		List<Link> networkLinks = HeaderUtils.getLinks(req.getHeaders());
		LOGGER.debug("Network links: " + networkLinks);
		
		List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
		LOGGER.debug("Categories: " + categories);
		HeaderUtils.checkCategories(categories, OrderConstants.TERM);
		HeaderUtils.checkOCCIContentType(req.getHeaders());		
		
		String federationAuthToken = HeaderUtils.getAuthToken(
				req.getHeaders(), getResponse(), application.getAuthenticationURI());
		
		String user = application.getUser(normalizeAuthToken(federationAuthToken));
		
		//TODO verificar se o ID da network é federado
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
		for (Link link : networkLinks) {
			
			String networkId;
			
			networkId = link.getId(); 
			
			if(networkId != null && networkId.startsWith(FED_NETWORK_PREFIX)){
				
				FedNetworkState fedNetworkState = networkDB.getByFedNetworkId(networkId, user);

				if (fedNetworkState == null) {
					throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
				}
				
				Order relatedOrder = application.getOrder(federationAuthToken, fedNetworkState.getOrderId());
				
				networkId = relatedOrder.getGlobalInstanceId(); 
			}
			
			networkId = ManagerController.normalizeInstanceId(networkId);
			
			xOCCIAtt.put(OrderAttribute.NETWORK_ID.getValue(), networkId);
		}
		xOCCIAtt = normalizeXOCCIAtt(xOCCIAtt);
		
		xOCCIAtt = normalizeRequirements(categories, xOCCIAtt, application.getFlavorsProvided());
		
		List<Order> currentOrders = application.createOrders(federationAuthToken, categories, xOCCIAtt);
		if (currentOrders != null || !currentOrders.isEmpty()) {
			setStatus(Status.SUCCESS_CREATED);
		}		
		setLocationHeader(currentOrders, req);
		
		if (acceptType.equals(OCCIHeaders.OCCI_ACCEPT)) {
			return new StringRepresentation(ResponseConstants.OK, new MediaType(
					OCCIHeaders.OCCI_ACCEPT));	
		}
		return new StringRepresentation(ResponseConstants.OK);
	}

	static protected Map<String, String> normalizeRequirements(List<Category> categories, Map<String, String> xOCCIAtt, List<Flavor> listFlavorsFogbow) {
		String requirementsAttr = xOCCIAtt.get(OrderAttribute.REQUIREMENTS.getValue());
		if (requirementsAttr != null) {
			if (!RequirementsHelper.checkSyntax(requirementsAttr)) {
				throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.UNSUPPORTED_ATTRIBUTES);
			}			
		}				

		String flavorTerm = null;
		List<Category> copyListCategory = new ArrayList<Category>(categories);
		for (Category category : copyListCategory) {
			if (category.getScheme().equals(OrderConstants.TEMPLATE_RESOURCE_SCHEME)) {
				flavorTerm = category.getTerm();
				break;
			}
		}
		
		String flavorRequirements = null;
		if (flavorTerm != null && !flavorTerm.isEmpty()) {
			boolean thereIsFlavor = false;
			for (Flavor flavor : listFlavorsFogbow) {
				if (flavor.getName().equals(flavorTerm)) {
					flavorRequirements = RequirementsHelper.GLUE_MEM_RAM_TERM + ">=" + flavor.getMem()
							+ "&&" + RequirementsHelper.GLUE_VCPU_TERM + ">=" + flavor.getCpu();
					thereIsFlavor = true;
				}
			}
			if (!thereIsFlavor) {
				throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.STATIC_FLAVORS_NOT_SPECIFIED);			
			}
		}
		
		if (requirementsAttr != null && flavorRequirements != null) {
			requirementsAttr = "(" + requirementsAttr + ")&&(" + flavorRequirements + ")";
		} else if (flavorRequirements != null) {
			requirementsAttr = flavorRequirements;
		}
		
		if (requirementsAttr != null) {
			xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsAttr);			
		}
		
		return xOCCIAtt;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setLocationHeader(List<Order> orders, HttpRequest req) {
		String requestEndpoint = getHostRef(req) + req.getHttpCall().getRequestUri();
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get(
				"org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Series(Header.class);
			getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);
		}

		responseHeaders.add(new Header("Location",
				generateLocationHeader(orders, requestEndpoint)));
	}

	protected String generateLocationHeader(List<Order> orders, String requestEndpoint) {
		String response = "";
		for (Order order : orders) {
			String prefix = requestEndpoint;
			if (!prefix.endsWith("/")){
				prefix += "/";
			}			
			String locationHeader = prefix + order.getId();		
			
			response += locationHeader + ",";
		}
		return response.substring(0, response.length() - 1);
	}	

	private String getAccept(List<String> listAccept) {
		if (listAccept.size() > 0 ) {
			if (listAccept.get(0).contains(MediaType.TEXT_PLAIN.toString())) {
				return MediaType.TEXT_PLAIN.toString();			
			} else if (listAccept.get(0).contains(OCCIHeaders.OCCI_CONTENT_TYPE)) {
				return OCCIHeaders.OCCI_CONTENT_TYPE;				
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
						ResponseConstants.ACCEPT_NOT_ACCEPTABLE);				
			}
		} else {
			return "";
		}
	}
	
	public static Map<String, String> normalizeXOCCIAtt(Map<String, String> xOCCIAtt) {
		Map<String, String> defOCCIAtt = new HashMap<String, String>();
		
		checkOCCIAtt(xOCCIAtt);
 		
		defOCCIAtt.put(OrderAttribute.TYPE.getValue(), OrderConstants.DEFAULT_TYPE);
		defOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(),
				OrderConstants.DEFAULT_INSTANCE_COUNT.toString());

		defOCCIAtt.putAll(xOCCIAtt);

		checkOrderType(defOCCIAtt.get(OrderAttribute.TYPE.getValue()));
		HeaderUtils.checkDateValue(defOCCIAtt.get(OrderAttribute.VALID_FROM.getValue()));
		HeaderUtils.checkDateValue(defOCCIAtt.get(OrderAttribute.VALID_UNTIL.getValue()));
		HeaderUtils.checkIntegerValue(defOCCIAtt.get(OrderAttribute.INSTANCE_COUNT.getValue()));

		LOGGER.debug("Checking if all attributes are supported. OCCI attributes: " + defOCCIAtt);

		List<Resource> orderResources = ResourceRepository.getInstance().getAll();
		for (String attributeName : xOCCIAtt.keySet()) {
			boolean supportedAtt = false;
			for (Resource resource : orderResources) {
				if (resource.supportAtt(attributeName) || isOCCIAttribute(attributeName)) {
					supportedAtt = true;
					break;
				}
			}
			if (!supportedAtt) {
				LOGGER.debug("The attribute " + attributeName + " is not supported.");
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.UNSUPPORTED_ATTRIBUTES);
			}
		}
		return defOCCIAtt;
	}

	private static void checkOCCIAtt(Map<String, String> xOCCIAtt) {
		
		String resourceKind = xOCCIAtt.get(OrderAttribute.RESOURCE_KIND.getValue());
		if (resourceKind != null) {
			if (resourceKind.equals(OrderConstants.STORAGE_TERM)
					&& xOCCIAtt.get(OrderAttribute.STORAGE_SIZE.getValue()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.NOT_FOUND_STORAGE_SIZE_ATTRIBUTE);
			}
			if (resourceKind.equals(OrderConstants.NETWORK_TERM)){
				
				String address = xOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS);
				if(address == null || !validateIpCidr(address)){
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.NETWORK_ADDRESS_INVALID_VALUE);
				}
				
				String gateway = xOCCIAtt.get(OCCIConstants.NETWORK_GATEWAY);
				if(gateway == null || !validateIp(gateway)){
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.NETWORK_GATEWAY_INVALID_VALUE);
				}
				
				String allocation = xOCCIAtt.get(OCCIConstants.NETWORK_ALLOCATION);
				if(allocation == null || 
						(!OCCIConstants.NetworkAllocation.DYNAMIC.getValue().equals(allocation) 
								&& !OCCIConstants.NetworkAllocation.STATIC.getValue().equals(allocation))
						){
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.NETWORK_ALLOCATION_INVALID_VALUE);
				}
			}
		} else {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.NOT_FOUND_RESOURCE_KIND);
		}
	}

	private static boolean isOCCIAttribute(String attributeName) {
		return attributeName.equals(OCCI_CORE_ID) || attributeName.equals(OCCI_CORE_TITLE);
	}

	protected static void checkOrderType(String enumString) {
		for (int i = 0; i < OrderType.values().length; i++) {
			if (enumString.equals(OrderType.values()[i].getValue())) {
				return;
			}
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
	}

	protected String generateTextPlainResponse(List<Order> orders, HttpRequest req, boolean verbose) {
		if (orders == null || orders.isEmpty()) { 
			return NO_ORDERS_MESSAGE;
		}
		String requestEndpoint = getHostRef(req) + req.getHttpCall().getRequestUri();
		String response = "";
		Iterator<Order> orderIt = orders.iterator();
		while(orderIt.hasNext()){			
			Order order = orderIt.next();
			String prefixOCCILocation = "";
			if (requestEndpoint.endsWith("/")){
				prefixOCCILocation += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint;
			}else {
				prefixOCCILocation += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint + "/";
			}
			if (verbose) {
				response += prefixOCCILocation + order.getId() + "; "
						+ OrderAttribute.STATE.getValue() + "=" + order.getState() + "; "
						+ OrderAttribute.TYPE.getValue() + "="
						+ order.getAttValue(OrderAttribute.TYPE.getValue()) + "; "
						+ OrderAttribute.INSTANCE_ID.getValue() + "=" + order.getGlobalInstanceId() + "; "
						+ OrderAttribute.RESOURCE_KIND.getValue() + "=" 
						+ order.getAttValue(OrderAttribute.RESOURCE_KIND.getValue()) 
						+ "\n";
			}else {			
				response += prefixOCCILocation + order.getId() + "\n";
			}
		}
		return response.length() > 0 ? response.trim() : "\n";
	}

	// TODO remove this method, because there is the same method in the headerUtils class.
	private String getHostRef(HttpRequest req) {
		OCCIApplication application = (OCCIApplication) getApplication();
		String myIp = application.getProperties().getProperty("my_ip");
		ServerCall httpCall = req.getHttpCall();
		String hostDomain = myIp == null ? httpCall.getHostDomain() : myIp;
		return req.getProtocol().getSchemeName() + "://" + hostDomain + ":" + httpCall.getHostPort();
	}
	

	private static boolean validateIpCidr(String value){
		return IPAddress.isValidIPv4WithNetmask(value) || IPAddress.isValidIPv6WithNetmask(value);
	}
	
	private static boolean validateIp(String value){
		return IPAddress.isValidIPv4(value) || IPAddress.isValidIPv6(value);
	}
	
	private String normalizeAuthToken(String authToken) {
		if (authToken.contains("Basic ")) {
			authToken = new String(Base64.decodeBase64(authToken.replace("Basic ", "")));
		}
		return authToken;
	}
}
