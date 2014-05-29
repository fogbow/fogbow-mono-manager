package org.fogbowcloud.manager.core.plugins.openstack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

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
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
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
	public static final String TENANT_NAME_KEYSTONE = "tenantName";
	public static final String USERNAME_KEYSTONE = "username";
	public static final String PASSWORD_KEYSTONE = "password";
	public static final String PASSWORD_CREDENTIALS_KEYSTONE = "passwordCredentials";
	public static final String AUTH_KEYSTONE = "auth";
	public static final String TOKEN_KEYSTONE = "token";
	public static final String ID_KEYSTONE = "id";
	public static final String TENANT_KEYSTONE = "tenant";
	public static final String ACCESS_KEYSTONE = "access";
	public static final String EXPIRES_KEYSTONE = "expires";
	public static final String USER_KEYSTONE = "user";
	public static final String NAME_KEYSTONE = "name";

	private static final int LAST_SUCCESSFUL_STATUS = 204;
	private final static Logger LOGGER = Logger.getLogger(OpenStackIdentityPlugin.class);
	private static String V2_ENDPOINT_PATH = "/v2.0/tokens";
	private String v2Endpoint;

	public OpenStackIdentityPlugin(Properties properties) {
		String keystoneUrl = properties.getProperty("identity_openstack_url");
		this.v2Endpoint = keystoneUrl + V2_ENDPOINT_PATH;
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

		String responseStr = doPostRequest(v2Endpoint, json);
		return getTokenFromJson(responseStr);
	}

	private JSONObject mountJson(Map<String, String> credentials) throws JSONException {
		JSONObject passwordCredentials = new JSONObject();
		passwordCredentials.put(USERNAME_KEYSTONE,
				credentials.get(OpenStackIdentityPlugin.USER_KEY));
		passwordCredentials.put(PASSWORD_KEYSTONE,
				credentials.get(OpenStackIdentityPlugin.PASSWORD_KEY));
		JSONObject auth = new JSONObject();
		auth.put(TENANT_NAME_KEYSTONE, credentials.get(TENANT_NAME_KEY));
		auth.put(PASSWORD_CREDENTIALS_KEYSTONE, passwordCredentials);
		JSONObject root = new JSONObject();
		root.put(AUTH_KEYSTONE, auth);
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
	public Token createToken(Token token) {
		JSONObject json;
		try {
			json = mountJson(token);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}

		String responseStr = doPostRequest(v2Endpoint, json);
		return getTokenFromJson(responseStr);
	}

	private JSONObject mountJson(Token token) throws JSONException {
		JSONObject idToken = new JSONObject();
		idToken.put(ID_KEYSTONE, token.getAccessId());
		JSONObject auth = new JSONObject();
		auth.put(TENANT_NAME_KEYSTONE, token.get(TENANT_NAME_KEY));
		auth.put(TOKEN_KEYSTONE, idToken);
		JSONObject root = new JSONObject();
		root.put(AUTH_KEYSTONE, auth);
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
			JSONObject tokenKeyStone = root.getJSONObject(ACCESS_KEYSTONE).getJSONObject(
					TOKEN_KEYSTONE);
			String token = tokenKeyStone.getString(ID_KEYSTONE);
			String tenantId = tokenKeyStone.getJSONObject(TENANT_KEYSTONE).getString(ID_KEYSTONE);
			String tenantName = tokenKeyStone.getJSONObject(TENANT_KEYSTONE).getString(
					NAME_KEYSTONE);
			String expirationDateToken = tokenKeyStone.getString(EXPIRES_KEYSTONE);
			String user = root.getJSONObject(ACCESS_KEYSTONE).getJSONObject(USER_KEYSTONE)
					.getString(NAME_KEYSTONE);

			Map<String, String> tokenAtt = new HashMap<String, String>();
			tokenAtt.put(TENANT_ID_KEY, tenantId);
			tokenAtt.put(TENANT_NAME_KEY, tenantName);
			LOGGER.debug("json token: " + token);
			LOGGER.debug("json user: " + user);
			LOGGER.debug("json expirationDate: " + expirationDateToken);
			LOGGER.debug("json attributes: " + tokenAtt);			
			return new Token(token, user, getDateFromOpenStackFormat(expirationDateToken),
					tokenAtt);
		} catch (Exception e) {
			LOGGER.error("Exception while getting token from json.", e);
			return null;
		}
	}

	@Override
	public Token getToken(String accessId) {
		JSONObject root;
		try {
			JSONObject idToken = new JSONObject();
			idToken.put(ID_KEYSTONE, accessId);
			JSONObject auth = new JSONObject();
			auth.put(TOKEN_KEYSTONE, idToken);
			root = new JSONObject();
			root.put(AUTH_KEYSTONE, auth);		
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		String responseStr = doPostRequest(v2Endpoint, root);
		return getTokenFromJson(responseStr);
	}

	/*
	 * The json response format can be seen in the following link:
	 * http://developer.openstack.org/api-ref-identity-v2.html
	 */
	public String getResponseJson(String accessId) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.v2Endpoint + "/" + accessId);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, accessId);
			response = httpCLient.execute(httpGet);

			responseStr = EntityUtils
					.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		checkStatusResponse(response);

		return responseStr;
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
		SimpleDateFormat dateFormatOpenStack = new SimpleDateFormat(OPEN_STACK_DATE_FORMAT, Locale.ROOT);
		String expirationDate = dateFormatOpenStack.format(date);
		return expirationDate;

	}
	
	public static Date getDateFromOpenStackFormat(String expirationDateStr) {
		SimpleDateFormat dateFormatOpenStack = new SimpleDateFormat(
				OPEN_STACK_DATE_FORMAT, Locale.ROOT);
		try {
			return dateFormatOpenStack.parse(expirationDateStr);
		} catch (Exception e) {
			LOGGER.error("Exception while parsing date.", e);
			return null;
		}
	}
}
