package org.fogbowcloud.manager.core.plugins.util;

public interface CredentialsInterface {
	
	public final String REQUIRED_FEATURE = "Required";
	public final String OPTIONAL_FEATURE = "Optional";
	
	public String getName();
	
	public String getValueDefault();
	
	public String getFeature();
	
}
