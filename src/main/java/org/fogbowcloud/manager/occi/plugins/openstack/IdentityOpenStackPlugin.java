package org.fogbowcloud.manager.occi.plugins.openstack;

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
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.resource.ResourceException;

public class IdentityOpenStackPlugin implements IdentityPlugin {

	public static final String USERNAME_KEYSTONE = "username";
	public static final String PASSWORD_KEYSTONE = "password";
	public static final String PASSWORD_CREDENTIALS_KEYSTONE = "passwordCredentials";
	public static final String AUTH_KEYSTONE = "auth";
	public static final String TOKEN_KEYSTONE = "token";
	public static final String ID_KEYSTONE = "id";
	public static final String ACCESS_KEYSTONE = "Access";
	public static final String USER_KEYSTONE = "user";
	public static final String NAME_KEYSTONE = "name";
	
	private String keystoneEndPointTokens;
	private String keystoneEndPointAuthToken;

	public IdentityOpenStackPlugin(String endPointTokens, String endPointAuthToken) {
		this.keystoneEndPointTokens = endPointTokens;
		this.keystoneEndPointAuthToken = endPointAuthToken;
	}

	public boolean isValidToken(String authToken) {
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.keystoneEndPointTokens);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			httpGet.addHeader(OCCIHeaders.X_SUBJEC_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED
					|| response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				return false;
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public String getUser(String authToken) {
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.keystoneEndPointTokens);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			httpGet.addHeader(OCCIHeaders.X_SUBJEC_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);
			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
			}
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new ResourceException(HttpStatus.SC_NOT_FOUND);
			}
			return getUserNameUserFromJson(responseStr);
		} catch (Exception e) {
			throw new ResourceException(HttpStatus.SC_BAD_REQUEST);
		}
	}

	@Override
	public String getToken(String username, String password) {
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(this.keystoneEndPointAuthToken);

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
				throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
			}
			
			return getTokenFromJson(responseStr);
		} catch (Exception e) {
			throw new ResourceException(HttpStatus.SC_BAD_REQUEST);
		}
	}

	private String getUserNameUserFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject(TOKEN_KEYSTONE)
					.getJSONObject(USER_KEYSTONE).getString(NAME_KEYSTONE);
		} catch (JSONException e) {
			return null;
		}
	}

	private String getTokenFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject(ACCESS_KEYSTONE)
					.getJSONObject(TOKEN_KEYSTONE).getString(ID_KEYSTONE);
		} catch (JSONException e) {
			return null;
		}
	}

	public void setEnd(String end) {
		this.keystoneEndPointAuthToken = end;
	}
}
