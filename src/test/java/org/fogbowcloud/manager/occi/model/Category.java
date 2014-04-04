package org.fogbowcloud.manager.occi.model;

import java.util.List;

public class Category {

	private String term;
	private String scheme;
	private String catClass;
	private List<String> attributes;
	private List<String> actions;

	public Category(String term, String scheme, String catClass) {
		setTerm(term);
		setScheme(scheme);
		setCatClass(catClass);
	}

	public String getHeaderFormat() {
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

}
