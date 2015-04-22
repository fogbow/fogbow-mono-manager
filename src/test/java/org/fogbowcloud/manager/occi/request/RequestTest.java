package org.fogbowcloud.manager.occi.request;

import java.util.HashMap;
import java.util.LinkedList;

import org.fogbowcloud.manager.occi.core.Category;
import org.junit.Assert;
import org.junit.Test;

public class RequestTest {

	@Test
	public void testAddCategoryTwice() {
		Request request = new Request("id", null, 
				null, new LinkedList<Category>(), new HashMap<String, String>(), true, null);
		request.addCategory(new Category(RequestConstants.USER_DATA_TERM,
				RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(1, request.getCategories().size());
		request.addCategory(new Category(RequestConstants.USER_DATA_TERM,
				RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(1, request.getCategories().size());
	}

}
