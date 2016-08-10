package org.fogbowcloud.manager.occi.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.data.Header;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class StorageServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(StorageServerResource.class);
	public static final String FED_STORAGE_PREFIX = "federated_storage_";
	private static final String DEFAULT_INSTANCE_COUNT = "1";
	
	private StorageDataStore storageDB;
	

	protected void doInit() throws ResourceException {
		Properties properties = ((OCCIApplication) getApplication()).getProperties();
		String storageDSUrl = properties.getProperty(ConfigurationConstants.STORAGE_DATA_STORE_URL);
		storageDB = new StorageDataStore(storageDSUrl);
	}

	@Get
	public StringRepresentation fetch() {
		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String storageId = (String) getRequestAttributes().get("storageId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());

		String user = application.getUser(ComputeServerResource.normalizeAuthToken(federationAuthToken));
		
		if (user == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		if (storageId == null) {
			return fetchWithoutStorageId(application, req, federationAuthToken, acceptContent, user);
		}else{
			return fetchWithStorageId(application, federationAuthToken, storageId, acceptContent, user);
		}
		
	}
	
	private StringRepresentation fetchWithStorageId(OCCIApplication application, String federationAuthToken, String storageId,
			List<String> acceptContent, String user) {
		
		LOGGER.info("Getting storage " + storageId);
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			try {
				
				Instance instance = null;
				Order relatedOrder = null;

				if (storageId.startsWith(FED_STORAGE_PREFIX)) {

					FedStorageState fedStorageState = storageDB.getByStorageId(storageId, user);

					if (fedStorageState == null) {
						throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
					}

					relatedOrder = application.getOrder(federationAuthToken, fedStorageState.getOrderId());

					if (relatedOrder != null) {

						instance = application.getInstance(federationAuthToken, relatedOrder.getGlobalInstanceId(),
								OrderConstants.STORAGE_TERM);

						// updating Storage DB
						fedStorageState.setGlobalStorageId(relatedOrder.getGlobalInstanceId());
						storageDB.update(fedStorageState);
						
					} 
					
				}else {
					instance = application.getInstance(federationAuthToken, storageId, OrderConstants.STORAGE_TERM);
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
				throw e;
			}
		}
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}

	private StringRepresentation fetchWithoutStorageId(OCCIApplication application, HttpRequest req,
			String federationAuthToken, List<String> acceptContent, String user) {
		LOGGER.info("Getting all instance(storage) of token :" + federationAuthToken);

		List<Instance> allInstances = getInstances(application, federationAuthToken);
		LOGGER.debug("There are " + allInstances.size() + " related to auth_token " + federationAuthToken);
		
		List<FedStorageState> fedPostStorages = storageDB.getAllByUser(user);
		LOGGER.debug("There are " + fedPostStorages.size() + " owned by user " + user);
		// replacing real instance id by fed_instance_id
		for (FedStorageState currentStorage : fedPostStorages) {
			
			allInstances.add(new Instance(currentStorage.getFedStorageId()));
			
			String globalId = currentStorage.getGlobalStorageId();
			if (globalId == null || globalId.isEmpty()) {
				try {
					Order relatedOrder = application.getOrder(federationAuthToken, currentStorage.getOrderId());
					if (relatedOrder != null && relatedOrder.getState().in(OrderState.FULFILLED)) {
						globalId = relatedOrder.getGlobalInstanceId();
						
						// updating Storage DB
						currentStorage.setGlobalStorageId(relatedOrder.getGlobalInstanceId());
						storageDB.update(currentStorage);
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
			return new StringRepresentation(ComputeServerResource.generateResponse(allInstances),
					MediaType.TEXT_PLAIN);
		} else if (acceptContent.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
			return new StringRepresentation(ComputeServerResource.generateURIListResponse(allInstances, req),
					MediaType.TEXT_URI_LIST);
		}
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}

	@Post
	public StringRepresentation post() {

		LOGGER.info("Posting a new storage...");

		HttpRequest req = (HttpRequest) getRequest();
		
		OCCIApplication application = (OCCIApplication) getApplication();
		
		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		String user = application.getUser(ComputeServerResource.normalizeAuthToken(federationAuthToken));
		
		if (user == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		String acceptType = getStoragePostAcceptType(HeaderUtils.getAccept(req.getHeaders()));

		List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
		HeaderUtils.checkCategories(categories, OrderConstants.STORAGE_TERM);
		
		LOGGER.debug("Categories: " + categories);

		HeaderUtils.checkOCCIContentType(req.getHeaders());

		List<Category> orderCategories = new ArrayList<Category>();

		orderCategories.add(new Category(OrderConstants.TERM, OrderConstants.SCHEME, OrderConstants.KIND_CLASS));
		Map<String, String> orderXOCCIAtt = new HashMap<String, String>();

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
		if (xOCCIAtt.keySet().contains(StorageAttribute.SIZE.getValue())) {
			try{
				Integer size = Integer.parseInt(xOCCIAtt.get(StorageAttribute.SIZE.getValue()));
				if(new Integer(0).compareTo(size) >= 0){
					throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
				}
			}catch(NumberFormatException e){
				throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
			}
			
			orderXOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), xOCCIAtt.get(StorageAttribute.SIZE.getValue()));
		}else{
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		orderXOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.STORAGE_TERM);
		orderXOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(), DEFAULT_INSTANCE_COUNT);

		List<Order> newOrder = application.createOrders(federationAuthToken, orderCategories, orderXOCCIAtt);

		if (newOrder != null && !newOrder.isEmpty()) {
			setStatus(Status.SUCCESS_CREATED);

			Order relatedOrder = newOrder.get(0);
			FedStorageState fedStorageState = new FedStorageState(FED_STORAGE_PREFIX + UUID.randomUUID().toString(),
					relatedOrder.getId(), "", relatedOrder.getFederationToken().getUser());
			storageDB.insert(fedStorageState);

			if (acceptType.equals(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				String requestEndpoint = HeaderUtils.getHostRef((OCCIApplication) getApplication(), req) + req.getHttpCall().getRequestUri();
				return new StringRepresentation(
						HeaderUtils.X_OCCI_LOCATION_PREFIX
						+ generateLocationHeader(fedStorageState.getFedStorageId(), requestEndpoint),
						new MediaType(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
			}

			setLocationHeader(fedStorageState.getFedStorageId(), req);
			return new StringRepresentation(ResponseConstants.OK);

		}else{
			setStatus(Status.SERVER_ERROR_INTERNAL);
			return new StringRepresentation(ResponseConstants.INTERNAL_ERROR);
		}
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String storageId = (String) getRequestAttributes().get("storageId");
		
		String user = application.getUser(ComputeServerResource.normalizeAuthToken(federationAuthToken));
		if (user == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		if (storageId == null) {
			LOGGER.info("Removing all instances(storage) of token :" + federationAuthToken);
			storageDB.deleteAllFromUser(user);
			return removeIntances(application, federationAuthToken);
		}
		
		if (storageId.startsWith(FED_STORAGE_PREFIX)) {
			LOGGER.info("Removing federated storage " + storageId);
			
			FedStorageState fedStorageState = storageDB.getByStorageId(storageId, user);
			
			if (fedStorageState == null) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			try {

				storageDB.deleteByStorageId(storageId, user);
				
				Order relatedOrder = application.getOrder(federationAuthToken, fedStorageState.getOrderId());

				if (relatedOrder != null && relatedOrder.getGlobalInstanceId() != null) {
					application.removeOrder(federationAuthToken, fedStorageState.getOrderId());
					application.removeInstance(federationAuthToken, relatedOrder.getGlobalInstanceId(), OrderConstants.STORAGE_TERM);
				}
				
				
			} catch (Exception e) {
				String erro = "Error while removing resources related to federated instance " + storageId + ".";
				LOGGER.warn(erro, e);
				throw new OCCIException(ErrorType.INTERNAL_SERVER_ERROR, erro);
			}
			return ResponseConstants.OK;
		}

		LOGGER.info("Removing instance(storage) " + storageId);
		application.removeInstance(federationAuthToken, storageId, OrderConstants.STORAGE_TERM);
		return ResponseConstants.OK;
	}	
	
	private String removeIntances(OCCIApplication application, String authToken) {
		application.removeInstances(authToken, OrderConstants.STORAGE_TERM);		
		return ResponseConstants.OK;
	}

	private List<Instance> getInstances(OCCIApplication application, String authToken) {
		authToken = ComputeServerResource.normalizeAuthToken(authToken);
		List<Instance> allInstances = application.getInstances(authToken, OrderConstants.STORAGE_TERM);	
		return allInstances;
	}	
	
	private String getStoragePostAcceptType(List<String> listAccept) {
		if (!listAccept.isEmpty()) {
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

	private String generateLocationHeader(String storageId, String requestEndpoint) {
		String prefix = requestEndpoint;
		if (!prefix.endsWith("/")) {
			prefix += "/";
		}
		return prefix + storageId;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setLocationHeader(String storageId, HttpRequest req) {
		String requestEndpoint = HeaderUtils.getHostRef((OCCIApplication) getApplication(), req) + req.getHttpCall().getRequestUri();
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Series(Header.class);
			getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);
		}

		responseHeaders.add(new Header("Location", generateLocationHeader(storageId, requestEndpoint)));
	}

	protected void setStorageDataStore(StorageDataStore storageDB){
		this.storageDB = storageDB;
	}
}
