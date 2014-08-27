package org.fogbowcloud.manager.core.plugins.util;

public class Credential {

	private String name;
	private boolean required;
	private String valueDefault;
	
	public Credential(String name, boolean required, String valueDefault) {
		this.name = name;
		this.required = required;
		this.valueDefault = valueDefault;
	}
	
	public String getName() {
		return name;
	}
	
	public String getValueDefault() {
		return valueDefault;
	}
	
	public boolean isRequired() {
		return required;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Credential) {
			Credential otherCredential = (Credential) obj;			 
			return getName().equals(otherCredential.getName())
					&& (isRequired() == otherCredential.isRequired())
					&& ((getValueDefault() != null && otherCredential.getValueDefault() != null && getValueDefault()
							.equals(otherCredential.getValueDefault())) || (getValueDefault() == null && otherCredential
							.getValueDefault() == null)); 
		}
		return false;
	}
}
