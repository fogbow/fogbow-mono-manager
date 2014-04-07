package org.fogbowcloud.manager.occi.core;

import java.util.List;

public class Resource {

	private Category category;
	private List<String> attributes;
	private List<String> actions;
	private String location;
	private String title;
	private String rel;
	
	public Resource(Category category, List<String> supportedAtt,
			List<String> actions, String location, String title, String rel) {
		this.category = category;
		this.attributes= supportedAtt;
		this.actions = actions;
		this.title = title;
		this.rel = rel;
	}
	
	public Resource(String term, String scheme, String catClass, List<String> supportedAtt,
			List<String> actions, String location, String title, String rel) {
		this(new Category(term, scheme, catClass), supportedAtt, 
				actions, location, title, rel);
	}
	
	public Category getCategory() {
		return category;
	}
	
	public List<String> getAttributes() {
		return attributes;
	}

	public void setAttributes(List<String> attributes) {
		this.attributes = attributes;
	}

	public List<String> getActions() {
		return actions;
	}

	public void setActions(List<String> actions) {
		this.actions = actions;
	}
	
	public String getLocation() {
		return location;
	}

	public String getTitle() {
		return title;
	}

	public String getRel() {
		return rel;
	}

	public boolean matches(Category category) {
		return getCategory().equals(category);
	}

	public boolean supportAtt(String attributeName) {		
		return attributes.contains(attributeName);
	}
}
