package org.fogbowcloud.manager.core.plugins.openstack;

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
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenStackIdentityPlugin implements IdentityPlugin {

	public static final String USERNAME_KEYSTONE = "username";
	public static final String PASSWORD_KEYSTONE = "password";
	public static final String PASSWORD_CREDENTIALS_KEYSTONE = "passwordCredentials";
	public static final String AUTH_KEYSTONE = "auth";
	public static final String TOKEN_KEYSTONE = "token";
	public static final String ID_KEYSTONE = "id";
	public static final String ACCESS_KEYSTONE = "Access";
	public static final String USER_KEYSTONE = "user";
	public static final String NAME_KEYSTONE = "name";

	private static String V3_ENDPOINT_PATH = "/v3/auth/tokens/";
	private static String V2_ENDPOINT_PATH = "/v2.0/tokens";

	private String v2Endpoint;
	private String v3Endpoint;

	public OpenStackIdentityPlugin(Properties properties) {
		String keystoneUrl = properties.getProperty("identity_openstack_url");
		this.v3Endpoint = keystoneUrl + V3_ENDPOINT_PATH;
		this.v2Endpoint = keystoneUrl + V2_ENDPOINT_PATH;
	}

	public String getUser(String authToken) {
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.v2Endpoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			httpGet.addHeader(OCCIHeaders.X_SUBJEC_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);
			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			return getUserNameUserFromJson(responseStr);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public String getToken(String username, String password) {
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(this.v3Endpoint);

			httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
			httpPost.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_ACCEPT);

			JSONObject rootCredentials = new JSONObject();
			rootCredentials.put(USERNAME_KEYSTONE, username);
			rootCredentials.put(PASSWORD_KEYSTONE, password);
			JSONObject rootAuth = new JSONObject();
			rootAuth.put(PASSWORD_CREDENTIALS_KEYSTONE, rootCredentials);
			JSONObject rootMain = new JSONObject();
			rootMain.put(AUTH_KEYSTONE, rootAuth);
			httpPost.setEntity(new StringEntity(rootMain.toString(), HTTP.UTF_8));

			HttpResponse response = httpClient.execute(httpPost);
			String responseStr = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}

			return getTokenFromJson(responseStr);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	private String getUserNameUserFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject(TOKEN_KEYSTONE).getJSONObject(USER_KEYSTONE)
					.getString(NAME_KEYSTONE);
		} catch (JSONException e) {
			return null;
		}
	}

	private String getTokenFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject(ACCESS_KEYSTONE).getJSONObject(TOKEN_KEYSTONE)
					.getString(ID_KEYSTONE);
		} catch (JSONException e) {
			return null;
		}
	}

	public void setEnd(String end) {
		this.v3Endpoint = end;
	}
}
