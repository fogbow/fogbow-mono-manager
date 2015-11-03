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
		Token token = new Token("accessId", "user", expirationTime, attributes);			
		
		Token tokenFromJSON = Token.fromJSON(token.toJSON().toString());
		Assert.assertTrue(token.equals(tokenFromJSON));
	}
	
	@Test
	public void testTokenToJSONullValues() throws JSONException {
		Token token = new Token(null, null, null, new HashMap<String, String>());			
		
		Token tokenFromJSON = Token.fromJSON(token.toJSON().toString());
		Assert.assertTrue(token.equals(tokenFromJSON));
	}	
}
