package org.fogbowcloud.manager.core.plugins.identity.naf;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONException;
import org.json.JSONObject;

public class NAFIdentityPlugin implements IdentityPlugin {

	protected static final String TOKEN_URL_OPERATION = "/token/";
	protected static final String METHOD_GET_VALIDITY_CHECK = "?method=validityCheck";
	protected static final String TYPE = "type";
	protected static final String VALID_RESPONSE_TOKEN_GENERATOR = "Valid";
	protected static final String PASSWORD_USER_TOKEN_GENERATOR = "password_user_token_generator";
	protected static final String NAME_USER_TOKEN_GENERATOR = "name_user_token_generator";
	protected static final String ENDPOINT_TOKEN_GENERATOR = "endpoint_token_generator";
	protected static final String DEFAULT_TYPE_TOKEN_GENERATOR = "token_generator";
	protected static final String SAML_ATTRIBUTES_JSONOBJECT = "saml_attributes";
	protected static final String TOKEN_ETIME_JSONOBJECT = "token_etime";
	protected static final String PASSWORD = "password";
	protected static final String NAME = "name";
	
	private static final Logger LOGGER = Logger.getLogger(NAFIdentityPlugin.class);
	protected static final String NAF_PUBLIC_KEY = "naf_identity_public_key";
	protected static final String STRING_SEPARATOR = "!#!";
	
	private Properties properties;
	private HttpClient client;
	
	public NAFIdentityPlugin(Properties properties) {
		this.properties = properties;
		initClient();
	}

	private void initClient() {
		client = HttpClients.createMinimal();
	}
	
	protected HttpClient getClient() {
		return client;
	}
	
	protected void setClient(HttpClient client) {
		this.client = client;
	}
	
	@Override
	public Token createToken(Map<String, String> userCredentials) {
		return null;
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		if (accessId == null || accessId.isEmpty()) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		if (!isValid(accessId)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}		
			
		JSONObject jsonObject;
		String user = null;
		Date date = null;
		Map<String, String> attributes = null;
		try {
			String accessIdDecoded = new String(Base64.decode(accessId.getBytes()), Charset.forName("UTF-8")); 
			AccessIdFormat accessIdFormat = new AccessIdFormat(accessIdDecoded);
			jsonObject = new JSONObject(accessIdFormat.getMessage());
			user = jsonObject.getString(NAME);
			date = new Date(Long.parseLong(jsonObject.getString(TOKEN_ETIME_JSONOBJECT)));
			attributes = toMap(jsonObject.optJSONObject(SAML_ATTRIBUTES_JSONOBJECT));
		} catch (Exception e) {
			LOGGER.error("Could not create token by accessId.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		return new Token(accessId, user, date, attributes);
	}

	@Override
	public boolean isValid(String accessId) {	
		if (accessId == null || accessId.isEmpty()) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		String accessIdDecoded = new String(Base64.decode(accessId.getBytes()), Charset.forName("UTF-8")); 		
		RSAPublicKey publicKey = null;
		try {
			publicKey = RSAUtils.getPublicKey(properties.getProperty(NAF_PUBLIC_KEY));
		} catch (Exception e) {
			LOGGER.warn("Could not create RSA public key.", e);
			return false;
		}
		
		AccessIdFormat accessIdFormat = null;
		try {
			accessIdFormat = new AccessIdFormat(accessIdDecoded);
			RSAUtils.verify(publicKey, accessIdFormat.getMessage(), accessIdFormat.getSignature());
		} catch (Exception e) {			
			LOGGER.warn("Could not verify access id.", e);
			return false;
		}
		
		try {
			String jsonTokenSlice = accessIdFormat.getMessage();
			String type = new JSONObject(jsonTokenSlice).optString(TYPE);
			if (jsonTokenSlice != null && type != null && type.equals(DEFAULT_TYPE_TOKEN_GENERATOR)) {
				if (!isValidTokenGenerator(accessIdFormat.getMessage(), URLEncoder.encode(accessId, "UTF-8"))) {
					return false;
				}				
			}
		} catch (Exception e) {
			LOGGER.warn("Could not check if is token generator.", e);
			return false;
		}
		
		return true;
	}

	private boolean isValidTokenGenerator(String json, String accessId) throws JSONException {		
		String endpoint = this.properties.getProperty(ENDPOINT_TOKEN_GENERATOR);
		String username = this.properties.getProperty(NAME_USER_TOKEN_GENERATOR);
		String password = this.properties.getProperty(PASSWORD_USER_TOKEN_GENERATOR);
		String responseStr = doGetRequest(endpoint + TOKEN_URL_OPERATION + accessId 
				+ METHOD_GET_VALIDITY_CHECK, username, password);
				
		if (!responseStr.equals(VALID_RESPONSE_TOKEN_GENERATOR)) {
			return false;
		}
		
		return true;
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential(NAME, true, null) };
	}

	@Override
	public String getAuthenticationURI() {
		return null;
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		return originalToken;
	}	
	
	@SuppressWarnings("unchecked")
	protected static Map<String, String> toMap(JSONObject jsonObject) throws JSONException {
		if (jsonObject == null) {
			return null;
		}
	    Map<String, String> map = new HashMap<String, String>();

		Iterator<String> keysItr = jsonObject.keys();
	    while(keysItr.hasNext()) {
	        String key = keysItr.next();
	        String value = jsonObject.getString(key);
	        map.put(key, value);
	    }
	    return map;
	}	
	
	protected String doGetRequest(String endpoint, String username, String password) {
		HttpResponse response = null;
		String responseStr = null;
		try {
			HttpUriRequest request = new HttpGet(endpoint);			
			request.addHeader(NAME, username);
			request.addHeader(PASSWORD, password);
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error("Could not make GET request.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				EntityUtils.consume(response.getEntity());
			} catch (Throwable t) {
				// Do nothing
			}
		}
		checkStatusResponse(response, responseStr);
		return responseStr;
	}	
	
	protected void checkStatusResponse(HttpResponse response, String message) {
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (response.getStatusLine().getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getStatusLine().toString());
		}
	}	
	
	private class AccessIdFormat {
		
		private String signature;
		private String message;
		
		public AccessIdFormat(String accessIdStr) throws Exception {
			int signatureIndex = 1;
			int messageIndex = 0;
			String[] partiesAccessId = accessIdStr.split(STRING_SEPARATOR);
			if (partiesAccessId.length != 2) {
				throw new Exception("Invalid format.");
			}
			this.signature = partiesAccessId[signatureIndex];
			this.message = partiesAccessId[messageIndex];
		}
		
		public String getSignature() {
			return signature;
		}
		
		public String getMessage() {
			return message;
		}
		
	}
}
