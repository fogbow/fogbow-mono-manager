package org.fogbowcloud.manager.occi.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

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
	
	public Resource(String resourceStr) {
		Map<String, String> resourceKeyAndValues = new HashMap<String, String>();
		String[] categoryTokens = resourceStr.split(";");
		if (categoryTokens.length < 3){
			throw new IllegalArgumentException();
		}
		//first token
		String[] nameValue = categoryTokens[0].split("=");
		if (nameValue.length > 1) {
			throw new IllegalArgumentException();
		}
		resourceKeyAndValues.put("term", nameValue[0].trim());
		for (int k = 1; k < categoryTokens.length; k++) {
			nameValue = categoryTokens[k].split("=");
			if (nameValue.length != 2) {
				throw new IllegalArgumentException();			
			}			
			resourceKeyAndValues.put(nameValue[0].trim().replace("\"", ""), nameValue[1].trim().replace("\"", ""));			 
		}
		
		//TODO refactor it
		category = new Category(resourceKeyAndValues.get("term"),
				resourceKeyAndValues.get("scheme"), resourceKeyAndValues.get("class"));
		
		if (resourceKeyAndValues.get("location") != null) {
			this.location = resourceKeyAndValues.get("location");
		} else {
			location = "";
		}
		
		if (resourceKeyAndValues.get("title") != null) {
			this.title = resourceKeyAndValues.get("title");
		} else {
			title = "";
		}
		
		if (resourceKeyAndValues.get("rel") != null) {
			this.rel = resourceKeyAndValues.get("rel");
		} else {
			rel = "";
		}
		
		List<String> attributes = new ArrayList<String>();
		if (resourceKeyAndValues.get("attributes") != null) {
			StringTokenizer st = new StringTokenizer(resourceKeyAndValues.get("attributes"));
			while (st.hasMoreTokens()){
				attributes.add(st.nextToken().trim());
			}
		}
		setAttributes(attributes);
		
		List<String> actions = new ArrayList<String>();
		if (resourceKeyAndValues.get("actions") != null) {
			StringTokenizer st = new StringTokenizer(resourceKeyAndValues.get("actions"));
			while (st.hasMoreTokens()){
				actions.add(st.nextToken().trim());
			}
		}
		setActions(actions);
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
	
	public String toString(){
		return toHeader();
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
	
	public boolean matches(Resource resource) {
		return getCategory().equals(resource.getCategory())
				&& getTitle().equals(resource.getTitle())
				&& getAttributes().equals(resource.getAttributes())
				&& getActions().equals(resource.getActions()) && getRel().equals(resource.getRel());
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
