package org.fogbowcloud.manager.occi.core;

public class Category {

	private String term;
	private String scheme;
	private String catClass;

	public Category(String term, String scheme, String catClass) {
		setTerm(term);
		setScheme(scheme);
		setCatClass(catClass);
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
