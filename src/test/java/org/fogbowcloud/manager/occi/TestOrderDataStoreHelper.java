package org.fogbowcloud.manager.occi;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class TestOrderDataStoreHelper {
	
	@Test
	public void testToMap() throws JSONException {		
		Map<String, String> map = new HashMap<String, String>();
		String keyOne = "a.a.";
		String keyTwo = "key";
		String keyThree = "a";
		String keyFour = "3121...";
		
		map.put(keyOne, "");
		map.put(keyTwo, "213");
		map.put(keyThree, "21355");
		map.put(keyFour, new String(Base64.encodeBase64("2134543fvs.g,6.7.67,4.7,.8==´´´".getBytes())));
		
		JSONObject jsonObject = new JSONObject().put("attribute", map.toString());
		Map<String, String> jsontoMap = OrderDataStoreHelper.toMap(jsonObject.optString("attribute"));
		
		Assert.assertEquals(map, jsontoMap);
	}	
	
}
