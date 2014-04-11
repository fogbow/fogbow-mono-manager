package org.fogbowcloud.manager.occi.plugins.openstack;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.model.RequestHelper;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.json.JSONException;
import org.json.JSONObject;

public class IdentityOpenStackPlugin implements IdentityPlugin {

	private String keystoneEndPoint;
	
	public IdentityOpenStackPlugin(String endPoint) {
		this.keystoneEndPoint = endPoint;
	}
	
	public boolean isValidToken(String authToken) {
		try {
			HttpClient httpCLient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(this.keystoneEndPoint);
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
			HttpGet httpGet = new HttpGet(this.keystoneEndPoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			httpGet.addHeader(OCCIHeaders.X_SUBJEC_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);
			String responseStr = EntityUtils.toString(response.getEntity(), 
					RequestHelper.UTF_8);

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

	private String getUserNameUserFromJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject("token").getJSONObject("user").getString("name");			
		} catch (JSONException e) {
			return null;
		}

	}
}
