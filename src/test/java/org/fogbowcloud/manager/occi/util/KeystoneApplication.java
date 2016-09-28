package org.fogbowcloud.manager.occi.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.plugins.identity.openstack.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONArray;
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
		router.attach(KeystoneIdentityPlugin.V2_TOKENS_ENDPOINT_PATH, KeystoneServer.class);
		router.attach(KeystoneIdentityPlugin.V2_TENANTS_ENDPOINT_PATH, KeystoneServer.class);
		return router;
	}

	public String getUserFromToken(String accessId) {
		return this.accessIdToUser.get(accessId);
	}

	public void putTokenAndUserId(String authToken, String user) {
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
		} else if (tenantName != null
				&& !defaultToken.get(KeystoneIdentityPlugin.TENANT_NAME).equals(tenantName)) {
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		}
	}

	public void checkAuthenticationCredentials(String username, String password, String tenantName) {
		if (!defaultToken.getUser().getName().equals(username) || !this.userPassword.equals(password)) {
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		} else if (tenantName != null
				&& !defaultToken.get(KeystoneIdentityPlugin.TENANT_NAME).equals(tenantName)) {
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
			if (getRequest().getResourceRef().toString()
					.endsWith(KeystoneIdentityPlugin.V2_TOKENS_ENDPOINT_PATH)) {
				String user = keyStoneApplication.getUserFromToken(token);
				return createUserTokenJSONResponse(token, user);
			} else {
				return createTenantJSONResponse(keyStoneApplication.getDefaultToken());

			}
		}

		private String createTenantJSONResponse(Token token) {
			JSONObject rootTenant = new JSONObject();
			try {
				rootTenant.put(KeystoneIdentityPlugin.ID_PROP,
						token.get(KeystoneIdentityPlugin.TENANT_ID));
				rootTenant.put(KeystoneIdentityPlugin.NAME_PROP,
						token.get(KeystoneIdentityPlugin.TENANT_NAME));

				JSONArray tenants = new JSONArray();
				tenants.put(rootTenant);
				JSONObject root = new JSONObject();
				return root.put(KeystoneIdentityPlugin.TENANTS_PROP, tenants).toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Post
		public Representation post(Representation entity) {
			KeystoneApplication keyStoneApplication = (KeystoneApplication) getApplication();

			String jsonCredentials = "";
			try {
				jsonCredentials = entity.getText();
			} catch (IOException e) {
				e.printStackTrace();
			}

			String tenantName = getTenantName(jsonCredentials);
			String idToken = getTokenAccessId(jsonCredentials);
			if (idToken != null) {
				keyStoneApplication.authenticationCheckToken(idToken, tenantName);
			} else {
				String username = getUserFeatureCredentials(jsonCredentials,
						KeystoneIdentityPlugin.USERNAME_PROP);
				String password = getUserFeatureCredentials(jsonCredentials,
						KeystoneIdentityPlugin.PASSWORD_PROP);

				keyStoneApplication.checkAuthenticationCredentials(username, password, tenantName);
			}

			return new StringRepresentation(
					createAuthenticationJSONResponse(keyStoneApplication.getDefaultToken()),
					MediaType.TEXT_ALL);
		}

		private String createAuthenticationJSONResponse(Token token) {
			try {
				String tokenAccessId = token.getAccessId();
				String tenantId = token.get(KeystoneIdentityPlugin.TENANT_ID);
				String tenantName = token.get(KeystoneIdentityPlugin.TENANT_NAME);

				String expirationDate = KeystoneIdentityPlugin.getDateOpenStackFormat(token
						.getExpirationDate());

				JSONObject rootIdToken = new JSONObject();

				JSONObject rootTenant = new JSONObject();
				rootTenant.put(KeystoneIdentityPlugin.ID_PROP, tenantId);
				rootTenant.put(KeystoneIdentityPlugin.NAME_PROP, tenantName);
				rootIdToken.put(KeystoneIdentityPlugin.ID_PROP, tokenAccessId);
				rootIdToken.put(KeystoneIdentityPlugin.EXPIRES_PROP, expirationDate);
				rootIdToken.put(KeystoneIdentityPlugin.TENANT_PROP, rootTenant);
				JSONObject rootAccess = new JSONObject();
				rootAccess.put(KeystoneIdentityPlugin.TOKEN_PROP, rootIdToken);
				JSONObject rootUserName = new JSONObject();
				rootUserName.put(KeystoneIdentityPlugin.NAME_PROP, token.getUser().getName());
				rootUserName.put(KeystoneIdentityPlugin.ID_PROP, token.getUser().getId());
				rootAccess.put(KeystoneIdentityPlugin.USER_PROP, rootUserName);
				JSONObject rootMain = new JSONObject();
				rootMain.put(KeystoneIdentityPlugin.ACCESS_PROP, rootAccess);
				return rootMain.toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		private String getTokenAccessId(String jsonCredentials) {
			try {
				JSONObject root = new JSONObject(jsonCredentials);
				return root.getJSONObject(KeystoneIdentityPlugin.AUTH_PROP)
						.getJSONObject(KeystoneIdentityPlugin.TOKEN_PROP)
						.getString(KeystoneIdentityPlugin.ID_PROP).toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		private String getTenantName(String jsonCredentials) {
			try {
				JSONObject root = new JSONObject(jsonCredentials);
				return root.getJSONObject(KeystoneIdentityPlugin.AUTH_PROP)
						.getString(KeystoneIdentityPlugin.TENANT_NAME_PROP).toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		private String getUserFeatureCredentials(String jsonCredentials, String feature) {
			try {
				JSONObject root = new JSONObject(jsonCredentials);
				return root.getJSONObject(KeystoneIdentityPlugin.AUTH_PROP)
						.getJSONObject(KeystoneIdentityPlugin.PASSWORD_CREDENTIALS_PROP)
						.getString(feature).toString();
			} catch (JSONException e) {
				return null;
			}
		}

		private String createUserTokenJSONResponse(String accessId, String user) {
			try {
				JSONObject usernameObject = new JSONObject();
				usernameObject.put(KeystoneIdentityPlugin.NAME_PROP, user);
				JSONObject userObject = new JSONObject();
				userObject.put(KeystoneIdentityPlugin.USER_PROP, usernameObject);
				JSONObject accessObject = new JSONObject();
				accessObject.put(KeystoneIdentityPlugin.ACCESS_PROP, userObject);
				return accessObject.toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
}