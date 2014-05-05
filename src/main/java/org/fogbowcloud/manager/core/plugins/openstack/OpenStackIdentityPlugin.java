package org.fogbowcloud.manager.core.plugins.openstack;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpException;
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
			HttpGet httpGet = new HttpGet(this.v3Endpoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			httpGet.addHeader(OCCIHeaders.X_SUBJECT_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}

			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));
			return getUserNameUserFromJson(responseStr);
		} catch (IOException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} catch (URISyntaxException e){
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} catch (HttpException e){
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public Token getToken(Map<String, String> tokenAttributes) {
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
			HttpResponse response = httpClient.execute(httpPost);
		
			String responseStr = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);		
			
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			
			return getTokenFromJson(responseStr);
		} catch (IOException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} catch (URISyntaxException e){
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} catch (HttpException e){
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} catch (JSONException e) {
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

	private Token getTokenFromJson(String responseStr) throws JSONException {
		Map<String, String> attributes = new HashMap<String, String>();
		JSONObject root = new JSONObject(responseStr);
		JSONObject tokenKeyStone = root.getJSONObject(ACCESS_KEYSTONE).getJSONObject(TOKEN_KEYSTONE);
		String token = tokenKeyStone.getString(ID_KEYSTONE);
		String tenantId = tokenKeyStone.getJSONObject(TENANT_KEYSTONE)
				.getString(ID_KEYSTONE);
		
		attributes.put(OCCIHeaders.X_TOKEN, token);
		attributes.put(OCCIHeaders.X_TOKEN_TENANT_ID, tenantId);
		
		return new Token(attributes);
	}

	public void setEnd(String end) {
		this.v3Endpoint = end;
	}
}
