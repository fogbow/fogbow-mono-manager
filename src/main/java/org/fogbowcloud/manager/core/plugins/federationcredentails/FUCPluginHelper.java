package org.fogbowcloud.manager.core.plugins.federationcredentails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class FUCPluginHelper {
	
	public static String FUC_PREFIX = "fuc_";
	public static String UNDERLINE = "_";
	public static String FOGBOW_DEFAULTS = "defaults";

	public static Map<String, Map<String, String>> getMemberCredentials(
			Properties properties, List<String> filters) {
		Map<String, Map<String, String>> memberCredentialsList = 
			new HashMap<String, Map<String,String>>();
		List<String> members = new ArrayList<String>();
		if (properties == null) {
			return memberCredentialsList;
		}
		for (Object key : properties.keySet()) {
			String keyStr = key.toString();
			if (keyStr.startsWith(FUC_PREFIX)) {
				String[] splitKeys = keyStr.replace(FUC_PREFIX, "").split("_");
				if (splitKeys.length <= 1) {
					continue;
				}
				String member = splitKeys[0].toString();
				if (members.contains(member)) {
					continue;
				}
				if (filters != null) {					
					if (!filters.contains(member)) {
						continue;
					}
				}
				members.add(member);
				memberCredentialsList.put(member, getCredentialsPerMember(properties, member));	
			}
		}
		return memberCredentialsList;
	}
	
	protected static Map<String, String> getCredentialsPerMember(Properties properties
			, String member) {
		Map<String, String> credentials = new HashMap<String, String>();
		for (Object key : properties.keySet()) {
			String prefix = FUC_PREFIX + member + "_";
			if (key.toString().startsWith(prefix)) {
				String crendentialKey = key.toString().replace(prefix, "");
				credentials.put(crendentialKey.toString(), properties.get(
						key.toString()).toString());
			}
		}
		return credentials;
	}		
}
