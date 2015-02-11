package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.HashMap;

import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.occi.core.Category;
import org.junit.Assert;
import org.junit.Test;

public class TestServedRequest {

	@Test
	public void testInitialization() {
		ServedRequest servedRequest = new ServedRequest("id", "member", new ArrayList<Category>(),
				new HashMap<String, String>());
		Assert.assertEquals("id", servedRequest.getInstanceToken());
		Assert.assertEquals("member", servedRequest.getMemberId());
		Assert.assertEquals(0, servedRequest.getCategories().size());
		Assert.assertEquals(0, servedRequest.getxOCCIAtt().size());
	}

}
