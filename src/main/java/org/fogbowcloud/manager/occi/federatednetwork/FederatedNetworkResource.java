package org.fogbowcloud.manager.occi.federatednetwork;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.restlet.data.MediaType;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

public class FederatedNetworkResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(FederatedNetworkResource.class);

	@Get
	public StringRepresentation getFederatedNetworkDetails() {
		LOGGER.info("Getting info about Federated Networks");
		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest request = (HttpRequest) getRequest();
		List<String> acceptContent = HeaderUtils.getAccept(request.getHeaders());

		LOGGER.debug("Accept Contents: " + acceptContent);
		
		String federationAuthToken = HeaderUtils.getAuthToken(request.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		LOGGER.debug("Federation Authentication Token: " + federationAuthToken);

		String federatedNetworkId = (String) getRequestAttributes()
				.get(FederatedNetworkConstants.FEDERATED_NETWORK_ID_TERM);

		if (federatedNetworkId == null || federatedNetworkId.trim().isEmpty()) {
			LOGGER.info("Getting all Federated Network IDs");
		} else {
			LOGGER.info("Getting details about Federated Network with ID: " + federatedNetworkId);
		}

		return new StringRepresentation("This is a return ok test", MediaType.TEXT_PLAIN);
	}

	@Post
	public StringRepresentation postFederatedNetwork() {
		LOGGER.info("Posting a new Federated Network");
		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest request = (HttpRequest) getRequest();
		String acceptType = getFederatedNetworkPostAccept(
				HeaderUtils.getAccept(request.getHeaders()));

		LOGGER.debug("Accept Contents: " + acceptType);

		HeaderUtils.checkOCCIContentType(request.getHeaders());

		String federationAuthToken = HeaderUtils.getAuthToken(request.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		LOGGER.debug("Federation Authentication Token: " + federationAuthToken);
		
		List<String> labelList = HeaderUtils.getValueHeaderPerName(OCCIConstants.FEDERATED_NETWORK_LABEL,
				request.getHeaders());

		if (labelList.size() != 1) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.INVALID_LABEL);
		}
		
		String label = labelList.get(0);
		
		LOGGER.info("Federated Network Request Label: " + label);

		List<String> cidrList = HeaderUtils.getValueHeaderPerName(OCCIConstants.FEDERATED_NETWORK_CIDR,
				request.getHeaders());

		if (cidrList.size() != 1) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.INVALID_CIDR);
		}
		
		String cidr = cidrList.get(0);
		
		LOGGER.info("Federated Network Request CIDR: " + cidr);
		
		List<String> membersList = HeaderUtils.getValueHeaderPerName(OCCIConstants.FEDERATED_NETWORK_MEMBER,
				request.getHeaders());
		
		if(membersList == null) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.INVALID_MEMBER);
		}
		
		Set<String> membersSet = new HashSet<String>(membersList);

		LOGGER.info("Federated Network Request Members: " + membersSet.toString());

		return new StringRepresentation("This is a return ok test", MediaType.TEXT_PLAIN);
	}

	@Put
	public StringRepresentation putFederatedNetwork() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest request = (HttpRequest) getRequest();
		String acceptType = getFederatedNetworkPostAccept(
				HeaderUtils.getAccept(request.getHeaders()));

		HeaderUtils.checkOCCIContentType(request.getHeaders());

		String federationAuthToken = HeaderUtils.getAuthToken(request.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		return new StringRepresentation("This is a return ok test", MediaType.TEXT_PLAIN);
	}

	private String getFederatedNetworkPostAccept(List<String> listAccept) {
		if (listAccept.size() > 0) {
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
}
