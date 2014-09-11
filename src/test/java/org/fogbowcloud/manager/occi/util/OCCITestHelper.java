package org.fogbowcloud.manager.occi.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class OCCITestHelper {

	public static final int ENDPOINT_PORT = PluginHelper.getAvailablePort();
	public static final String ACCESS_TOKEN = "HgjhgYUDFTGBgrbelihBDFGB40uyrb";
	public static final String INVALID_TOKEN = "invalid-token";
	public static final String URI_FOGBOW_REQUEST = "http://localhost:" + ENDPOINT_PORT + "/" + RequestConstants.TERM + "/";
	public static final String URI_FOGBOW_COMPUTE = "http://localhost:" + ENDPOINT_PORT + "/compute/";
	public static final String URI_FOGBOW_MEMBER = "http://localhost:" + ENDPOINT_PORT + "/members";
	public static final String URI_FOGBOW_TOKEN = "http://localhost:" + ENDPOINT_PORT + "/token";
	public static final String URI_FOGBOW_QUERY = "http://localhost:" + ENDPOINT_PORT + "/-/";
	public static final String USER_MOCK = "user_mock";

	private Component component;
	private RequestRepository requests;

	public void initializeComponent(ComputePlugin computePlugin, IdentityPlugin identityPlugin, AuthorizationPlugin authorizationPlugin)
			throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.SSH_PRIVATE_HOST_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.SSH_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();
	}
	
	public void initializeComponentCompute(ComputePlugin computePlugin,
			IdentityPlugin identityPlugin, AuthorizationPlugin authorizationPlugin,
			List<Request> requestsToAdd) throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.SSH_PRIVATE_HOST_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.SSH_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);

		requests = new RequestRepository();
		facade.setRequests(requests);
		for (Request request : requestsToAdd) {
			requests.addRequest(OCCITestHelper.USER_MOCK, request);
		}

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();
	}

	public void initializeComponentMember(ComputePlugin computePlugin,
			IdentityPlugin identityPlugin, List<FederationMember> federationMembers)
			throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.MANAGER_COMPONENT_URL);
		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);
		facade.updateMembers(federationMembers);

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();
	}

	public void stopComponent() throws Exception {
		component.stop();
	}

	public static List<String> getRequestIds(HttpResponse response) throws ParseException,
			IOException {
		String responseStr = "";
		try {
			responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));			
		} catch (Exception e) {
			return new ArrayList<String>();
		}

		List<String> requestIds = new ArrayList<String>();
		if (responseStr.contains(HeaderUtils.X_OCCI_LOCATION_PREFIX)) {
			String[] tokens = responseStr.split(HeaderUtils.X_OCCI_LOCATION_PREFIX);

			for (int i = 0; i < tokens.length; i++) {
				if (!tokens[i].equals("")) {
					requestIds.add(tokens[i].trim());
				}
			}
		}
		return requestIds;
	}

	public String getStateFromRequestDetails(String requestDetails) {
		StringTokenizer st = new StringTokenizer(requestDetails, "\n");
		Map<String, String> occiAttributes = new HashMap<String, String>();
		while (st.hasMoreElements()) {
			String line = st.nextToken().trim();
			if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE)){
				StringTokenizer st2 = new StringTokenizer(line, ":");
				st2.nextToken(); //X-OCCI-Attribute
				String attToValue = st2.nextToken().trim();
				String[] attAndValue = attToValue.split("=");
				if (attAndValue.length == 2) {
					occiAttributes.put(attAndValue[0], attAndValue[1].replaceAll("\"", ""));
				} else {
					occiAttributes.put(attAndValue[0], "");
				}
			}
		}
		return occiAttributes.get(RequestAttribute.STATE.getValue());
	}

	public static List<URI> getURIList(HttpResponse response) throws URISyntaxException {
		String responseStr = "";
		try {
			responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));			
		} catch (Exception e) {
			return new ArrayList<URI>();
		}
		
		List<URI> requestURIs = new ArrayList<URI>();		
		String[] tokens = responseStr.trim().split("\n");

		for (int i = 0; i < tokens.length; i++) {
			if (!tokens[i].equals("")) {
				requestURIs.add(new URI(tokens[i].trim()));
			}
		}
		
		
		return requestURIs;
	}
}
