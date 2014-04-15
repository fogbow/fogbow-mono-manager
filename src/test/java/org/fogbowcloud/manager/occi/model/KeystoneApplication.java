package org.fogbowcloud.manager.occi.model;

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
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;

public class KeystoneApplication extends Application {

	private Map<String,String> tokenToUser;
	
	public static String TARGET = "/v3/auth/tokens/";
	
	public KeystoneApplication() {
		this.tokenToUser = new HashMap<String, String>();
	}
	
	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(TARGET, KeystoneServer.class);
		return router;
	}
	
	public String getUserFromToken(String token){
		return this.tokenToUser.get(token);
	}
	
	public void putTokenAndUser(String authToken, String user){
		this.tokenToUser.put(authToken, user);
	}
	
	public void checkUserByToken(String token){
		String user = this.tokenToUser.get(token);
		if(user == null || user.equals("")){
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
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