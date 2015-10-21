package org.fogbowcloud.manager.core.plugins.identity.cloudstack;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.common.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;

public class CloudStackIdentityPlugin implements IdentityPlugin {

	protected final static String REISSUE_COMMAND = "listApis";
	private final static String COMMAND = "command";

	public static final String API_KEY = "apiKey";
	protected static final String SECRET_KEY = "secretKey";
	public static final String SIGNATURE = "signature";

	private static final Logger LOGGER = Logger
			.getLogger(CloudStackIdentityPlugin.class);

	private Properties properties;
	private String endpoint;
	private HttpClientWrapper httpClient;

	public CloudStackIdentityPlugin(Properties properties) {
		this(properties, new HttpClientWrapper());
	}

	public CloudStackIdentityPlugin(Properties properties,
			HttpClientWrapper httpClient) {
		this.properties = properties;
		this.httpClient = httpClient;
		this.endpoint = this.properties.getProperty("identity_url");
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		LOGGER.debug("Creating token with credentials: " + userCredentials);
		if ((userCredentials == null) || (userCredentials.get(API_KEY) == null) 
				|| (userCredentials.get(SECRET_KEY) == null)) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "User credentials can't be null");
		}
		String apiKey = userCredentials.get(API_KEY);
		String secretKey = userCredentials.get(SECRET_KEY);
		String accessId = apiKey + ":" + secretKey;
		return new Token(accessId, apiKey, null, new HashMap<String, String>());
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		String[] accessIdSplit = accessId.split(":");
		if (accessIdSplit.length != 2) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		String apiKey = accessIdSplit[0];
		URIBuilder requestEndpoint = null;
		try {
			requestEndpoint = new URIBuilder(endpoint);
			requestEndpoint.addParameter(COMMAND, REISSUE_COMMAND);
			CloudStackHelper.sign(requestEndpoint, accessId);
		} catch (URISyntaxException e) {
			LOGGER.warn("Couldn't retrieve token.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED,
					ResponseConstants.UNAUTHORIZED);
		}
		HttpResponseWrapper response = httpClient.doGet(requestEndpoint.toString());
		checkStatusResponse(response.getStatusLine());
		return new Token(accessId, apiKey, null, new HashMap<String, String>());
	}

	@Override
	public boolean isValid(String accessId) {
		try {
			return getToken(accessId) != null;
		} catch (OCCIException e) {
			return false;
		}
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential(API_KEY, true, null),
				new Credential(SECRET_KEY, true, null) };
	}

	@Override
	public String getAuthenticationURI() {
		return null;
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		return null;
	}
	
	protected void checkStatusResponse(StatusLine statusLine) {
		if (statusLine.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (statusLine.getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST, statusLine.getReasonPhrase());
		}
	}

}
