package org.fogbowcloud.manager.occi.core;

import java.util.List;

public class Resource {

	private Category category;
	private List<String> attributes;
	private List<String> actions;
	private String location;
	private String title;
	private String rel;

	public Resource(Category category, List<String> supportedAtt, List<String> actions,
			String location, String title, String rel) {
		setAttributes(supportedAtt);
		setActions(actions);
		this.category = category;
		this.location = location;
		this.title = title;
		this.rel = rel;
	}

	public Resource(String term, String scheme, String catClass, List<String> supportedAtt,
			List<String> actions, String location, String title, String rel) {
		this(new Category(term, scheme, catClass), supportedAtt, actions, location, title, rel);
	}

	public String toHeader() {
		return category.getTerm() + "; scheme=\"" + category.getScheme() + "\"; class=\""
				+ category.getCatClass() + "\" attributes=\"" + attributesToHeader() + "\" actions=\""
				+ actionsToHeader() + "\" location=\"" + getLocation() + "\" title=\"" + getTitle()
				+ "\" rel=\"" + getRel() + "\"";
	}
	
	private String actionsToHeader() {
		String actionsString = "";
		for (String action : getActions()) {
			actionsString += action + " ";
		}
		return actionsString.trim();		
	}
	
	private String attributesToHeader() {
		String attributesString = "";
		for (String attribute : getAttributes()) {
			attributesString += attribute + " ";
		}
		return attributesString.trim();		
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
