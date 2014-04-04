package org.fogbowcloud.manager.occi.core;

import java.util.List;

import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;

public class FogbowResource {

	private String term;
	private String scheme;
	private String catClass;
	private List<String> attributes;
	private List<String> actions;
	private String location;
	private String title;
	private String rel;
	
	public FogbowResource(String term, String scheme, String catClass, List<String> supportedAtt,
			List<String> actions, String location, String title, String rel) {
		setTerm(term);
		setScheme(scheme);
		setCatClass(catClass);
		this.attributes= supportedAtt;
		this.actions = actions;
		this.title = title;
		this.rel = rel;
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
		if (category.getTerm().equals(getTerm())
				&& category.getCatClass().equals(getCatClass())
				&& category.getScheme().equals(getScheme())) {
			return true;
		}
		return false;
	}

	public boolean supportAtt(String attributeName) {		
		return attributes.contains(attributeName);
	}
}
