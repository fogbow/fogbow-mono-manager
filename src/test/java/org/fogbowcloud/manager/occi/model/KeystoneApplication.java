package org.fogbowcloud.manager.occi.model;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
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
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

public class KeystoneApplication extends Application {

	public static String TARGET_TOKENS = "/v3/auth/tokens/";
	public static String TARGET_AUTH_TOKEN = "/v2.0/tokens";

	private Map<String,String> tokenToUser;
	
	private String usernameAdmin;
	private String passwordAdmin;
	private String defaultToken;
	
	public KeystoneApplication() {
		this.tokenToUser = new HashMap<String, String>();
	}
	
	public KeystoneApplication(String usernameAdmin, String passwordAdmin) {
		this.tokenToUser = new HashMap<String, String>();
		this.usernameAdmin = usernameAdmin;
		this.passwordAdmin = passwordAdmin;
	}
	
	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(TARGET_TOKENS, KeystoneServer.class);
		router.attach(TARGET_AUTH_TOKEN, KeystoneServer.class);
		return router;
	}
	
	public String getUserFromToken(String token){
		return this.tokenToUser.get(token);
	}
	
	public void putTokenAndUser(String authToken, String user){
		this.tokenToUser.put(authToken, user);
		this.defaultToken = authToken;
	}
	
	public void checkUserByToken(String token){
		String user = this.tokenToUser.get(token);
		if(user == null || user.equals("")){
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}
	
	public void authenticationCheck(String username, String password) {
		if(!this.usernameAdmin.equals(username) || !this.passwordAdmin.equals(password)){
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}
	
	public String getDefaultToken(){
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
			return mountJSONObject(token, user);
		}
		
		@Post
		public Representation post(Representation entity) {
			KeystoneApplication keyStoneApplication = (KeystoneApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String jsonCredentials = "";
			try {
				 jsonCredentials = entity.getText();
			} catch (IOException e) {}	
			
			String username = getUserFeatureCredentials(jsonCredentials, "username");
			String password = getUserFeatureCredentials(jsonCredentials, "password");
			keyStoneApplication.authenticationCheck(username, password);
			
			return new StringRepresentation(mountJSONResponseAuthenticateToken(keyStoneApplication.getDefaultToken()), MediaType.TEXT_ALL);
		}
	
		private String mountJSONResponseAuthenticateToken(String token){
			try {
				JSONObject rootIdTOken = new JSONObject();
				rootIdTOken.put("id", token);
				JSONObject rootToken = new JSONObject();
				rootToken.put("token", rootIdTOken);
				JSONObject rootAccess = new JSONObject();
				rootAccess.put("access", rootToken);
				return rootAccess.toString();
			} catch (JSONException e) {
				e.printStackTrace();
			}			
			return null;
		}
	
		private String getUserFeatureCredentials(String jsonCredentials, String feature){
			try {
				JSONObject root = new JSONObject(jsonCredentials);
				return root.getJSONObject("auth").getJSONObject("passwordCredentials").getString(feature).toString();
			} catch (JSONException e) {
				return null;
			}
		}
		
		private String mountJSONObject(String token, String user) {
			try {
				JSONObject nameObject = new JSONObject();
				nameObject.put("name", user);
				JSONObject tokenObject = new JSONObject();		
				tokenObject.put("user", nameObject);
				JSONObject accessObject = new JSONObject();
				accessObject.put("token", tokenObject);				
				return accessObject.toString();
			} catch (JSONException e) {
			}
			return null;
		}
	}
}