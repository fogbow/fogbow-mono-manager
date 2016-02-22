package org.fogbowcloud.manager.occi.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.JSONHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;


public class TestOrder {

	@Test
	public void testAddCategoryTwice() {
		Order order = new Order("id", null, new LinkedList<Category>(),
				new HashMap<String, String>(), true, null);
		order.addCategory(new Category(OrderConstants.USER_DATA_TERM,
				OrderConstants.SCHEME, OrderConstants.MIXIN_CLASS));
		Assert.assertEquals(1, order.getCategories().size());
		order.addCategory(new Category(OrderConstants.USER_DATA_TERM,
				OrderConstants.SCHEME, OrderConstants.MIXIN_CLASS));
		Assert.assertEquals(1, order.getCategories().size());
	}
	
	@Test
	public void testFromCategoriesJSON() throws JSONException {
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category("term", "scheme", "catClass"));
		categories.add(new Category("termTwo", "schemeTwo", "catClassTwo"));
		
		JSONArray categoriesJSON = JSONHelper.mountCategoriesJSON(categories);
		List<Category> categoriesFromJSON = JSONHelper.getCategoriesFromJSON(categoriesJSON.toString());
		
		Assert.assertEquals(categories, categoriesFromJSON);
	}
	
	@Test
	public void testFromCategoriesJSONNullValue() throws JSONException {
		
		JSONArray categoriesJSON = JSONHelper.mountCategoriesJSON(null);
		List<Category> categoriesFromJSON = JSONHelper.getCategoriesFromJSON(categoriesJSON.toString());
		
		Assert.assertTrue(categoriesFromJSON.isEmpty());
	}	
	
	@Test
	public void testGetXOCCIAttrToJSON() throws JSONException {
		Map<String, String> xOCCIAttributes = new HashMap<String, String>();
		xOCCIAttributes.put("keyOne", "valueOne");
		xOCCIAttributes.put("keyTwo", "valueTwo");
		
		JSONObject xocciAttrToJSON = JSONHelper.mountXOCCIAttrJSON(xOCCIAttributes);
		Map<String, String> fromXOCCIAttrJSON = JSONHelper.getXOCCIAttrFromJSON(xocciAttrToJSON.toString());
		
		Assert.assertEquals(xOCCIAttributes, fromXOCCIAttrJSON);
	}
	
	@Test
	public void testGetXOCCIAttrToJSONNUllValue() throws JSONException {
		JSONObject xocciAttrToJSON = JSONHelper.mountXOCCIAttrJSON(null);
		Map<String, String> fromXOCCIAttrJSON = JSONHelper.getXOCCIAttrFromJSON(xocciAttrToJSON.toString());
		
		Assert.assertTrue(fromXOCCIAttrJSON.isEmpty());
	}
}
