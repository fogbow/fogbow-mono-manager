package org.fogbowcloud.manager.occi.storage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.instance.FedInstanceState;
import org.fogbowcloud.manager.occi.instance.InstanceDataStore;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderConstants;
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

public class StorageLinkServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(StorageLinkServerResource.class);
	private static final CharSequence NO_STORAGE_MESSAGE = "There are not storage links.";
	
	private StorageDataStore storageDB;
	private InstanceDataStore instanceDB;
	
	protected void doInit() throws ResourceException {
		Properties properties = ((OCCIApplication) getApplication()).getProperties();
		String storageDSUrl = properties.getProperty(ConfigurationConstants.STORAGE_DATA_STORE_URL);
		String instanceDSUrl = properties.getProperty(ConfigurationConstants.INSTANCE_DATA_STORE_URL);
		storageDB = new StorageDataStore(storageDSUrl);
		instanceDB = new InstanceDataStore(instanceDSUrl);
	}
	
	@Post
	public StringRepresentation post() {
		LOGGER.info("Posting a new storage link...");
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		getAccept(HeaderUtils.getAccept(req.getHeaders()));
		
		List<Category> categories = HeaderUtils.getCategories(req.getHeaders());
		LOGGER.debug("Categories: " + categories);
		HeaderUtils.checkCategories(categories, OrderConstants.STORAGELINK_TERM);
		HeaderUtils.checkOCCIContentType(req.getHeaders());		
		
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(req.getHeaders());
		xOCCIAtt = checkXOCCIAtt(xOCCIAtt);	
		
		String federationAuthToken = HeaderUtils.getAuthToken(
				req.getHeaders(), getResponse(), application.getAuthenticationURI());
				
		xOCCIAtt = resolveFederatedInstances(xOCCIAtt, federationAuthToken);
		
		StorageLink storageLinkCreated = application.createStorageLink(federationAuthToken, categories, xOCCIAtt);
		setStatus(Status.SUCCESS_CREATED);
		
		setLocationHeader(storageLinkCreated, req);
		
		return new StringRepresentation(ResponseConstants.OK);
	}

	private String getAccept(List<String> accept) {
		if (!accept.isEmpty() && !accept.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
		}
		return OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE;
	}

	@Delete
	public String remove() {		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAccessToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String storageLinkId = (String) getRequestAttributes().get("storageLinkId");

		if (storageLinkId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "");
		}

		LOGGER.info("Removing storage link  of token :" + federationAccessToken);
		application.removeStorageLink(federationAccessToken, storageLinkId);
		return ResponseConstants.OK;
	}	
	
	@Get
	public StringRepresentation fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAccessToken = HeaderUtils.getAuthToken(
				req.getHeaders(), getResponse(), application.getAuthenticationURI());
		String storageLinkId = (String) getRequestAttributes().get("storageLinkId");
		List<String> acceptContent = HeaderUtils.getAccept(req.getHeaders());
		LOGGER.debug("accept contents:" + acceptContent);
		
		boolean verbose = false;
		try {
			verbose = Boolean.parseBoolean(getQuery().getValues("verbose"));
		} catch (Exception e) {}
		
		if (storageLinkId == null) {
			LOGGER.info("Getting all storage link of token :" + federationAccessToken);
			List<StorageLink> storageLinkFromUser = application.getStorageLinksFromUser(federationAccessToken);
			
			if (acceptContent.size() == 0
					|| acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(generateTextPlainResponse(
						storageLinkFromUser, req, verbose),
						MediaType.TEXT_PLAIN);
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);				
			}
		}

		LOGGER.info("Getting order(" + storageLinkId + ") of token :" + federationAccessToken);
		StorageLink storageLink = application.getStorageLink(federationAccessToken, storageLinkId);
		if (storageLink == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			return new StringRepresentation(
					generateTextPlainResponseStorageLink(storageLink, federationAccessToken), MediaType.TEXT_PLAIN);				
		}
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}	
	
	private CharSequence generateTextPlainResponseStorageLink(
			StorageLink storageLink, String federationAuthToken) {
		StringBuffer responseOCCIFormat = new StringBuffer();
		if (storageLink == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}else{
			Resource resource = ResourceRepository.getInstance().get(OrderConstants.STORAGELINK_TERM);
			responseOCCIFormat.append(resource.toHeader());
			responseOCCIFormat.append("\n");

			Map<String, String> attToOutput = new HashMap<String, String>();
			attToOutput.put(StorageAttribute.DEVICE_ID.getValue(), storageLink.getDeviceId());		
			attToOutput.put(StorageAttribute.SOURCE.getValue(), storageLink.getSource());
			attToOutput.put(StorageAttribute.TARGET.getValue(), storageLink.getTarget());
			attToOutput.put(StorageAttribute.PROVIDING_MEMBER_ID.getValue(), storageLink.getProvidingMemberId());

			//Checking if compute or storage are federated.
			attToOutput = replaceGlobalIdByFederatedInstanceId(attToOutput, federationAuthToken);

			for (String attName : attToOutput.keySet()) {
				String attrValue = attToOutput.get(attName) != null ? attToOutput.get(attName) : null;
				responseOCCIFormat.append(OCCIHeaders.X_OCCI_ATTRIBUTE);
				responseOCCIFormat.append(": ");
				responseOCCIFormat.append(attName);
				responseOCCIFormat.append("=\"");
				responseOCCIFormat.append(attrValue);
				responseOCCIFormat.append("\"");
				responseOCCIFormat.append("\n");
			}
		}
		return responseOCCIFormat;
	}

	public static CharSequence generateResponse(List<StorageLink> storageLinks) {
		if (storageLinks == null || storageLinks.isEmpty()) { 
			return NO_STORAGE_MESSAGE;
		}
		String response = "";
		Iterator<StorageLink> storageLinkIt = storageLinks.iterator();
		while(storageLinkIt.hasNext()){			
			StorageLink storageLink = storageLinkIt.next();
			response += HeaderUtils.X_OCCI_LOCATION_PREFIX + storageLink.getId() 
					 + Order.SEPARATOR_GLOBAL_ID + storageLink.getProvidingMemberId();
			if (storageLinkIt.hasNext()) {
				response += "\n";
			}
		}
		return response;
	}
	
	private CharSequence generateTextPlainResponse(
			List<StorageLink> storageLinks, HttpRequest req, boolean verbose) {
		if (storageLinks == null || storageLinks.isEmpty()) { 
			return NO_STORAGE_MESSAGE;
		}
		String requestEndpoint = HeaderUtils.getHostRef((OCCIApplication) getApplication()
				, req) + req.getHttpCall().getRequestUri();
		String response = "";
		Iterator<StorageLink> storageLinkIt = storageLinks.iterator();
		while(storageLinkIt.hasNext()){			
			StorageLink storageLInk = storageLinkIt.next();
			String prefixOCCILocation = "";
			if (requestEndpoint.endsWith("/")){
				prefixOCCILocation += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint;
			}else {
				prefixOCCILocation += HeaderUtils.X_OCCI_LOCATION_PREFIX + requestEndpoint + "/";
			}
			
			response += prefixOCCILocation + storageLInk.getId();
			if (verbose) {
				String deviceId = storageLInk.getDeviceId();
				deviceId = deviceId != null && deviceId != "null" ? deviceId : OrderConstants.DEVICE_ID_DEFAULT;
				response += ";" 
						+ StorageAttribute.TARGET.getValue() + "=" + storageLInk.getTarget() + "; "
						+ StorageAttribute.SOURCE.getValue() + "=" + storageLInk.getSource() + "; "
						+ StorageAttribute.DEVICE_ID.getValue() + "=" + deviceId + "; "
						+ StorageAttribute.PROVIDING_MEMBER_ID.getValue() + "=" + storageLInk.getProvidingMemberId() + "; "
						+ "\n";
			} else {
				response += "\n";
			}
		}
		return response.length() > 0 ? response.trim() : "\n";
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void setLocationHeader(StorageLink storageLink, HttpRequest req) {
		String requestEndpoint = getHostRef(req) + req.getHttpCall().getRequestUri();
		Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get(
				"org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Series(Header.class);
			getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);
		}

		responseHeaders.add(new Header("Location", requestEndpoint + "/"
				+ storageLink.getId() + Order.SEPARATOR_GLOBAL_ID + storageLink.getProvidingMemberId()));
	}
	
	public String getHostRef(HttpRequest req) {
		OCCIApplication application = (OCCIApplication) getApplication();
		String myIp = application.getProperties().getProperty("my_ip");
		ServerCall httpCall = req.getHttpCall();
		String hostDomain = myIp == null ? httpCall.getHostDomain() : myIp;
		return req.getProtocol().getSchemeName() + "://" + hostDomain + ":" + httpCall.getHostPort();
	}
	
	private Map<String, String> checkXOCCIAtt(Map<String, String> xOCCIAtt) {
		String target = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		String source = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		if (target == null || source ==null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.NOT_FOUND_STORAGE_SIZE_ATTRIBUTE);
		}
		return xOCCIAtt;
	}
	
	private Map<String, String> resolveFederatedInstances(Map<String, String> xOCCIAtt, String federationAuthToken){
		
		String target = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		String source = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		
		OCCIApplication application = (OCCIApplication) getApplication();
		
		String user = application.getUserId(normalizeAuthToken(federationAuthToken));
		
		if(target != null && target.startsWith(StorageServerResource.FED_STORAGE_PREFIX)){
			FedStorageState fedStorageState = storageDB.getByStorageId(target, user);
			Order relatedOrder = application.getOrder(federationAuthToken, fedStorageState.getOrderId());
			xOCCIAtt.put(StorageAttribute.TARGET.getValue(), relatedOrder.getGlobalInstanceId());
			fedStorageState.setGlobalStorageId(relatedOrder.getGlobalInstanceId());
			storageDB.update(fedStorageState);
		}
		
		if(source != null && source.startsWith(ComputeServerResource.FED_INSTANCE_PREFIX)){
			FedInstanceState fedInstanceState = instanceDB.getByInstanceId(source, user);
			Order relatedOrder = application.getOrder(federationAuthToken, fedInstanceState.getOrderId());
			xOCCIAtt.put(StorageAttribute.SOURCE.getValue(), relatedOrder.getGlobalInstanceId());
			fedInstanceState.setGlobalInstanceId(relatedOrder.getGlobalInstanceId());
			instanceDB.update(fedInstanceState);
		}
		
		return xOCCIAtt;
		
	}
	
	private Map<String, String> replaceGlobalIdByFederatedInstanceId(Map<String, String> xOCCIAtt, String federationAuthToken){
		
		String target = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		String source = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		
		OCCIApplication application = (OCCIApplication) getApplication();
		
		String user = application.getUserId(normalizeAuthToken(federationAuthToken));
		
		if(target != null){
			FedStorageState fedStorageState = storageDB.getByGlobalId(target, user);
			if(fedStorageState != null){
				xOCCIAtt.put(StorageAttribute.TARGET.getValue(), fedStorageState.getFedStorageId());
			}
		}
		
		if(source != null){
			FedInstanceState fedInstanceState = instanceDB.getByGlobalId(source, user);
			if(fedInstanceState != null){
				xOCCIAtt.put(StorageAttribute.SOURCE.getValue(), fedInstanceState.getFedInstanceId());
			}
		}
		
		return xOCCIAtt;
		
	}
	
	public static String normalizeAuthToken(String authToken) {
		if (authToken.contains("Basic ")) {
			authToken = new String(Base64.decodeBase64(authToken.replace("Basic ", "")));
		}
		return authToken;
	}
	
}
