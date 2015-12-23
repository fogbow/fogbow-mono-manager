package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MapperHelper {
	
	public static String MAPPER_PREFIX = "mapper_";
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
			if (keyStr.startsWith(MAPPER_PREFIX)) {
				String[] splitKeys = keyStr.replace(MAPPER_PREFIX, "").split("_");
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
			String prefix = MAPPER_PREFIX + relatedLocalName + "_";
			if (key.toString().startsWith(prefix)) {
				String crendentialKey = key.toString().replace(prefix, "");
				credentials.put(crendentialKey.toString(), properties.get(
						key.toString()).toString());
			}
		}
		return credentials;
	}
	

	protected static String normalizeUser(String federationUser) {
		return federationUser.replaceAll("=", "->").replaceAll(" ", "");
	}
}
