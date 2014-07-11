package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Category {

	private String term;
	private String scheme;
	private String catClass;
	private List<String> avaiableProperties = new ArrayList<String>() {{
	    add("scheme");
	    add("class");
	    add("title");
	    add("location");
	    add("rel");
	}};

	public Category(String term, String scheme, String catClass) {
		setTerm(term);
		setScheme(scheme);
		setCatClass(catClass);
	}

	public Category(String stringCategory) {
		Map<String, String> properties = new HashMap<String, String>();
		String[] categoryTokens = stringCategory.split(";");
		if (categoryTokens.length < 3){
			throw new IllegalArgumentException();
		}
		//first token
		String[] nameValue = categoryTokens[0].split("=");
		if (nameValue.length > 1) {
			throw new IllegalArgumentException();
		}
		properties.put("term", nameValue[0].trim());
		for (int k = 1; k < categoryTokens.length; k++) {
			nameValue = categoryTokens[k].split("=");

			if (nameValue.length != 2 || !avaiableProperties.contains(nameValue[0].trim())) {
				throw new IllegalArgumentException();			
			} 
			properties.put(nameValue[0].trim().replace("\"", ""), nameValue[1].trim().replace("\"", ""));			 
		}
		setTerm(properties.get("term"));
		setScheme(properties.get("scheme"));
		setCatClass(properties.get("class"));
	}

	public String toHeader() {
		return getTerm() + "; scheme=\"" + getScheme() + "\"; class=\"" + getCatClass() + "\"";
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		if (term == null || term.equals("")) {
			throw new IllegalArgumentException();
		}
		this.term = term;
	}

	public String getScheme() {
		return scheme;
	}

	public void setScheme(String scheme) {
		if (scheme == null || scheme.equals("")) {
			throw new IllegalArgumentException();
		}
		this.scheme = scheme;
	}

	public String getCatClass() {
		return catClass;
	}

	public void setCatClass(String catClass) {
		if (catClass == null || catClass.equals("")) {
			throw new IllegalArgumentException();
		}
		this.catClass = catClass;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Category)) {
			return false;
		}
		Category category = (Category) obj;
		if (category.getTerm().equals(getTerm())
				&& category.getCatClass().equals(getCatClass())
				&& category.getScheme().equals(getScheme())) {
			return true;
		}
		return false;
	}
}
