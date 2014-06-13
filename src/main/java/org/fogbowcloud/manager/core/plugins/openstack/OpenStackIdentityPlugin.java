package org.fogbowcloud.manager.core.plugins.openstack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenStackIdentityPlugin implements IdentityPlugin {

	public static final String OPEN_STACK_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	// keys for attributes map
	public static final String USER_KEY = "X-Token-User";
	public static final String PASSWORD_KEY = "X-Token-Password";
	public static final String TENANT_ID_KEY = "X-Token-TenantId";
	public static final String TENANT_NAME_KEY = "X-Token-TenantName";

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

	private static final int LAST_SUCCESSFUL_STATUS = 204;
	private final static Logger LOGGER = Logger.getLogger(OpenStackIdentityPlugin.class);
	/*
	 * The json response format can be seen in the following link:
	 * http://developer.openstack.org/api-ref-identity-v2.html
	 */
	public static String V2_TOKENS_ENDPOINT_PATH = "/v2.0/tokens";
	public static String V2_TENANTS_ENDPOINT_PATH = "/v2.0/tenants";

	private String v2TokensEndpoint;
	private String v2TenantsEndpoint;

	public OpenStackIdentityPlugin(Properties properties) {
		String keystoneUrl = properties.getProperty("identity_openstack_url");
		this.v2TokensEndpoint = keystoneUrl + V2_TOKENS_ENDPOINT_PATH;
		this.v2TenantsEndpoint = keystoneUrl + V2_TENANTS_ENDPOINT_PATH;
	}

	@Override
	public Token createToken(Map<String, String> credentials) {
		JSONObject json;
		try {
			json = mountJson(credentials);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		String responseStr = doPostRequest(v2TokensEndpoint, json);
		return getTokenFromJson(responseStr);
	}

	private JSONObject mountJson(Map<String, String> credentials) throws JSONException {
		JSONObject passwordCredentials = new JSONObject();
		passwordCredentials.put(USERNAME_PROP, credentials.get(OpenStackIdentityPlugin.USER_KEY));
		passwordCredentials.put(PASSWORD_PROP,
				credentials.get(OpenStackIdentityPlugin.PASSWORD_KEY));
		JSONObject auth = new JSONObject();
		auth.put(TENANT_NAME_PROP, credentials.get(TENANT_NAME_KEY));
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
			HttpClient client = new DefaultHttpClient();
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		checkStatusResponse(response);

		return responseStr;
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
		return getTokenFromJson(responseStr);
	}

	private JSONObject mountJson(Token token) throws JSONException {
		JSONObject idToken = new JSONObject();
		idToken.put(ID_PROP, token.getAccessId());
		JSONObject auth = new JSONObject();
		auth.put(TENANT_NAME_PROP, token.get(TENANT_NAME_KEY));
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

	private Token getTokenFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			JSONObject tokenKeyStone = root.getJSONObject(ACCESS_PROP).getJSONObject(TOKEN_PROP);
			String token = tokenKeyStone.getString(ID_PROP);
			String tenantId = "";
			String tenantName = "";
			Map<String, String> tokenAtt = new HashMap<String, String>();
			try {
				tenantId = tokenKeyStone.getJSONObject(TENANT_PROP).getString(ID_PROP);
				tenantName = tokenKeyStone.getJSONObject(TENANT_PROP).getString(NAME_PROP);
				tokenAtt.put(TENANT_ID_KEY, tenantId);
				tokenAtt.put(TENANT_NAME_KEY, tenantName);
			} catch (JSONException e) {
				LOGGER.debug("There are not tenant properties on json.");
			}
			String expirationDateToken = tokenKeyStone.getString(EXPIRES_PROP);
			String user = root.getJSONObject(ACCESS_PROP).getJSONObject(USER_PROP)
					.getString(NAME_PROP);

			LOGGER.debug("json token: " + token);
			LOGGER.debug("json user: " + user);
			LOGGER.debug("json expirationDate: " + expirationDateToken);
			LOGGER.debug("json attributes: " + tokenAtt);
			return new Token(token, user, getDateFromOpenStackFormat(expirationDateToken), tokenAtt);
		} catch (Exception e) {
			LOGGER.error("Exception while getting token from json.", e);
			return null;
		}
	}

	@Override
	public Token getToken(String accessId) {
		String responseStr;
		String tenantName = getTenantName(accessId);

		JSONObject root;
		try {
			JSONObject idToken = new JSONObject();
			idToken.put(ID_PROP, accessId);

			JSONObject auth = new JSONObject();
			auth.put(TOKEN_PROP, idToken);
			auth.put(TENANT_NAME_PROP, tenantName);

			root = new JSONObject();
			root.put(AUTH_PROP, auth);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		responseStr = doPostRequest(v2TokensEndpoint, root);
		return getTokenFromJson(responseStr);
	}

	private String getTenantName(String accessId) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.v2TenantsEndpoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, accessId);
			httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			httpGet.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);

			response = httpCLient.execute(httpGet);
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
			JSONObject tenantStone = tenantsStone.getJSONObject(0); // getting
																	// first
																	// tenant
			return tenantStone.getString(NAME_PROP);
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
}
