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
		String title = "";
		String rel = "";
		String location = "";
		String actions = "";
		String attributes = "";
		if (!getTitle().equals("")) {
			title = "\"; title=\"" + getTitle();
		}
		if (!getRel().equals("")) {
			rel = "\"; rel=\"" + getRel();
		}
		if (!getLocation().equals("")) {
			location = "\"; location=\"" + getLocation();
		}
		if(getActions().size() != 0 ){
			actions = "\"; actions=\"" + actionsToHeader();
		}
		if(getAttributes().size() != 0){
			attributes = "\"; attributes=\"" + attributesToHeader();
		}

		return category.getTerm() + "; scheme=\"" + category.getScheme() + "\"; class=\""
				+ category.getCatClass() + title + rel + location 
				+ attributes + actions + "\"";
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
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Resource)){
			return false;
		}
		Resource otherRes = (Resource) obj;		
		return getCategory().equals(otherRes.getCategory())
				&& getTitle().equals(otherRes.getTitle()) && getRel().equals(otherRes.getRel())
				&& getActions().equals(otherRes.getActions())
				&& getAttributes().equals(otherRes.getAttributes())
				&& getLocation().equals(otherRes.getLocation());
	}
}
