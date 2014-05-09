package org.fogbowcloud.manager.core.plugins.openstack;

import java.util.HashMap;
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
import org.json.JSONException;
import org.json.JSONObject;

public class OpenStackIdentityPlugin implements IdentityPlugin {

	private static final String KEYSTONE_TENANT_NAME = "tenantName";
	public static final String KEYSTONE_USERNAME = "username";
	public static final String KEYSTONE_PASSWORD = "password";
	public static final String PASSWORD_CREDENTIALS = "passwordCredentials";
	public static final String AUTH_KEYSTONE = "auth";
	public static final String TOKEN_KEYSTONE = "token";
	public static final String ID_KEYSTONE = "id";
	public static final String TENANT_KEYSTONE = "tenant";
	public static final String ACCESS_KEYSTONE = "access";
	public static final String EXPIRES_KEYSTONE = "expires";
	public static final String USER_KEYSTONE = "user";
	public static final String NAME_KEYSTONE = "name";
	private static final Logger LOGGER = Logger.getLogger(OpenStackIdentityPlugin.class);
	private static final int LAST_SUCCESSFUL_STATUS = 204;

	private static String V2_ENDPOINT_PATH = "/v2.0/tokens";

	private String v2Endpoint;

	public OpenStackIdentityPlugin(Properties properties) {
		String keystoneUrl = properties.getProperty("identity_openstack_url");
		this.v2Endpoint = keystoneUrl + V2_ENDPOINT_PATH;
	}

	@Override
	public String getTokenExpiresDate(String tokenId) {
		return getUserDateExpirationTokenFromJson(getResponseJson(tokenId));
	}

	public String getUser(String authToken) {
		return getUserNameUserFromJson(getResponseJson(authToken));
	}

	public String getResponseJson(String authToken) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.v2Endpoint + "/" + authToken);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
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
	public Token getToken(Map<String, String> tokenAttributes) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(this.v2Endpoint);

			httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			httpPost.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_ACCEPT);

			JSONObject passwordCredentials = new JSONObject();
			passwordCredentials.put(KEYSTONE_USERNAME,
					tokenAttributes.get(OCCIHeaders.X_TOKEN_USER));
			passwordCredentials.put(KEYSTONE_PASSWORD,
					tokenAttributes.get(OCCIHeaders.X_TOKEN_PASS));
			JSONObject auth = new JSONObject();
			auth.put(KEYSTONE_TENANT_NAME, tokenAttributes.get(OCCIHeaders.X_TOKEN_TENANT_NAME));
			auth.put(PASSWORD_CREDENTIALS, passwordCredentials);
			JSONObject root = new JSONObject();
			root.put(AUTH_KEYSTONE, auth);
			httpPost.setEntity(new StringEntity(root.toString(), HTTP.UTF_8));
			response = httpClient.execute(httpPost);

			responseStr = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		checkStatusResponse(response);

		return getTokenFromJson(responseStr);
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

	private String getUserNameUserFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject(ACCESS_KEYSTONE).getJSONObject(USER_KEYSTONE)
					.getString(NAME_KEYSTONE);
		} catch (JSONException e) {
			return null;
		}
	}

	private String getUserDateExpirationTokenFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject(ACCESS_KEYSTONE).getJSONObject(TOKEN_KEYSTONE)
					.getString(EXPIRES_KEYSTONE);
		} catch (JSONException e) {
			return null;
		}
	}

	private Token getTokenFromJson(String responseStr) {
		try {
			Map<String, String> attributes = new HashMap<String, String>();
			JSONObject root = new JSONObject(responseStr);
			JSONObject tokenKeyStone = root.getJSONObject(ACCESS_KEYSTONE).getJSONObject(
					TOKEN_KEYSTONE);
			String token = tokenKeyStone.getString(ID_KEYSTONE);
			String tenantId = tokenKeyStone.getJSONObject(TENANT_KEYSTONE).getString(ID_KEYSTONE);
			String expirationDateToken = tokenKeyStone.getString(EXPIRES_KEYSTONE);

			attributes.put(OCCIHeaders.X_TOKEN_ACCESS_ID, token);
			attributes.put(OCCIHeaders.X_TOKEN_TENANT_ID, tenantId);
			attributes.put(OCCIHeaders.X_TOKEN_EXPIRATION_DATE, expirationDateToken);

			return new Token(attributes);
		} catch (Exception e) {
			return null;
		}
	}
}
