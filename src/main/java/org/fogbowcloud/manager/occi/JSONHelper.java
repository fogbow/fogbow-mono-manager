package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONHelper {
		
	public static Map<String, String> toMap(String jsonStr) {
		Map<String, String> newMap = new HashMap<String, String>();
		jsonStr = jsonStr.replace("{", "").replace("}", "");
		String[] blocks = jsonStr.split(",");
		for (int i = 0; i < blocks.length; i++) {
			String block = blocks[i];
			int indexOfCarac = block.indexOf("=");
			if (indexOfCarac < 0) {
				continue;
			}
			String key = block.substring(0, indexOfCarac).trim();
			String value = block.substring(indexOfCarac + 1, block.length()).trim();
			newMap.put(key, value);
		}
		return newMap;
	}

	public static Map<String, String> getXOCCIAttrFromJSON(String jsonStr) throws JSONException {
		JSONObject jsonObject = new JSONObject(jsonStr);
		return toMap(jsonObject.optString("xocci_attributes"));
	}
	
	public static JSONObject mountXOCCIAttrJSON(Map<String, String> xOCCIAtt) throws JSONException {
		return new JSONObject().put("xocci_attributes", xOCCIAtt != null ? xOCCIAtt.toString()
				: null);
	}
	
	public static List<Category> getCategoriesFromJSON(String jsonArrayString) throws JSONException {
		List<Category> categories = new ArrayList<Category>();
		JSONArray jsonArray = new JSONArray(jsonArrayString);
		for (int i = 0; i < jsonArray.length(); i++) {
			categories.add(Category.fromJSON(jsonArray.getString(i)));
		}
		
		return categories;
	}
	
	public static JSONArray mountCategoriesJSON(List<Category> categories) throws JSONException {
		List<JSONObject> categoryObj = new ArrayList<JSONObject>();
		for (Category category : categories != null ? categories : new ArrayList<Category>()) {
			categoryObj.add(category.toJSON());
		}
		return new JSONArray(categoryObj);
	}

	public static JSONArray mountLinksJSON(List<Link> links) throws JSONException {
		List<JSONObject> linkObj = new ArrayList<JSONObject>();
		for (Link link : links != null ? links : new ArrayList<Link>()) {
			linkObj.add(link.toJSON());
		}
		return new JSONArray(linkObj);
	}
	
	public static List<Link> getLinksFromJSON(String jsonArrayString) throws JSONException {
		if (jsonArrayString == null) {
			return null;
		}
		List<Link> links = new ArrayList<Link>();
		JSONArray jsonArray = new JSONArray(jsonArrayString);
		for (int i = 0; i < jsonArray.length(); i++) {
			links.add(Link.fromJSON(jsonArray.getString(i)));
		}
		return links;
	}
}
