package org.fogbowcloud.manager.occi.storage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class StorageLinkServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(StorageLinkServerResource.class);
	private static final CharSequence NO_STORAGE_MESSAGE = "There are not storage links.";
	
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
		
		application.createStorageLink(federationAuthToken, categories, xOCCIAtt);
		setStatus(Status.SUCCESS_CREATED);
		
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
		
		if (storageLinkId == null) {
			LOGGER.info("Getting all storage link of token :" + federationAccessToken);
			List<StorageLink> storageLinkFromUser = application.getStorageLinksFromUser(federationAccessToken);
			
			if (acceptContent.size() == 0
					|| acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(generateTextPlainResponse(storageLinkFromUser
						, req), MediaType.TEXT_PLAIN);
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
						ResponseConstants.ACCEPT_NOT_ACCEPTABLE);				
			}
		}

		LOGGER.info("Getting order(" + storageLinkId + ") of token :" + federationAccessToken);
		StorageLink storageLink = application.getStorageLink(federationAccessToken, storageLinkId);
		if (storageLink == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			return new StringRepresentation(
					generateTextPlainResponseOneStorageLink(storageLink), MediaType.TEXT_PLAIN);				
		}
		throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
	}	
	
	private CharSequence generateTextPlainResponseOneStorageLink(
			StorageLink storageLink) {
		StringBuffer responseOCCIFormat = new StringBuffer();
		if (storageLink == null) {
			
		}
		Resource resource = ResourceRepository.getInstance().get(OrderConstants.STORAGELINK_TERM);
		responseOCCIFormat.append(resource.toHeader());
				
		Map<String, String> attToOutput = new HashMap<String, String>();
		attToOutput.put(StorageAttribute.SOURCE.getValue(), storageLink.getSource());
		attToOutput.put(StorageAttribute.TARGET.getValue(), storageLink.getTarget());
		attToOutput.put(StorageAttribute.DEVICE_ID.getValue(), storageLink.getDeviceId());
		for (String attName : attToOutput.keySet()) {
			String attrValue = attToOutput.get(attName);
			responseOCCIFormat.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": " + attName + "=\""
					+ attrValue != null ? attrValue : "null" + "\"");	
			responseOCCIFormat.append("\n");
		}
		
		return responseOCCIFormat;
	}

	private CharSequence generateTextPlainResponse(
			List<StorageLink> storageLinks, HttpRequest req) {
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
			
			response += prefixOCCILocation + storageLInk.getId() + "\n";			
		}
		return response.length() > 0 ? response.trim() : "\n";
	}

	private Map<String, String> checkXOCCIAtt(Map<String, String> xOCCIAtt) {
		String target = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		String source = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		if (target == null || source ==null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.NOT_FOUND_STORAGE_SIZE_ATTRIBUTE);
		}
		return xOCCIAtt;
	}
	
}
