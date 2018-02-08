package org.fogbowcloud.manager.occi.federatednetwork;

import java.util.*;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.federatednetwork.FederatedNetwork;
import org.fogbowcloud.manager.core.model.FederationMember;
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
	private static final String FILE_SEPARATOR = "/";

	public static final String NO_NETWORKS_MESSAGE = "No federated networks.";

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

		Collection<FederatedNetwork> federatedNetworks = null;

		if (federatedNetworkId == null || federatedNetworkId.trim().isEmpty()) {
			LOGGER.info("Getting all Federated Network IDs");

			federatedNetworks = application.getAllFederatedNetworks();
		} else {
			LOGGER.info("Getting details about Federated Network with ID: " + federatedNetworkId);

			FederatedNetwork federatedNetwork = application.getFederatedNetwork(federatedNetworkId);

			if (federatedNetwork != null) {
				federatedNetworks = Arrays.asList(federatedNetwork);
			}
		}
		return new StringRepresentation(
				generateTextPlainResponse(federatedNetworks, request, true, application),
				MediaType.TEXT_PLAIN);
	}

	protected String generateTextPlainResponse(Collection<FederatedNetwork> networks,
			HttpRequest req, boolean verbose, OCCIApplication application) {
		if (networks == null || networks.isEmpty()) {
			return NO_NETWORKS_MESSAGE;
		}

		String response = new String();
		for (FederatedNetwork federatedNetwork : networks) {
			String locationEndpoint = HeaderUtils.getHostRef(application, req)
					+ FederatedNetworkResource.FILE_SEPARATOR
					+ FederatedNetworkConstants.FEDERATED_NETWORK_TERM
					+ FederatedNetworkResource.FILE_SEPARATOR + federatedNetwork.getId();

			String prefixOCCILocation;
			if (locationEndpoint.endsWith(FederatedNetworkResource.FILE_SEPARATOR)) {
				prefixOCCILocation = HeaderUtils.X_OCCI_LOCATION_PREFIX + locationEndpoint;
			} else {
				prefixOCCILocation = HeaderUtils.X_OCCI_LOCATION_PREFIX + locationEndpoint
						+ FederatedNetworkResource.FILE_SEPARATOR;
			}

			if (verbose) {
				String[] keys = new String[] { OCCIConstants.FEDERATED_NETWORK_CIDR,
						OCCIConstants.FEDERATED_NETWORK_LABEL,
						OCCIConstants.FEDERATED_NETWORK_MEMBERS };
				String[] values = new String[] { federatedNetwork.getCidr(),
						federatedNetwork.getLabel(),
						formatMembers(federatedNetwork.getAllowedMembers()) };

				response += prefixOCCILocation + System.lineSeparator();

				String attributeFormat = "X-OCCI-Attribute: %s=%s; ";
				for (int i = 0; i < keys.length; i++) {
					response += String.format(attributeFormat, keys[i], values[i])
							+ System.lineSeparator();
				}

			} else {
				response += prefixOCCILocation + System.lineSeparator();
			}
		}
		return response.length() > 0 ? response.trim() : System.lineSeparator();
	}

	private String formatMembers(Collection<FederationMember> members) {
		String str = members.toString();
		return str.substring(1, str.length() - 1);
	}

	// TODO: remove this, HTTP Post to Federated Network will be implemented as a
	// Order
	// in OrderServerResource
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

		String label = getLabel(request);
		LOGGER.info("Federated Network Request Label: " + label);

		String cidr = getCIDR(request);
		LOGGER.info("Federated Network Request CIDR: " + cidr);

		Set<String> membersSet = getMembersSet(request);
		LOGGER.info("Federated Network Request Members: " + membersSet.toString());

		String response = "Posted a new Federated Network" + System.lineSeparator() + "Label: "
				+ label + System.lineSeparator() + "CIDR: " + cidr + System.lineSeparator()
				+ "Members: " + membersSet.toString();
		return new StringRepresentation(response, MediaType.TEXT_PLAIN);
	}

	// TODO: review how it will be implemented (FederatedNetworksController)
	@Put
	public StringRepresentation putFederatedNetwork() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest request = (HttpRequest) getRequest();
		String acceptType = getFederatedNetworkPostAccept(
				HeaderUtils.getAccept(request.getHeaders()));

		LOGGER.debug("Accept Contents: " + acceptType);

		HeaderUtils.checkOCCIContentType(request.getHeaders());

		String federationAuthToken = HeaderUtils.getAuthToken(request.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		LOGGER.debug("Federation Authentication Token: " + federationAuthToken);

		String federatedNetworkId = getFederatedNetworkId();
		LOGGER.info("Federated Network with ID: " + federatedNetworkId);

		Set<String> membersSet = getMembersSet(request);
		LOGGER.info("Federated Network Request Members: " + membersSet.toString());

		String response = "Puted into Federated Network ID: " + federatedNetworkId
				+ System.lineSeparator() + "Members: " + membersSet.toString();
		return new StringRepresentation(response, MediaType.TEXT_PLAIN);
	}

	private String getFederatedNetworkPostAccept(List<String> listAccept) {
		String result = "";
		if (listAccept.size() > 0) {
			if (listAccept.get(0).contains(MediaType.TEXT_PLAIN.toString())) {
				result = MediaType.TEXT_PLAIN.toString();
			} else if (listAccept.get(0).contains(OCCIHeaders.OCCI_CONTENT_TYPE)) {
				result = OCCIHeaders.OCCI_CONTENT_TYPE;
			} else {
				throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
						ResponseConstants.ACCEPT_NOT_ACCEPTABLE);
			}
		}
		return result;
	}

	private String getFederatedNetworkId() {
		String federatedNetworkId = (String) getRequestAttributes()
				.get(FederatedNetworkConstants.FEDERATED_NETWORK_ID_TERM);

		if (federatedNetworkId == null || federatedNetworkId.trim().isEmpty()) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE,
					ResponseConstants.INVALID_FEDERATED_NETWORK_ID);
		}
		return federatedNetworkId;
	}

	private Set<String> getMembersSet(HttpRequest request) {
		List<String> membersList = HeaderUtils.getValueHeaderPerName(
				OCCIConstants.FEDERATED_NETWORK_MEMBERS, request.getHeaders());

		if (membersList == null || membersList.isEmpty()) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.INVALID_MEMBER);
		}

		Set<String> membersSet = new HashSet<String>(membersList);
		return membersSet;
	}

	private String getCIDR(HttpRequest request) {
		List<String> cidrList = HeaderUtils
				.getValueHeaderPerName(OCCIConstants.FEDERATED_NETWORK_CIDR, request.getHeaders());

		if (cidrList.size() != 1) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.INVALID_CIDR);
		}

		String cidr = cidrList.get(0);
		return cidr;
	}

	private String getLabel(HttpRequest request) {
		List<String> labelList = HeaderUtils
				.getValueHeaderPerName(OCCIConstants.FEDERATED_NETWORK_LABEL, request.getHeaders());

		if (labelList.size() != 1) {
			throw new OCCIException(ErrorType.NOT_ACCEPTABLE, ResponseConstants.INVALID_LABEL);
		}

		String label = labelList.get(0);
		return label;
	}

}
