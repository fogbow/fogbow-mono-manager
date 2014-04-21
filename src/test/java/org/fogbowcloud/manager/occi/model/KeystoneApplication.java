package org.fogbowcloud.manager.occi.model;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
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

	public static String TARGET_TOKENS = "/v3/auth/tokens/";
	public static String TARGET_AUTH_TOKEN = "/v2.0/tokens";

	private Map<String, String> tokenToUser;

	private String usernameAdmin;
	private String passwordAdmin;
	private String defaultToken;

	public KeystoneApplication() {
		this.tokenToUser = new HashMap<String, String>();
	}

	public KeystoneApplication(String usernameAdmin, String passwordAdmin, String defaultToken) {
		this.tokenToUser = new HashMap<String, String>();
		this.usernameAdmin = usernameAdmin;
		this.passwordAdmin = passwordAdmin;
		this.defaultToken = defaultToken;
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(TARGET_TOKENS, KeystoneServer.class);
		router.attach(TARGET_AUTH_TOKEN, KeystoneServer.class);
		return router;
	}

	public String getUserFromToken(String token) {
		return this.tokenToUser.get(token);
	}

	public void putTokenAndUser(String authToken, String user) {
		this.tokenToUser.put(authToken, user);
	}

	public void checkUserByToken(String token) {
		String user = this.tokenToUser.get(token);
		if (user == null || user.equals("")) {
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		}
	}

	public void authenticationCheck(String username, String password) {
		if (!this.usernameAdmin.equals(username) || !this.passwordAdmin.equals(password)) {
			throw new ResourceException(HttpStatus.SC_UNAUTHORIZED);
		}
	}

	public String getDefaultToken() {
		return this.defaultToken;
	}

	public static class KeystoneServer extends ServerResource {

		@Get
		public String fetch() {
			KeystoneApplication keyStoneApplication = (KeystoneApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String token = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);
			keyStoneApplication.checkUserByToken(token);
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

			String username = getUserFeatureCredentials(jsonCredentials,
					OpenStackIdentityPlugin.USERNAME_KEYSTONE);
			String password = getUserFeatureCredentials(jsonCredentials,
					OpenStackIdentityPlugin.PASSWORD_KEYSTONE);
			keyStoneApplication.authenticationCheck(username, password);

			return new StringRepresentation(
					mountJSONResponseAuthenticateToken(keyStoneApplication.getDefaultToken()),
					MediaType.TEXT_ALL);
		}

		private String mountJSONResponseAuthenticateToken(String token) {
			try {
				JSONObject rootIdTOken = new JSONObject();
				rootIdTOken.put(OpenStackIdentityPlugin.ID_KEYSTONE, token);
				JSONObject rootToken = new JSONObject();
				rootToken.put(OpenStackIdentityPlugin.TOKEN_KEYSTONE, rootIdTOken);
				JSONObject rootAccess = new JSONObject();
				rootAccess.put(OpenStackIdentityPlugin.ACCESS_KEYSTONE, rootToken);
				return rootAccess.toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
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

		private String mountJSONResponseUserPerToken(String token, String user) {
			try {
				JSONObject nameObject = new JSONObject();
				nameObject.put(OpenStackIdentityPlugin.NAME_KEYSTONE, user);
				JSONObject tokenObject = new JSONObject();
				tokenObject.put(OpenStackIdentityPlugin.USER_KEYSTONE, nameObject);
				JSONObject accessObject = new JSONObject();
				accessObject.put(OpenStackIdentityPlugin.TOKEN_KEYSTONE, tokenObject);
				return accessObject.toString();
			} catch (JSONException e) {
			}
			return null;
		}
	}
}