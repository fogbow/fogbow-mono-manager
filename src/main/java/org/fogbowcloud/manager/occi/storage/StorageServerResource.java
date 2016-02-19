package org.fogbowcloud.manager.occi.storage;

import java.util.List;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.restlet.data.MediaType;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class StorageServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(StorageServerResource.class);
	
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
			LOGGER.info("Getting all instance(storage) of token :" + federationAuthToken);

			List<Instance> allInstances = getInstances(application, federationAuthToken);
			LOGGER.debug("There are " + allInstances.size() + " related to auth_token " + federationAuthToken);			

			if (acceptContent.size() == 0 || acceptContent.contains(
					OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
				return new StringRepresentation(ComputeServerResource
						.generateResponse(allInstances), MediaType.TEXT_PLAIN);
			} else if (acceptContent.contains(
					OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
				return new StringRepresentation(ComputeServerResource.generateURIListResponse(
						allInstances, req), MediaType.TEXT_URI_LIST);
			}
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
		}

		LOGGER.info("Getting storage " + storageId);
		if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
			try {
				 Instance instance = application.getInstance(federationAuthToken, storageId
						 , OrderConstants.STORAGE_TERM);

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
			return removeIntances(application, federationAuthToken);
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
	
}
