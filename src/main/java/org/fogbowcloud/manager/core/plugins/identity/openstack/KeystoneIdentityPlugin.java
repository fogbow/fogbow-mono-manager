package org.fogbowcloud.manager.core.plugins.identity.openstack;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class KeystoneIdentityPlugin implements IdentityPlugin {

	public static final String OPEN_STACK_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

	// keystone json data
	public static final String TENANT_NAME_PROP = "tenantName";
	public static final String USERNAME_PROP = "username";
	public static final String PASSWORD_PROP = "password";
	public static final String PASSWORD_CREDENTIALS_PROP = "passwordCredentials";
	public static final String AUTH_PROP = "auth";
	public static final String TOKEN_PROP = "token";
	public static final String ID_PROP = "id";
	public static final String TENANT_PROP = "tenant";
	public static final String TENANTS_PROP = "tenants";
	public static final String ACCESS_PROP = "access";
	public static final String EXPIRES_PROP = "expires";
	public static final String USER_PROP = "user";
	public static final String NAME_PROP = "name";
	
	public static final String AUTH_URL = "authUrl";
	public static final String USERNAME = "username";
	public static final String PASSWORD = "password";
	public static final String TENANT_NAME = "tenantName";
	public static final String TENANT_ID = "tenantId";	

	private static final int LAST_SUCCESSFUL_STATUS = 204;
	private final static Logger LOGGER = Logger.getLogger(KeystoneIdentityPlugin.class);
	/*
	 * The json response format can be seen in the following link:
	 * http://developer.openstack.org/api-ref-identity-v2.html
	 */
	public static String V2_TOKENS_ENDPOINT_PATH = "/v2.0/tokens";
	public static String V2_TENANTS_ENDPOINT_PATH = "/v2.0/tenants";

	private String keystoneUrl; 
	private String v2TokensEndpoint;
	private String v2TenantsEndpoint;
	private DefaultHttpClient client;
	private Properties properties;

	public KeystoneIdentityPlugin(Properties properties) {
		this.properties = properties;
		this.keystoneUrl = properties.getProperty(ConfigurationConstants.IDENTITY_URL);
		this.v2TokensEndpoint = keystoneUrl + V2_TOKENS_ENDPOINT_PATH;
		this.v2TenantsEndpoint = keystoneUrl + V2_TENANTS_ENDPOINT_PATH;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}
	
	@Override
	public Token createToken(Map<String, String> credentials) {
		return createToken(credentials, true);
	}
	
	private Token createToken(Map<String, String> credentials, boolean encodeJSON) {
		JSONObject json;
		try {
			json = mountJson(credentials);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		String authUrl = credentials.get(AUTH_URL);
		String currentTokenEndpoint = v2TokensEndpoint;
		if (authUrl != null && !authUrl.isEmpty()) {
			currentTokenEndpoint = authUrl + V2_TOKENS_ENDPOINT_PATH;
		}
		
		String responseStr = doPostRequest(currentTokenEndpoint, json);
		Token token = getTokenFromJson(responseStr, encodeJSON);
		
		return token;
	}
	
	private JSONObject mountJson(Map<String, String> credentials) throws JSONException {
		JSONObject passwordCredentials = new JSONObject();
		passwordCredentials.put(USERNAME_PROP, credentials.get(USERNAME));
		passwordCredentials.put(PASSWORD_PROP, credentials.get(PASSWORD));
		JSONObject auth = new JSONObject();
		auth.put(TENANT_NAME_PROP, credentials.get(TENANT_NAME));
		auth.put(PASSWORD_CREDENTIALS_PROP, passwordCredentials);
		JSONObject root = new JSONObject();
		root.put(AUTH_PROP, auth);
		return root;
	}

	private String doPostRequest(String endpoint, JSONObject json) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpPost request = new HttpPost(endpoint);
			request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
			request.setEntity(new StringEntity(json.toString(), HTTP.UTF_8));
			
			getClient();
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
		} catch (UnknownHostException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.UNKNOWN_HOST);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		checkStatusResponse(response);

		return responseStr;
	}

	private void getClient() {
		if (client == null) {
			client = new DefaultHttpClient();
			HttpParams params = new BasicHttpParams();
			params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
			client = new DefaultHttpClient(new ThreadSafeClientConnManager(params, client
					.getConnectionManager().getSchemeRegistry()), params);
		}
	}

	@Override
	public Token reIssueToken(Token token) {
		JSONObject json;
		try {
			json = mountJson(token);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		String responseStr = doPostRequest(v2TokensEndpoint, json);
		return getTokenFromJson(responseStr, false);
	}

	private static JSONObject mountJson(Token token) throws JSONException {
		return mountJson(token.getAccessId(), token.get(TENANT_NAME));
	}
	
	private static JSONObject mountJson(String accessId, String tenantName) throws JSONException {
		JSONObject idToken = new JSONObject();
		idToken.put(ID_PROP, accessId);
		JSONObject auth = new JSONObject();
		auth.put(TENANT_NAME_PROP, tenantName);
		auth.put(TOKEN_PROP, idToken);
		JSONObject root = new JSONObject();
		root.put(AUTH_PROP, auth);
		return root;
	}

	private void checkStatusResponse(HttpResponse response) {
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} else if (response.getStatusLine().getStatusCode() > LAST_SUCCESSFUL_STATUS) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getStatusLine().toString());
		}
	}

	private Token getTokenFromJson(String responseStr, boolean encodeJSON) {
		try {
			JSONObject root = new JSONObject(responseStr);
			JSONObject tokenKeyStone = root.getJSONObject(ACCESS_PROP).getJSONObject(TOKEN_PROP);
			String accessId = tokenKeyStone.getString(ID_PROP);
			
			Map<String, String> tokenAtt = new HashMap<String, String>();
			String tenantId = null;
			String tenantName = null;
			try {
				tenantId = tokenKeyStone.getJSONObject(TENANT_PROP).getString(ID_PROP);
				tokenAtt.put(TENANT_ID, tenantId);
			} catch (JSONException e) {
				LOGGER.debug("There is no tenantId inside json response.");
			}
			try {
				tenantName = tokenKeyStone.getJSONObject(TENANT_PROP).getString(NAME_PROP);
				tokenAtt.put(TENANT_NAME, tenantName);
			} catch (JSONException e) {
				LOGGER.debug("There is no tenantName inside json response.");
			}
			
			String expirationDateToken = tokenKeyStone.getString(EXPIRES_PROP);
			String user = root.getJSONObject(ACCESS_PROP).getJSONObject(
					USER_PROP).getString(NAME_PROP);

			LOGGER.debug("json token: " + accessId);
			LOGGER.debug("json user: " + user);
			LOGGER.debug("json expirationDate: " + expirationDateToken);
			LOGGER.debug("json attributes: " + tokenAtt);
			
			if (encodeJSON && (tenantId != null || tenantName != null)) {
				JSONObject jsonAccess = new JSONObject();
				jsonAccess.put(ACCESS_PROP, accessId);
				if (tenantId != null) {
					jsonAccess.put(TENANT_ID, tenantId);
				}
				if (tenantName != null) {
					jsonAccess.put(TENANT_NAME, tenantName);
				}
				accessId = new String(Base64.encodeBase64(jsonAccess.toString().getBytes(Charsets.UTF_8), 
						false, false), Charsets.UTF_8);
			}
			
			return new Token(accessId, user, getDateFromOpenStackFormat(expirationDateToken), tokenAtt);
		} catch (Exception e) {
			LOGGER.error("Exception while getting token from json.", e);
			return null;
		}
	}

	@Override
	public Token getToken(String accessId) {
		
		String tenantName = null;
		try {
			JSONObject decodedAccessId = new JSONObject(new String(Base64.decodeBase64(
					accessId.getBytes(Charsets.UTF_8)), Charsets.UTF_8));
			accessId = decodedAccessId.optString(ACCESS_PROP);
			tenantName = decodedAccessId.optString(TENANT_NAME);
		} catch (Exception e) {
			tenantName = getTenantName(accessId);
		}

		JSONObject root;
		try {
			root = mountJson(accessId, tenantName);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		String responseStr = doPostRequest(v2TokensEndpoint, root);
		return getTokenFromJson(responseStr, false);
	}

	private String getTenantName(String accessId) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpGet httpGet = new HttpGet(this.v2TenantsEndpoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, accessId);
			httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);

			getClient();
			response = client.execute(httpGet);
			responseStr = EntityUtils
					.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		checkStatusResponse(response);
		return getTenantNameFromJson(responseStr);
	}

	private String getTenantNameFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			JSONArray tenantsStone = root.getJSONArray(TENANTS_PROP);
			for (int i = 0; i < tenantsStone.length(); i++) {
				String currentTenantName = tenantsStone.getJSONObject(i).getString(NAME_PROP);
				// if tenantName is not the same of fogbow tenant
				if (currentTenantName != null && !currentTenantName.equals(properties
						.getProperty(ConfigurationConstants.FEDERATION_USER_TENANT_NAME_KEY))){
					return currentTenantName;
				}
				
			}
			return tenantsStone.getJSONObject(0).getString(NAME_PROP); // getting first tenant
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public boolean isValid(String accessId) {
		try {
			getToken(accessId);
			return true;
		} catch (OCCIException e) {
			return false;
		}
	}

	public static String getDateOpenStackFormat(Date date) {
		SimpleDateFormat dateFormatOpenStack = new SimpleDateFormat(OPEN_STACK_DATE_FORMAT,
				Locale.ROOT);
		String expirationDate = dateFormatOpenStack.format(date);
		return expirationDate;

	}

	public static Date getDateFromOpenStackFormat(String expirationDateStr) {
		SimpleDateFormat dateFormatOpenStack = new SimpleDateFormat(OPEN_STACK_DATE_FORMAT,
				Locale.ROOT);
		try {
			return dateFormatOpenStack.parse(expirationDateStr);
		} catch (Exception e) {
			LOGGER.error("Exception while parsing date.", e);
			return null;
		}
	}

	@Override
	public Token createFederationUserToken() {
		Map<String, String> federationUserCredentials = new HashMap<String, String>();
		String username = properties.getProperty(ConfigurationConstants.FEDERATION_USER_NAME_KEY);
		String password = properties.getProperty(ConfigurationConstants.FEDERATION_USER_PASS_KEY);
		String tenantName = properties
				.getProperty(ConfigurationConstants.FEDERATION_USER_TENANT_NAME_KEY);
		federationUserCredentials.put(USERNAME, username);
		federationUserCredentials.put(PASSWORD, password);
		federationUserCredentials.put(TENANT_NAME, tenantName);
		
		return createToken(federationUserCredentials, false);
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential(USERNAME, true, null),
				new Credential(PASSWORD, true, null), new Credential(TENANT_NAME, true, null),
				new Credential(AUTH_URL, true, null) };
	}

	@Override
	public String getAuthenticationURI() {
		return "Keystone uri='" + keystoneUrl +"'";
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		return null;
	}	
 }
