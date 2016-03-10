package org.fogbowcloud.manager.occi.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.UserdataUtils;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
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
import org.restlet.Response;
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

public class ComputeServerResource extends ServerResource {

	private static final String DEFAULT_INSTANCE_COUNT = "1";
	protected static final String NO_INSTANCES_MESSAGE = "There are not instances.";
	private static final Logger LOGGER = Logger.getLogger(ComputeServerResource.class);
	protected static final String FED_INSTANCE_PREFIX = "federated_instance_";

	protected static final String OCCI_NETWORK_INTERFACE_GATEWAY = "occi.networkinterface.gateway";
	protected static final String OCCI_NETWORK_INTERFACE_MAC = "occi.networkinterface.mac";
	protected static final String OCCI_NETWORK_INTERFACE = "occi.networkinterface.interface";
	protected static final String OCCI_NETWORK_INTERFACE_STATE = "occi.networkinterface.state";
	protected static final String OCCI_NETWORK_INTERFACE_ALLOCATION = "occi.networkinterface.allocation";
	protected static final String OCCI_NETWORK_INTERFACE_ADDRESS = "occi.networkinterface.address";
	protected static final String OCCI_CORE_SOURCE = "occi.core.source";
	protected static final String OCCI_CORE_TARGET = "occi.core.target";
	protected static final String OCCI_CORE_ID = "occi.core.id";

	private InstanceDataStore instanceDB;

	protected void doInit() throws ResourceException {

		Properties properties = ((OCCIApplication) getApplication()).getProperties();
		String instanceDSUrl = properties
				.getProperty(ConfigurationConstants.INSTANCE_DATA_STORE_URL);
		instanceDB = new InstanceDataStore(instanceDSUrl);

	};

	@SuppressWarnings("deprecation")
	@Get
	public StringRepresentation fetch() {

		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String instanceId = (String) getRequestAttributes().get("instanceId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());

		if (instanceId == null) {
			LOGGER.info("Getting all instances of token :" + federationAuthToken);

			List<String> filterCategory = HeaderUtils.getValueHeaderPerName(OCCIHeaders.CATEGORY, req.getHeaders());
			List<String> filterAttribute = HeaderUtils.getValueHeaderPerName(OCCIHeaders.X_OCCI_ATTRIBUTE,
					req.getHeaders());

			List<Instance> allInstances = new ArrayList<Instance>();
			if (filterCategory.size() != 0 || filterAttribute.size() != 0) {
				allInstances = filterInstances(getInstancesFiltered(application, federationAuthToken), filterCategory,
						filterAttribute);
			} else {
				allInstances = getInstances(application, federationAuthToken);
			}			
			LOGGER.debug("There are " + allInstances.size() + " related to auth_token " + federationAuthToken);

			String user = application.getUser(normalizeAuthToken(federationAuthToken));
			if (user == null) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			List<FedInstanceState> fedPostInstances = instanceDB.getAllByUser(user);
			LOGGER.debug("There are " + fedPostInstances.size() + " owened by user " + user);
			// replacing real instance id by fed_instance_id
			for (FedInstanceState currentInstance : fedPostInstances) {
				allInstances.add(new Instance(currentInstance.getFedInstanceId()));
				
				String globalId = currentInstance.getGlobalInstanceId();
				if (globalId == null || globalId.isEmpty()) {
					Order relatedOrder = application.getOrder(federationAuthToken, currentInstance.getOrderId());
					if (relatedOrder.getState().in(OrderState.FULFILLED)) {
						globalId = relatedOrder.getGlobalInstanceId();
					}					
				}

				if (globalId != null && allInstances.contains(new Instance(globalId))) {
					allInstances.remove(new Instance(globalId));
				}
			}

			if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(generateResponse(allInstances), MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				return new StringRepresentation(generateURIListResponse(allInstances, req), MediaType.TEXT_URI_LIST);
			}
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);

		}

		Order relatedOrder = null;

		if (instanceId.startsWith(FED_INSTANCE_PREFIX)) {
			String user = application.getUser(normalizeAuthToken(federationAuthToken));
			if (user == null) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			
			FedInstanceState fedInstanceState = instanceDB.getByInstanceId(instanceId, user);

			if (fedInstanceState == null) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			
			relatedOrder = application.getOrder(federationAuthToken, fedInstanceState.getOrderId());
			if (!relatedOrder.getState().in(OrderState.FULFILLED)) {
				// instance is not ready for using yet
				return new StringRepresentation(generateInactiveInstanceResponse(fedInstanceState, relatedOrder),
						MediaType.TEXT_PLAIN);
			}

		}

		LOGGER.info("Getting instance " + instanceId);
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			try {

				Instance instance;
				// if it is instance created by post-compute
				if (relatedOrder != null) {
					String user = application.getUser(normalizeAuthToken(federationAuthToken));
					if (user == null) {
						throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
					}
					
					instance = application.getInstance(federationAuthToken, relatedOrder.getGlobalInstanceId());

					// updating instance DB
					FedInstanceState fedInstanceState = instanceDB.getByInstanceId(instanceId, user);
					fedInstanceState.setGlobalInstanceId(relatedOrder.getGlobalInstanceId());
					instanceDB.update(fedInstanceState);

					return new StringRepresentation(
							generateFedInstanceResponse(fedInstanceState, relatedOrder, instance),
							MediaType.TEXT_PLAIN);
				} else {
					instance = application.getInstance(federationAuthToken, instanceId);
				}

				try {
					LOGGER.info("Instance " + instance);
					LOGGER.debug("Instance id: " + instance.getId());
					LOGGER.debug("Instance attributes: " + instance.getAttributes());
					LOGGER.debug("Instance links: " + instance.getLinks());
					LOGGER.debug("Instance resources: " + instance.getResources());
					LOGGER.debug("Instance OCCI format " + instance.toOCCIMessageFormatDetails());
				} catch (Throwable e) {
					LOGGER.warn("", e);
				}

				return new StringRepresentation(instance.toOCCIMessageFormatDetails(), MediaType.TEXT_PLAIN);
				
			} catch (OCCIException e) {
				Response response = new Response(getRequest());

				normalizeURIForBypass(req);
				OCCIApplication.normalizeHeadersForBypass(req);

				application.bypass(req, response);

				// if it is a local instance created out of fogbow
				if (response.getStatus().getCode() == HttpStatus.SC_OK) {
					try {
						return new StringRepresentation(response.getEntity().getText(), MediaType.TEXT_PLAIN);
					} catch (Exception e1) {
					}
				}
				throw e;
			}

		}
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}

	private String generateFedInstanceResponse(FedInstanceState fedInstanceState, Order relatedOrder,
			Instance instance) {
		
		String sshInformation = instance.getAttributes().get(Instance.SSH_PUBLIC_ADDRESS_ATT);
		List<Link> links = fedInstanceState.getLinks();
		
		if (!containsLink(links, "</network/public>") && sshInformation != null) {
			links.add(generateFakePublicLink(fedInstanceState.getFedInstanceId(), instance));
			fedInstanceState.setLinks(links);			
			instanceDB.update(fedInstanceState);
		}
		
		String privateInformation = instance.getAttributes().get(Instance.LOCAL_IP_ADDRESS_ATT);
		if (!containsLink(links, "</network/private>") && privateInformation != null) {
			links.add(generateFakePrivateLink(fedInstanceState.getFedInstanceId(), instance));
			fedInstanceState.setLinks(links);			
			instanceDB.update(fedInstanceState);
		}	
		
		return new Instance(fedInstanceState.getFedInstanceId(), ResourceRepository.getInstance()
				.get(fedInstanceState.getCategories()), instance.getAttributes(), links,
				instance.getState()).toOCCIMessageFormatDetails();
	}

	private boolean containsLink(List<Link> links, String linkName) {
		for (Link link : links) {
			if (link.getName().equals(linkName)) {
				return true;
			}
		}
		return false;
	}

	private Link generateFakePrivateLink(String fedInstanceId, Instance instance) {
		String privateInformation = instance.getAttributes().get(Instance.LOCAL_IP_ADDRESS_ATT);
		
		Map<String, String> linkAttrs = new HashMap<String, String>();
		linkAttrs.put("rel", "http://schemas.ogf.org/occi/infrastructure#network");
		String fakeLinkId = "/network/interface/" + UUID.randomUUID().toString();
		linkAttrs.put("self", fakeLinkId);
		linkAttrs.put("category", "http://schemas.ogf.org/occi/infrastructure#networkinterface http://schemas.ogf.org/occi/infrastructure/networkinterface#ipnetworkinterface");
		linkAttrs.put("occi.networkinterface.gateway", "Not defined");
		linkAttrs.put("occi.networkinterface.mac", "00:0a:95:9d:68:16");
		linkAttrs.put("occi.networkinterface.interface", "eth0");
		linkAttrs.put("occi.networkinterface.state", "active");
		linkAttrs.put("occi.networkinterface.allocation", "static");
		linkAttrs.put("occi.networkinterface.address", privateInformation);
		linkAttrs.put("occi.core.source", "/compute/" + fedInstanceId);
		linkAttrs.put("occi.core.target", "</network/private>");
		linkAttrs.put("occi.core.id", fakeLinkId);

		return new Link("</network/private>", linkAttrs);
	}
	
	private Link generateFakePublicLink(String fedInstanceId, Instance instance) {
		String sshInformation = instance.getAttributes().get(Instance.SSH_PUBLIC_ADDRESS_ATT);
		
		Map<String, String> linkAttrs = new HashMap<String, String>();
		linkAttrs.put("rel", "http://schemas.ogf.org/occi/infrastructure#network");
		String fakeLinkId = "/network/interface/" + UUID.randomUUID().toString();
		linkAttrs.put("self", fakeLinkId);
		linkAttrs.put("category", "http://schemas.ogf.org/occi/infrastructure#networkinterface http://schemas.ogf.org/occi/infrastructure/networkinterface#ipnetworkinterface");
		linkAttrs.put("occi.networkinterface.gateway", "Not defined");
		linkAttrs.put("occi.networkinterface.mac", "00:0a:95:9d:68:16");
		linkAttrs.put("occi.networkinterface.interface", "eth0");
		linkAttrs.put("occi.networkinterface.state", "active");
		linkAttrs.put("occi.networkinterface.allocation", "static");
		linkAttrs.put("occi.networkinterface.address", sshInformation);
		linkAttrs.put("occi.core.source", "/compute/" + fedInstanceId);
		linkAttrs.put("occi.core.target", "</network/public>");
		linkAttrs.put("occi.core.id", fakeLinkId);

		return new Link("</network/public>", linkAttrs);
	}

	private String generateInactiveInstanceResponse(FedInstanceState fedInstanceState, Order order) {
		Map<String, String> instanceAttrs = new HashMap<String, String>();
		instanceAttrs.put("occi.core.id", fedInstanceState.getFedInstanceId());
		
		String titleAttValue = order.getAttValue("occi.core.title");
		if (titleAttValue != null) {
			instanceAttrs.put("occi.core.title", titleAttValue);
		}
		
		instanceAttrs.put("occi.compute.architecture", "Not defined");
		instanceAttrs.put("occi.compute.state", InstanceState.PENDING.getOcciState());
		instanceAttrs.put("occi.compute.speed", "Not defined");
		instanceAttrs.put("occi.compute.memory", "Not defined");
		instanceAttrs.put("occi.compute.cores", "Not defined");
		instanceAttrs.put("occi.compute.hostname", "Not defined");
		
		return new Instance(fedInstanceState.getFedInstanceId(), ResourceRepository.getInstance()
				.get(fedInstanceState.getCategories()), instanceAttrs, fedInstanceState.getLinks(),
				InstanceState.PENDING).toOCCIMessageFormatDetails();
	}

	@Post
	public StringRepresentation post() {
		LOGGER.info("Posting a new compute...");

		OCCIApplication application = (OCCIApplication) getApplication();
		Properties properties = application.getProperties();

		HttpRequest req = (HttpRequest) getRequest();
		String acceptType = getComputePostAccept(HeaderUtils.getAccept(req.getHeaders()));

		List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
		LOGGER.debug("Categories: " + categories);

		HeaderUtils.checkOCCIContentType(req.getHeaders());

		List<Resource> resources = ResourceRepository.getInstance().get(categories);
		if (resources.size() != categories.size()) {
			LOGGER.debug("Some categories was not found in available resources! Resources " + resources.size()
					+ " and categories " + categories.size());
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		boolean computeWasFound = false;
		for (Category category : categories) {
			if (category.getTerm().equals(OrderConstants.COMPUTE_TERM)) {
				computeWasFound = true;
				break;
			}
		}

		if (!computeWasFound) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		List<Category> orderCategories = new ArrayList<Category>();
		orderCategories
				.add(new Category(OrderConstants.TERM, OrderConstants.SCHEME, OrderConstants.KIND_CLASS));

		Map<String, String> orderXOCCIAtt = new HashMap<String, String>();

		// os tpl
		List<Resource> osTplResources = filterByRelProperty(OrderConstants.OS_TPL_OCCI_SCHEME, resources);
		if (osTplResources.size() != 1) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		String imageName = properties.getProperty(
				ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + osTplResources.get(0).getCategory().getTerm());
		if (imageName == null || imageName.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.PROPERTY_NOT_SPECIFIED_FOR_EXTRA_OCCI_RESOURCE
							+ osTplResources.get(0).getCategory());
		}
		orderCategories
				.add(new Category(imageName, OrderConstants.TEMPLATE_OS_SCHEME, OrderConstants.MIXIN_CLASS));

		// resource tpl
		List<Resource> resourceTplResources = filterByRelProperty(OrderConstants.RESOURCE_TPL_OCCI_SCHEME,
				resources);
		String fogbowRequirements = getRequirements(properties, resourceTplResources);		
		orderXOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), fogbowRequirements);
		orderXOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(), DEFAULT_INSTANCE_COUNT);
		orderXOCCIAtt.put(OrderAttribute.TYPE.getValue(), OrderConstants.DEFAULT_TYPE);
		
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
		if (xOCCIAtt.keySet().contains("occi.core.title")) {
			orderXOCCIAtt.put("occi.core.title", xOCCIAtt.get("occi.core.title"));
		}

		convertPublicKey(properties, resources, orderCategories, orderXOCCIAtt, xOCCIAtt);
		convertUserData(properties, resources, orderCategories, orderXOCCIAtt, xOCCIAtt);

		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		orderXOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		 
		Instance instance = new Instance(FED_INSTANCE_PREFIX + UUID.randomUUID().toString());
		List<Order> newOrder = application.createOrders(federationAuthToken, orderCategories, orderXOCCIAtt);

		if (newOrder != null && !newOrder.isEmpty()) {
			setStatus(Status.SUCCESS_CREATED);
		}

		Order relatedOrder = newOrder.get(0);
		FedInstanceState fedInstanceState = new FedInstanceState(instance.getId(),
				relatedOrder.getId(), categories, new ArrayList<Link>(), "", relatedOrder
						.getFederationToken().getUser());
		instanceDB.insert(fedInstanceState);

		if (acceptType.equals(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			String requestEndpoint = getHostRef(req) + req.getHttpCall().getRequestUri();
			return new StringRepresentation(HeaderUtils.X_OCCI_LOCATION_PREFIX+generateLocationHeader(instance, requestEndpoint)
					, new MediaType(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		}

		setLocationHeader(instance, req);
		return new StringRepresentation(ResponseConstants.OK);
	}

	private String getRequirements(Properties properties, List<Resource> resourceTplResources) {
		StringBuilder requirements = new StringBuilder();
		for (int i = 0; i < resourceTplResources.size(); i++) {
			String currentRequirement = properties.getProperty(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX
					+ resourceTplResources.get(i).getCategory().getTerm());

			if (currentRequirement == null || currentRequirement.isEmpty()) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.PROPERTY_NOT_SPECIFIED_FOR_EXTRA_OCCI_RESOURCE + resourceTplResources.get(i));
			}

			// if it is not the last resource requirement
			if (i < resourceTplResources.size() - 1) {
				requirements.append(currentRequirement + " && ");
			} else {
				requirements.append(currentRequirement);
			}
		}

		return requirements.toString();
	}

	protected void convertUserData(Properties properties,
			List<Resource> resources, List<Category> requestCategories,
			Map<String, String> orderXOCCIAtt, Map<String, String> xOCCIAtt) {
		String userdataTerm = properties
				.getProperty(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + OrderConstants.USER_DATA_TERM);

		if (userdataTerm != null && !userdataTerm.isEmpty()) {
			Resource userDataResource = filterByTerm(userdataTerm, resources);
			if (userDataResource != null) {
				requestCategories.add(new Category(OrderConstants.USER_DATA_TERM, OrderConstants.SCHEME,
						OrderConstants.MIXIN_CLASS));

				String userdataDataAtt = properties.getProperty(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX
						+ OrderAttribute.EXTRA_USER_DATA_ATT.getValue());
				
				String userDataContenEncoded = xOCCIAtt.get(userdataDataAtt);
				if (userDataContenEncoded == null) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							"User Data content mus not be null");					
				}
				
				String userDataContent = new String(Base64.decodeBase64(xOCCIAtt.get(userdataDataAtt)));
				
				String userDataContentType = getTypeFromUserData(userDataContent);

				String userData = userDataContent.replace("\n", UserdataUtils.USER_DATA_LINE_BREAKER);
				userData = new String(Base64.encodeBase64(userData.getBytes()));

				orderXOCCIAtt.put(OrderAttribute.EXTRA_USER_DATA_ATT.getValue(), userData);
				orderXOCCIAtt.put(OrderAttribute.EXTRA_USER_DATA_CONTENT_TYPE_ATT.getValue(), userDataContentType);
			}
		}
	}

	protected void convertPublicKey(Properties properties,
			List<Resource> resources, List<Category> orderCategories,
			Map<String, String> orderXOCCIAtt, Map<String, String> xOCCIAtt) {
		String publicKeyTerm = properties
				.getProperty(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX
						+ OrderConstants.PUBLIC_KEY_TERM);
		if (publicKeyTerm != null && !publicKeyTerm.isEmpty()) {
			Resource publicKeyResource = filterByTerm(publicKeyTerm, resources);

			if (publicKeyResource != null) {
				orderCategories.add(new Category(OrderConstants.PUBLIC_KEY_TERM,
						OrderConstants.CREDENTIALS_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));

				String publicKeyDataAtt = properties.getProperty(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX
						+ OrderAttribute.DATA_PUBLIC_KEY.getValue());
				
				String publicKeyContent = xOCCIAtt.get(publicKeyDataAtt);
				if (publicKeyContent == null) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							"public-key content mus not be null");					
				}
				orderXOCCIAtt.put(OrderAttribute.DATA_PUBLIC_KEY.getValue(), publicKeyContent);
			}
		}
	}

	// http://cloudinit.readthedocs.org/en/latest/topics/format.html
	private String getTypeFromUserData(String userDataContent) {
		return MIMEMultipartArchive.getTypeFromContent(userDataContent);
	}

	private Resource filterByTerm(String term, List<Resource> resources) {
		for (Resource r : resources) {
			if (term.equals(r.getCategory().getTerm())) {
				return r;
			}
		}
		return null;
	}

	private List<Resource> filterByRelProperty(String relScheme, List<Resource> resources) {
		List<Resource> filtered = new ArrayList<Resource>();

		for (Resource resource : resources) {
			if (relScheme.equals(resource.getRel())) {
				filtered.add(resource);
			}
		}
		return filtered;
	}

	private String getComputePostAccept(List<String> listAccept) {
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

	public static void normalizeURIForBypass(HttpRequest req) {
		String path = req.getResourceRef().getPath();
		if (path != null && path.contains(org.fogbowcloud.manager.occi.order.Order.SEPARATOR_GLOBAL_ID)) {
			String[] partOfInstanceId = path.split(org.fogbowcloud.manager.occi.order.Order.SEPARATOR_GLOBAL_ID);
			path = partOfInstanceId[0];
		}
		req.getResourceRef().setPath(path);
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

	private String generateLocationHeader(Instance instance, String requestEndpoint) {
		String prefix = requestEndpoint;
		if (!prefix.endsWith("/")) {
			prefix += "/";
		}
		return prefix + instance.getId();
	}

	public String getHostRef(HttpRequest req) {
		OCCIApplication application = (OCCIApplication) getApplication();
		String myIp = application.getProperties().getProperty("my_ip");
		ServerCall httpCall = req.getHttpCall();
		String hostDomain = myIp == null ? httpCall.getHostDomain() : myIp;
		return req.getProtocol().getSchemeName() + "://" + hostDomain + ":" + httpCall.getHostPort();
	}

	private List<Instance> filterInstances(List<Instance> allInstances, List<String> filterCategory,
			List<String> filterAttribute) {
		List<Instance> instancesFiltrated = new ArrayList<Instance>();
		boolean thereIsntCategory = true;
		for (Instance instance : allInstances) {
			if (filterCategory.size() != 0) {
				for (String valueCategoryFilter : filterCategory) {
					for (Resource resource : instance.getResources()) {
						if (valueCategoryFilter.contains(resource.getCategory().getTerm())
								&& valueCategoryFilter.contains(resource.getCategory().getScheme())) {
							instancesFiltrated.add(instance);
							thereIsntCategory = false;
						}
					}
				}
			}
			if (filterAttribute.size() != 0) {
				for (String valueAttributeFilter : filterAttribute) {
					Map<String, String> mapAttributes = instance.getAttributes();
					for (String keyAttribute : mapAttributes.keySet()) {
						if (valueAttributeFilter.contains(keyAttribute) && valueAttributeFilter.endsWith(
								HeaderUtils.normalizeValueAttributeFilter(mapAttributes.get(keyAttribute).trim()))) {
							instancesFiltrated.add(instance);
						}
					}
				}
			}
		}
		if (filterCategory.size() != 0 && thereIsntCategory) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.CATEGORY_IS_NOT_REGISTERED);
		}
		return instancesFiltrated;
	}

	private List<Instance> getInstancesFiltered(OCCIApplication application, String authToken) {
		List<Instance> allInstances = application.getInstancesFullInfo(authToken);
		return allInstances;
	}

	@SuppressWarnings("deprecation")
	private List<Instance> getInstances(OCCIApplication application, String authToken) {
		authToken = normalizeAuthToken(authToken);
		List<Instance> allInstances = application.getInstances(authToken);

		// Adding local instances created out of fogbow
		HttpRequest req = (HttpRequest) getRequest();
		Response response = new Response(req);
		OCCIApplication.normalizeHeadersForBypass(req);
		application.bypass(req, response);
		for (Instance instance : getInstancesCreatedOutOfFogbow(response, application)) {
			if (!allInstances.contains(instance)) {
				allInstances.add(instance);
			}
		}
		return allInstances;
	}

	public static String normalizeAuthToken(String authToken) {
		if (authToken.contains("Basic ")) {
			authToken = new String(Base64.decodeBase64(authToken.replace("Basic ", "")));
		}
		return authToken;
	}

	private List<Instance> getInstancesCreatedOutOfFogbow(Response response, OCCIApplication application) {
		List<Instance> localInstances = new ArrayList<Instance>();
		if (response.getStatus().getCode() == HttpStatus.SC_OK) {
			try {
				String instanceLocations = response.getEntity().getText();
				LOGGER.debug("Cloud Instances Location: " + instanceLocations);
				if (instanceLocations != null && !"".equals(instanceLocations)) {
					if (instanceLocations.contains(HeaderUtils.X_OCCI_LOCATION_PREFIX)) {
						String[] tokens = instanceLocations.trim().split(HeaderUtils.X_OCCI_LOCATION_PREFIX);
						for (int i = 0; i < tokens.length; i++) {
							if (!tokens[i].equals("")) {
								localInstances.add(new Instance(normalizeInstanceId(tokens[i].trim()
										+ org.fogbowcloud.manager.occi.order.Order.SEPARATOR_GLOBAL_ID + application
												.getProperties().getProperty(ConfigurationConstants.XMPP_JID_KEY))));
							}
						}
					}
				}
			} catch (Exception e) {
				LOGGER.error("Exception while getting instance locations from private cloud ...", e);
			}
		}
		return localInstances;
	}

	private String normalizeInstanceId(String instanceLocation) {
		if (!instanceLocation.contains("/")) {
			return instanceLocation;
		}
		String[] splitInstanceId = instanceLocation.split("/");
		return splitInstanceId[splitInstanceId.length - 1];
	}

	public static String generateURIListResponse(List<Instance> instances, HttpRequest req) {
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

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String instanceId = (String) getRequestAttributes().get("instanceId");

		if (instanceId == null) {
			LOGGER.info("Removing all instances of token :" + federationAuthToken);
			String user = application.getUser(normalizeAuthToken(federationAuthToken));
			if (user == null) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			instanceDB.deleteAllFromUser(user);
			return removeIntances(application, federationAuthToken);
		}

		if (instanceId.startsWith(FED_INSTANCE_PREFIX)) {
			LOGGER.info("Removing federated instance " + instanceId);
			
			String user = application.getUser(normalizeAuthToken(federationAuthToken));
			if (user == null) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			
			FedInstanceState fedInstanceState = instanceDB.getByInstanceId(instanceId, user);
			
			if (fedInstanceState == null) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			instanceDB.deleteByIntanceId(instanceId, user);
			try {
				application.removeOrder(federationAuthToken, fedInstanceState.getOrderId());
				if (fedInstanceState.getGlobalInstanceId() != null) {
					LOGGER.debug("Federated instance " + instanceId + " is related to "
							+ fedInstanceState.getGlobalInstanceId());

					return removeInstance(application, federationAuthToken,
							fedInstanceState.getGlobalInstanceId());
				}
			} catch (Exception e) {
				LOGGER.warn("Error while removing resources related to federated instance " + instanceId + ".", e);
			}
			return ResponseConstants.OK;
		}

		LOGGER.info("Removing instance " + instanceId);
		return removeInstance(application, federationAuthToken, instanceId);
	}

	@SuppressWarnings("deprecation")
	private String removeInstance(OCCIApplication application, String federationAuthToken, String instanceId) {
		try {
			application.removeInstance(federationAuthToken, instanceId);
		} catch (OCCIException e) {
			// The request will be bypassed only if the error was not found
			if (e.getStatus().getCode() == HttpStatus.SC_NOT_FOUND) {
				HttpRequest req = (HttpRequest) getRequest();
				Response response = new Response(req);

				normalizeURIForBypass(req);
				OCCIApplication.normalizeHeadersForBypass(req);

				application.bypass(req, response);
				// if it is a local instance created outside fogbow
				if (response.getStatus().getCode() == HttpStatus.SC_OK) {
					return ResponseConstants.OK;
				}
			}
			throw e;
		}
		return ResponseConstants.OK;
	}

	@SuppressWarnings("deprecation")
	private String removeIntances(OCCIApplication application, String authToken) {
		application.removeInstances(authToken);
		// Removing local cloud instances for the token
		HttpRequest req = (HttpRequest) getRequest();
		Response response = new Response(req);
		OCCIApplication.normalizeHeadersForBypass(req);
		try {
			application.bypass(req, response);
		} catch (OCCIException e) {
		}
		return ResponseConstants.OK;
	}

	public static String generateResponse(List<Instance> instances) {
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
}