package org.fogbowcloud.manager.occi.util;

import java.io.IOException;
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
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.ssh.SSHTunnel;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.mockito.Mockito;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class OCCITestHelper {

	public static final int ENDPOINT_PORT = PluginHelper.getAvailablePort();
	public static final String ACCESS_TOKEN = "HgjhgYUDFTGBgrbelihBDFGB40uyrb";
	public static final String INVALID_TOKEN = "invalid-token";
	public static final String CONTENT_TYPE_OCCI = "text/occi";
	public static final String URI_FOGBOW_REQUEST = "http://localhost:" + ENDPOINT_PORT + "/" + RequestConstants.TERM + "/";
	public static final String URI_FOGBOW_COMPUTE = "http://localhost:" + ENDPOINT_PORT + "/compute/";
	public static final String URI_FOGBOW_MEMBER = "http://localhost:" + ENDPOINT_PORT + "/members";
	public static final String URI_FOGBOW_TOKEN = "http://localhost:" + ENDPOINT_PORT + "/token";
	public static final String URI_FOGBOW_QUERY = "http://localhost:" + ENDPOINT_PORT + "/-/";
	public static final String USER_MOCK = "user_mock";

	private Component component;
	private RequestRepository requests;

	public void initializeComponent(ComputePlugin computePlugin, IdentityPlugin identityPlugin)
			throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		ManagerController facade = new ManagerController(new Properties());
		facade.setComputePlugin(computePlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);
		facade.setSSHTunnel(Mockito.mock(SSHTunnel.class));

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();
	}

	public void initializeComponentCompute(ComputePlugin computePlugin,
			IdentityPlugin identityPlugin, List<Request> requestsToAdd) throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		ManagerController facade = new ManagerController(new Properties());
		facade.setComputePlugin(computePlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);
		facade.setSSHTunnel(Mockito.mock(SSHTunnel.class));

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

		ManagerController facade = new ManagerController(new Properties());
		facade.setComputePlugin(computePlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);
		facade.setMembers(federationMembers);

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();
	}

	public void stopComponent() throws Exception {
		component.stop();
	}

	public static List<String> getRequestLocations(HttpResponse response) throws ParseException,
			IOException {
		String responseStr = "";
		try {
			responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));			
		} catch (Exception e) {
			return new ArrayList<String>();
		}

		List<String> requestIds = new ArrayList<String>();
		if (responseStr.contains(HeaderUtils.X_OCCI_LOCATION)) {
			String[] tokens = responseStr.split(HeaderUtils.X_OCCI_LOCATION);

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
			String occiAtt = st.nextToken().trim();
			StringTokenizer st2 = new StringTokenizer(occiAtt, ":");
			st2.nextToken(); //X-OCCI-Attribute
			String attToValue = st2.nextToken().trim();
			String[] attAndValue = attToValue.split("=");
			if (attAndValue.length == 2) {
				occiAttributes.put(attAndValue[0], attAndValue[1].replaceAll("\"", ""));
			} else {
				occiAttributes.put(attAndValue[0], "");
			}
		}
		return occiAttributes.get("request.state");
	}
}
