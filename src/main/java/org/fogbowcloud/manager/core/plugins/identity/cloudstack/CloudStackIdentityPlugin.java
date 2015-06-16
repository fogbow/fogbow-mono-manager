package org.fogbowcloud.manager.core.plugins.identity.cloudstack;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONException;
import org.json.JSONObject;

public class CloudStackIdentityPlugin implements IdentityPlugin {
	
	private final static String USER = "username";
	private final static String PASSWORD = "password";
	private final static String DOMAIN = "domain";
	private final static String RESPONSE_FORMAT = "response";
	private final static String JSON_FORMAT = "json";
	private final static String COMMAND = "command";
	private final static String LOGIN = "login";
	private final static String ACCESS_PROP = "loginresponse";
	private final static String ACCESS_ID = "sessionkey"; 
	private final static String TIME_OUT = "timeout";
	private static final Logger LOGGER = Logger.getLogger(CloudStackIdentityPlugin.class);
	
	private Properties properties;
	private String endpoint;
	private HttpClient client;
	
	private HttpClient getClient() {
		if (client == null) {
			client = HttpClients.createMinimal();
		}
		return client;
	}
	
	public CloudStackIdentityPlugin(Properties properties) {
		this.properties = properties;
		this.endpoint = this.properties.getProperty("identity_url");
	}
	
	private void checkStatusResponse(HttpResponse response) {
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} 
	}
	
	private String doPost(String username, String password, String domain)  {
		HttpResponse response = null;
		String responseStr = null;
		URIBuilder requestEndpoint;
		try {
			requestEndpoint = new URIBuilder(endpoint);
			requestEndpoint.addParameter(COMMAND, LOGIN);
			requestEndpoint.addParameter(USER, username);
			requestEndpoint.addParameter(PASSWORD, password);
			if (domain != null) {
				requestEndpoint.addParameter(DOMAIN, domain);
			}
			requestEndpoint.addParameter(RESPONSE_FORMAT, JSON_FORMAT);
			HttpPost request = new HttpPost(requestEndpoint.toString());
			response = getClient().execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error("Could not do post request.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		checkStatusResponse(response);
		return responseStr;
	}
	
	private Token getTokenFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			JSONObject tokenKeyStone = root.getJSONObject(ACCESS_PROP);
			String accessId = tokenKeyStone.getString(ACCESS_ID);
			String user = tokenKeyStone.getString(USER);
		    Long timeOut = Long.parseLong(tokenKeyStone.getString(TIME_OUT));
		    Date expirationDate = new Date(new Date().getTime() + timeOut * 1000);
		    return new Token(accessId, user, expirationDate, null);
		} catch (JSONException e) {
			LOGGER.error("Exception while getting token from json.", e);
			return null;
		}
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		String username = userCredentials.get(USER);
		String password = userCredentials.get(PASSWORD);
		String domain = userCredentials.get(DOMAIN);
		String responseStr = doPost(username, password, domain);
		return getTokenFromJson(responseStr);
	}

	@Override
	public Token reIssueToken(Token token) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Token getToken(String accessId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isValid(String accessId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Token createFederationUserToken() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Credential[] getCredentials() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getAuthenticationURI() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public static void main(String[] args) throws ClientProtocolException, IOException, URISyntaxException {
		Properties p = new Properties();
		p.setProperty("identity_url", "http://10.4.10.247:8080/client/api");
		CloudStackIdentityPlugin csp = new CloudStackIdentityPlugin(p);
		csp.getTokenFromJson(csp.doPost("admin", "password", null));
		Map<String, String> uc = new HashMap<String, String>();
		uc.put(USER, "admin");
		uc.put(PASSWORD, "password");
		
		System.out.println(csp.createToken(uc));
	}

}
