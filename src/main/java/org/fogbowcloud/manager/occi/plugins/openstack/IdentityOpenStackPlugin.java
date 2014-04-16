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
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.json.JSONException;
import org.json.JSONObject;

public class IdentityOpenStackPlugin implements IdentityPlugin {

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
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			return getUserNameUserFromJson(responseStr);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
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
			rootCredentials.put("username", username);
			rootCredentials.put("password", password);
			JSONObject rootAuth = new JSONObject();
			rootAuth.put("passwordCredentials", rootCredentials);			
			JSONObject rootMain = new JSONObject();
			rootMain.put("auth", rootAuth);
			
			httpPost.setEntity(new StringEntity(rootMain.toString(), HTTP.UTF_8));
			HttpResponse response = httpClient.execute(httpPost);
			String responseStr = EntityUtils.toString(response.getEntity(),
					HTTP.UTF_8);
			
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}	
			
			return getTokenFromJson(responseStr);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "");
		}
	}
	
	private String getUserNameUserFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject("token").getJSONObject("user").getString("name");
		} catch (JSONException e) {
			return null;
		}
	}
	
	private String getTokenFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject("access").getJSONObject("token").getString("id");
		} catch (JSONException e) {
			return null;
		}
	}
	
	public void setEnd(String end){
		this.keystoneEndPointAuthToken = end;
	}
}
