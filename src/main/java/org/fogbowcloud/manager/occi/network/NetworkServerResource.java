package org.fogbowcloud.manager.occi.network;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
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

public class NetworkServerResource extends ServerResource {

	protected static final String NO_INSTANCES_MESSAGE = "There are not instances.";
	private static final Logger LOGGER = Logger.getLogger(NetworkServerResource.class);

	@Get
	public StringRepresentation fetch() {

		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest request = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(request.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		List<String> acceptContent = HeaderUtils.getAccept(request.getHeaders());

		String user = application.getUser(normalizeAuthToken(federationAuthToken));
		if (user == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		String networkId = (String) getRequestAttributes().get("networkId");

		if (networkId == null) {

			LOGGER.info("Getting all instance(network) of token : [" + federationAuthToken + "]");

			List<Instance> allInstances = getInstances(application, federationAuthToken);
			LOGGER.debug("There are " + allInstances.size() + " networks related to auth_token " + federationAuthToken);

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

			if (acceptContent.size() == 0 || acceptContent.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {

				try {

					Instance instance = application.getInstance(federationAuthToken, networkId,
							OrderConstants.NETWORK_TERM);

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
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.ACCEPT_NOT_ACCEPTABLE);

		}
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		String networkId = (String) getRequestAttributes().get("networkId");

		String user = application.getUser(normalizeAuthToken(federationAuthToken));
		if (user == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		if (networkId == null) {
			LOGGER.info("Removing all instances(network) of token :" + federationAuthToken);
			return removeIntances(application, federationAuthToken);
		}

		LOGGER.info("Removing instance(network) " + networkId);
		application.removeInstance(federationAuthToken, networkId, OrderConstants.STORAGE_TERM);
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
}
