package org.fogbowcloud.manager.occi.core;

import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.fogbowcloud.manager.occi.model.TestRequestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestFogBowUtils {

	Series<Header> headers;
	
	@Before
	public void setup() throws Exception {
		headers = new Series<Header>(Header.class);
		headers.add(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
	}

	@Test
	public void testValidSyntaxToken(){
		headers.add(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
	
		System.out.println(headers.getValues(HeaderConstants.X_AUTH_TOKEN));
		
		String token = FogbowUtils.getToken(headers);
		
		Assert.assertEquals(TestRequestHelper.ACCESS_TOKEN, token);
	}
	

}
