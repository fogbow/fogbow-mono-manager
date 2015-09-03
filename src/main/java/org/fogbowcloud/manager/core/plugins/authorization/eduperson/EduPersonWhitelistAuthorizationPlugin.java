package org.fogbowcloud.manager.core.plugins.authorization.eduperson;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class EduPersonWhitelistAuthorizationPlugin implements AuthorizationPlugin {

	private static final Logger LOGGER = Logger.getLogger(EduPersonWhitelistAuthorizationPlugin.class);
	private List<String> institutionWhiteList;
	
	public EduPersonWhitelistAuthorizationPlugin(Properties properties) {
		String whiteListStr = properties.getProperty("authorization_eduperson_whitelist");
		if (whiteListStr == null) {
			LOGGER.error("Property authorization_eduperson_whitelist must be set.");
			throw new IllegalArgumentException(
					"Property authorization_eduperson_whitelist must be set.");
		}
		this.institutionWhiteList = Arrays.asList(whiteListStr.split(","));
	}
	
	@Override
	public boolean isAuthorized(Token token) {
		if (token == null) {
			return false;
		}
		String eduPersonPrincipalName = token.get("eduPersonPrincipalName");
		if (eduPersonPrincipalName == null) {
			return false;
		}
		String[] eduPersonPrincipalNameSplit = eduPersonPrincipalName.split("@");
		if (eduPersonPrincipalNameSplit.length != 2) {
			return false;
		}
		String institutionIdP = eduPersonPrincipalNameSplit[1];
		return institutionWhiteList.contains(institutionIdP);
	}

}
