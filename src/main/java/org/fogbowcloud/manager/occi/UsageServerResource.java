package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.accounting.DataStore;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class UsageServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(UsageServerResource.class);

	@Get
	public String fetch() {
		LOGGER.debug("Executing the query interface fetch method");
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		Series<Header> headers = req.getHeaders();
		
		String federationAccessToken = HeaderUtils.getFederationAuthToken(headers, getResponse(),
				application.getAuthenticationURI());

		String option = (String) getRequestAttributes().get("option");
		
		StringBuilder builder = new StringBuilder("\n");
		
		if (option == null) {
			List<ResourceUsage> membersUsage = application.getMembersUsage(federationAccessToken);
			Map<String, Double> usersUsage = application.getUsersUsage(federationAccessToken);
			
			builder.append(generateMembersUsage(membersUsage));
			builder.append(generateUsersOutput(usersUsage));			
			return builder.toString();
		} else if ("members".equals(option)) {
			List<ResourceUsage> membersUsage = application.getMembersUsage(federationAccessToken);
			builder.append(generateMembersUsage(membersUsage));
			return builder.toString();
		} else if ("users".equals(option)) {
			Map<String, Double> usersUsage = application.getUsersUsage(federationAccessToken);			
			builder.append(generateUsersOutput(usersUsage));
			return builder.toString();
		}		
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}

	private String generateMembersUsage(List<ResourceUsage> membersUsage) {
		StringBuilder builder = new StringBuilder();
		for (ResourceUsage resourceUsage : membersUsage) {
			builder.append(resourceUsage.toString() + "\n");
		}			
		return builder.toString();
	}

	private String generateUsersOutput(Map<String, Double> usersUsage) {
		StringBuilder builder = new StringBuilder();
		for (String userId : usersUsage.keySet()) {
			builder.append("userId=" + userId + ", consumed=" + usersUsage.get(userId) + "\n");
		}
		return builder.toString();
	}

	public static Map<String, Double> getUsersUsage(String responseStr) {
		HashMap<String, Double> usersUsage = new HashMap<String, Double>();
		StringTokenizer st = new StringTokenizer(responseStr, "\n");
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
			if (line.contains("userId")) {
				StringTokenizer st2 = new StringTokenizer(line, ",");
				String token = st2.nextToken().trim();
				String userId = token.replace("userId=", ""); // userId

				token = st2.nextToken().trim(); // consumed
				Double consumed = Double.parseDouble(token.replace(DataStore.CONSUMED + "=", "")
						.trim());
				usersUsage.put(userId, consumed);
			}
		}
		return usersUsage;

	}

	public static List<ResourceUsage> getMembersUsage(String responseStr) {
		ArrayList<ResourceUsage> membersUsage = new ArrayList<ResourceUsage>();
		StringTokenizer st = new StringTokenizer(responseStr, "\n");
		while(st.hasMoreTokens()){
			String line = st.nextToken();
			if (line.contains("memberId")){
				membersUsage.add(ResourceUsage.parse(line));
			}
		}
		return membersUsage;
	
	}
}
