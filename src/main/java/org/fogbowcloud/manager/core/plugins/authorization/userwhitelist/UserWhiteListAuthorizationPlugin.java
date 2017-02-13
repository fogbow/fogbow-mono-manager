package org.fogbowcloud.manager.core.plugins.authorization.userwhitelist;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class UserWhiteListAuthorizationPlugin implements AuthorizationPlugin {

	private static final Logger LOGGER = Logger.getLogger(UserWhiteListAuthorizationPlugin.class);
	protected static final String AUTHORIZATION_USER_WHITELIST = "authorization_user_whitelist";
	
	private List<String> userWhiteList;
	
	public UserWhiteListAuthorizationPlugin(Properties properties) {
		String whiteListStr = properties.getProperty(AUTHORIZATION_USER_WHITELIST);
		if (whiteListStr == null) {
			String errorMsg = "Property " + AUTHORIZATION_USER_WHITELIST + " must be set.";
			LOGGER.error(errorMsg);
			throw new IllegalArgumentException(errorMsg);
		}
		this.userWhiteList = Arrays.asList(whiteListStr.split(","));
		LOGGER.debug("List of authorized users : " + this.userWhiteList.toString());
	}
	
	@Override
	public boolean isAuthorized(Token token) {
		if (token == null) {
			return false;
		}
		return this.userWhiteList.contains(token.getUser().getId());
	}

}
