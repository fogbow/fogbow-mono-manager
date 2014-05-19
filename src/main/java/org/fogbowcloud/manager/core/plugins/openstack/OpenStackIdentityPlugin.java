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
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.json.JSONObject;

public class OpenStackIdentityPlugin implements IdentityPlugin {

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
	private static final Logger LOGGER = Logger.getLogger(OpenStackIdentityPlugin.class);
	private static final int LAST_SUCCESSFUL_STATUS = 204;

	private static String V2_ENDPOINT_PATH = "/v2.0/tokens";

	private String v2Endpoint;

	public OpenStackIdentityPlugin(Properties properties) {
		String keystoneUrl = properties.getProperty("identity_openstack_url");
		this.v2Endpoint = keystoneUrl + V2_ENDPOINT_PATH;
	}

	public String getResponseJson(String tokenId) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.v2Endpoint + "/" + tokenId);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, tokenId);
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
	public Token createToken(Map<String, String> tokenAttributes) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(this.v2Endpoint);

			httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			httpPost.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_ACCEPT);

			JSONObject passwordCredentials = new JSONObject();
			passwordCredentials.put(USERNAME_KEYSTONE,
					tokenAttributes.get(OCCIHeaders.X_TOKEN_USER));
			passwordCredentials.put(PASSWORD_KEYSTONE,
					tokenAttributes.get(OCCIHeaders.X_TOKEN_PASS));
			JSONObject auth = new JSONObject();
			auth.put(TENANT_NAME_KEYSTONE, tokenAttributes.get(OCCIHeaders.X_TOKEN_TENANT_NAME));
			auth.put(PASSWORD_CREDENTIALS_KEYSTONE, passwordCredentials);
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

	@Override
	public Token createToken(Token token) {
		HttpResponse response;
		String responseStr = null;
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(this.v2Endpoint);

			httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			httpPost.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_ACCEPT);

			JSONObject idToken = new JSONObject();
			idToken.put(ID_KEYSTONE, token.getAccessId());
			JSONObject auth = new JSONObject();
			auth.put(TENANT_NAME_KEYSTONE, token.get(OCCIHeaders.X_TOKEN_TENANT_NAME));
			auth.put(TOKEN_KEYSTONE, idToken);
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

	private Token getTokenFromJson(String responseStr) {
		try {
			Map<String, String> attributes = new HashMap<String, String>();
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

			attributes.put(OCCIHeaders.X_TOKEN_TENANT_ID, tenantId);
			attributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, tenantName);

			return new Token(token, user, getDate(expirationDateToken), attributes);
		} catch (Exception e) {
			LOGGER.error("Exception while getting token from json.", e);
			return null;
		}
	}

	private Date getDate(String expirationDateStr) {
		SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
				FederationMember.ISO_8601_DATE_FORMAT, Locale.ROOT);
		dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));
		try {
			return dateFormatISO8601.parse(expirationDateStr);
		} catch (Exception e) {
			LOGGER.error("Exception while getting date from String.", e);
			return null;
		}
	}

	@Override
	public Token getToken(String accessId) {
		String responseJson = getResponseJson(accessId);
		long expirationTimeMillis = 0;
		String user = null;
		try {
			//TODO Refactor! This code is repeated at many classes
			SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
					FederationMember.ISO_8601_DATE_FORMAT, Locale.ROOT);
			dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));

			JSONObject root = new JSONObject(responseJson);

			user = root.getJSONObject(ACCESS_KEYSTONE).getJSONObject(USER_KEYSTONE)
					.getString(NAME_KEYSTONE);

			String expirationTime = root.getJSONObject(ACCESS_KEYSTONE)
					.getJSONObject(TOKEN_KEYSTONE).getString(EXPIRES_KEYSTONE);
			expirationTimeMillis = dateFormatISO8601.parse(expirationTime).getTime();
		} catch (Exception e) {
			LOGGER.error(e);
		}

		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, user);

		return new Token(accessId, user, new Date(expirationTimeMillis), tokenAttributes);
	}

	@Override
	public boolean isValid(String accessId) {
		try{
			getToken(accessId);
			return true;
		} catch (OCCIException e){
			return false;
		}
	}

}
