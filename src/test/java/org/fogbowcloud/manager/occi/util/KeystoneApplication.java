package org.fogbowcloud.manager.occi.util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

public class KeystoneApplication extends Application {

	public static String TARGET_TOKEN_POST = "/v2.0/tokens";
	public static String TARGET_TOKEN_GET = "/v2.0/tokens/{tokenId}";

	private Map<String, String> accessIdToUser;

	private String userPassword;
	private Token defaultToken;

	public KeystoneApplication() {
		this.accessIdToUser = new HashMap<String, String>();
	}

	public KeystoneApplication(Token defaultToken, String userPassword) {
		this.accessIdToUser = new HashMap<String, String>();
		this.userPassword = userPassword;
		this.defaultToken = defaultToken;
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(TARGET_TOKEN_POST, KeystoneServer.class);
		router.attach(TARGET_TOKEN_GET, KeystoneServer.class);
		return router;
	}

	public String getUserFromToken(String accessId) {
		return this.accessIdToUser.get(accessId);
	}

	public void putTokenAndUser(String authToken, String user) {
		this.accessIdToUser.put(authToken, user);
	}

	public void checkUserByAccessId(String accessId) {
		String user = accessIdToUser.get(accessId);
		if (user == null || user.equals("")) {
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		}
	}

	public void authenticationCheckToken(String accessId, String tenantName) {
		if (!defaultToken.getAccessId().equals(accessId)) {
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		} else if (tenantName != null && !defaultToken.get(OpenStackIdentityPlugin.TENANT_NAME_KEY).equals(tenantName)){
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		}		 
	}

	public void checkAuthenticationCredentials(String username, String password, String tenantName) {
		if (!defaultToken.getUser().equals(username) || !this.userPassword.equals(password)) {
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		} else if (tenantName != null && !defaultToken.get(OpenStackIdentityPlugin.TENANT_NAME_KEY).equals(tenantName)){
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		}
	}

	public Token getDefaultToken() {
		return this.defaultToken;
	}

	public static class KeystoneServer extends ServerResource {

		@Get
		public String fetch() {
			KeystoneApplication keyStoneApplication = (KeystoneApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String token = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);
			keyStoneApplication.checkUserByAccessId(token);
			String user = keyStoneApplication.getUserFromToken(token);
			return mountJSONResponseUserPerToken(token, user);
		}

		@Post
		public Representation post(Representation entity) {
			KeystoneApplication keyStoneApplication = (KeystoneApplication) getApplication();
			
			String jsonCredentials = "";
			try {
				jsonCredentials = entity.getText();
			} catch (IOException e) {
			}

			String tenantName = getTenantName(jsonCredentials);
			String idToken = getIdToken(jsonCredentials);
			if (idToken != null) {
				keyStoneApplication.authenticationCheckToken(idToken, tenantName);
			} else {
				String username = getUserFeatureCredentials(jsonCredentials,
						OpenStackIdentityPlugin.USERNAME_KEYSTONE);
				String password = getUserFeatureCredentials(jsonCredentials,
						OpenStackIdentityPlugin.PASSWORD_KEYSTONE);

				keyStoneApplication.checkAuthenticationCredentials(username, password, tenantName);
			}

			return new StringRepresentation(
					mountJSONResponseAuthenticateToken(keyStoneApplication.getDefaultToken()),
					MediaType.TEXT_ALL);
		}

		private String mountJSONResponseAuthenticateToken(Token token) {
			try {
				String tokenAccessId = token.getAccessId();
				String tenantId = token.get(OpenStackIdentityPlugin.TENANT_ID_KEY);
				String tenantName = token.get(OpenStackIdentityPlugin.TENANT_NAME_KEY);

				String expirationDate = OpenStackIdentityPlugin.getDateOpenStackFormat(token
						.getExpirationDate());

				JSONObject rootIdToken = new JSONObject();

				JSONObject rootTenant = new JSONObject();
				rootTenant.put(OpenStackIdentityPlugin.ID_KEYSTONE, tenantId);
				rootTenant.put(OpenStackIdentityPlugin.NAME_KEYSTONE, tenantName);
				rootIdToken.put(OpenStackIdentityPlugin.ID_KEYSTONE, tokenAccessId);
				rootIdToken.put(OpenStackIdentityPlugin.EXPIRES_KEYSTONE, expirationDate);
				rootIdToken.put(OpenStackIdentityPlugin.TENANT_KEYSTONE, rootTenant);
				JSONObject rootAccess = new JSONObject();
				rootAccess.put(OpenStackIdentityPlugin.TOKEN_KEYSTONE, rootIdToken);
				JSONObject rootUserName = new JSONObject();
				rootUserName.put(OpenStackIdentityPlugin.NAME_KEYSTONE, token.getUser());
				rootAccess.put(OpenStackIdentityPlugin.USER_KEYSTONE, rootUserName);		
				JSONObject rootMain = new JSONObject();
				rootMain.put(OpenStackIdentityPlugin.ACCESS_KEYSTONE, rootAccess);
				return rootMain.toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		private String getIdToken(String jsonCredentials) {
			try {
				JSONObject root = new JSONObject(jsonCredentials);
				return root.getJSONObject(OpenStackIdentityPlugin.AUTH_KEYSTONE)
						.getJSONObject(OpenStackIdentityPlugin.TOKEN_KEYSTONE)
						.getString(OpenStackIdentityPlugin.ID_KEYSTONE).toString();
			} catch (JSONException e) {
				return null;
			}
		}

		private String getTenantName(String jsonCredentials) {
			try {
				JSONObject root = new JSONObject(jsonCredentials);
				return root.getJSONObject(OpenStackIdentityPlugin.AUTH_KEYSTONE)
						.getString(OpenStackIdentityPlugin.TENANT_NAME_KEYSTONE).toString();
			} catch (JSONException e) {
				return null;
			}
		}

		private String getUserFeatureCredentials(String jsonCredentials, String feature) {
			try {
				JSONObject root = new JSONObject(jsonCredentials);
				return root.getJSONObject(OpenStackIdentityPlugin.AUTH_KEYSTONE)
						.getJSONObject(OpenStackIdentityPlugin.PASSWORD_CREDENTIALS_KEYSTONE)
						.getString(feature).toString();
			} catch (JSONException e) {
				return null;
			}
		}

		private String mountJSONResponseUserPerToken(String token, String username) {
			try {
				JSONObject usernameObject = new JSONObject();
				usernameObject.put(OpenStackIdentityPlugin.NAME_KEYSTONE, username);
				JSONObject userObject = new JSONObject();
				userObject.put(OpenStackIdentityPlugin.USER_KEYSTONE, usernameObject);
				JSONObject accessObject = new JSONObject();
				accessObject.put(OpenStackIdentityPlugin.ACCESS_KEYSTONE, userObject);
				return accessObject.toString();
			} catch (JSONException e) {
			}
			return null;
		}
	}
}