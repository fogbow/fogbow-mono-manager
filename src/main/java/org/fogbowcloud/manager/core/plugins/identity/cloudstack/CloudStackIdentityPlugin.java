package org.fogbowcloud.manager.core.plugins.identity.cloudstack;

import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONException;
import org.json.JSONObject;

public class CloudStackIdentityPlugin implements IdentityPlugin {
	
	public final static String USER = "username";
	public final static String PASSWORD = "password";
	public final static String DOMAIN = "domain";
	public final static String RESPONSE_FORMAT = "response";
	public final static String JSON_FORMAT = "json";
	public final static String COMMAND = "command";
	public final static String LOGIN = "login";
	public final static String ACCESS_PROP = "loginresponse";
	public final static String ACCESS_ID = "sessionkey"; 
	public final static String TIME_OUT = "timeout";
	public static final Logger LOGGER = Logger.getLogger(CloudStackIdentityPlugin.class);
	
	private Properties properties;
	private String endpoint;
	private HttpClientWrapper httpClient;
	
	public CloudStackIdentityPlugin(Properties properties) {
		this(properties, new HttpClientWrapper());
	}
	
	public CloudStackIdentityPlugin(Properties properties, HttpClientWrapper httpClient) {
		this.properties = properties;
		this.httpClient = httpClient;
		this.endpoint = this.properties.getProperty("identity_url");
	}
	
	private String doPost(String username, String password, String domain)  {
		try {
			URIBuilder requestEndpoint = new URIBuilder(endpoint);
			requestEndpoint.addParameter(COMMAND, LOGIN);
			requestEndpoint.addParameter(USER, username);
			requestEndpoint.addParameter(PASSWORD, password);
			if (domain != null) {
				requestEndpoint.addParameter(DOMAIN, domain);
			}
			requestEndpoint.addParameter(RESPONSE_FORMAT, JSON_FORMAT);
			return httpClient.doPost(requestEndpoint.build().toString());
		} catch (URISyntaxException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
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
}
