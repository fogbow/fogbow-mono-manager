package org.fogbowcloud.manager.occi.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;

public class TestToken {

	@Test
	public void testTokenFromJson() throws JSONException {
		Date expirationTime = new Date();
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("attrOne", "valueOne");
		attributes.put("attrTwo", "valueTwo");
		String userId = "userId";
		String userName = "userName";
		Token token = new Token("accessId", new Token.User(userId, userName), expirationTime, attributes);			
		
		Token tokenFromJSON = Token.fromJSON(token.toJSON().toString());
		Assert.assertTrue(token.equals(tokenFromJSON));
		Assert.assertEquals(tokenFromJSON.getUser().getId(), token.getUser().getId());
		Assert.assertEquals(userId, token.getUser().getId());
		Assert.assertEquals(tokenFromJSON.getUser().getName(), token.getUser().getName());
		Assert.assertEquals(userName, token.getUser().getName());
	}
	
	@Test
	public void testTokenToJSONullValues() throws JSONException {
		Token token = new Token(null, null, null, new HashMap<String, String>());			
		
		Token tokenFromJSON = Token.fromJSON(token.toJSON().toString());
		Assert.assertTrue(token.equals(tokenFromJSON));
	}	
}
