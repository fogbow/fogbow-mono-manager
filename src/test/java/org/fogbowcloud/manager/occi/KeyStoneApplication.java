package org.fogbowcloud.manager.occi;

import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.plugins.TestIdentityOpenStack;
import org.json.JSONObject;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class KeyStoneApplication extends Application {

	private Map<String,String> tokenToUser;
	
	public static String TARGET = "/v3/auth/tokens/";
	
	public KeyStoneApplication() {
		this.tokenToUser = new HashMap<String, String>();
		normalizeUsers();
	}
	
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach(TARGET, KeyStoneServer.class);
		return router;
	}	
	
	public String getUserFromToken(String token){
		return this.tokenToUser.get(token);
	}
	
	public void checkUserByToken(String token){
		String user = this.tokenToUser.get(token);
		if(user == null || user.equals("")){
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}
	
	public void normalizeUsers() {
		try {
			JSONObject nameObject = new JSONObject();
			nameObject.put("name", TestIdentityOpenStack.USERNAME_AUTH);
			
			JSONObject tokenObject = new JSONObject();		
			tokenObject.put("user", nameObject);

			JSONObject accessObject = new JSONObject();
			accessObject.put("token", tokenObject);
			
			this.tokenToUser.put(TestIdentityOpenStack.MY_TOKEN, accessObject.toString());
		} catch (Exception e) {
		}		
	}
}
