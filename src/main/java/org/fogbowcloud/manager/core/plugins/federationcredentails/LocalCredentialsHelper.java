package org.fogbowcloud.manager.core.plugins.federationcredentails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class LocalCredentialsHelper {
	
	public static String LOCAL_CREDENTIAL_PREFIX = "local_credential_";
	public static String UNDERLINE = "_";
	public static String FOGBOW_DEFAULTS = "defaults";

	public static Map<String, Map<String, String>> getLocalCredentials(
			Properties properties, List<String> filterLocalNames) {
		Map<String, Map<String, String>> localCredentials = 
			new HashMap<String, Map<String,String>>();
		List<String> localNames = new ArrayList<String>();
		if (properties == null) {
			return localCredentials;
		}
		for (Object key : properties.keySet()) {
			String keyStr = key.toString();
			if (keyStr.startsWith(LOCAL_CREDENTIAL_PREFIX)) {
				String[] splitKeys = keyStr.replace(LOCAL_CREDENTIAL_PREFIX, "").split("_");
				if (splitKeys.length <= 1) {
					continue;
				}
				String relatedLocalName = splitKeys[0].toString();
				if (localNames.contains(relatedLocalName)) {
					continue;
				}
				if (filterLocalNames != null) {					
					if (!filterLocalNames.contains(relatedLocalName)) {
						continue;
					}
				}
				localNames.add(relatedLocalName);
				localCredentials.put(relatedLocalName, 
						getCredentialsPerRelatedLocalName(properties, relatedLocalName));	
			}
		}
		return localCredentials;
	}
	
	protected static Map<String, String> getCredentialsPerRelatedLocalName(
			Properties properties, String relatedLocalName) {
		Map<String, String> credentials = new HashMap<String, String>();
		for (Object key : properties.keySet()) {
			String prefix = LOCAL_CREDENTIAL_PREFIX + relatedLocalName + "_";
			if (key.toString().startsWith(prefix)) {
				String crendentialKey = key.toString().replace(prefix, "");
				credentials.put(crendentialKey.toString(), properties.get(
						key.toString()).toString());
			}
		}
		return credentials;
	}		
}
