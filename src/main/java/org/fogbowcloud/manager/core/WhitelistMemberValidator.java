package org.fogbowcloud.manager.core;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.core.Token;

public class WhitelistMemberValidator implements FederationMemberValidator {

	private static final String PROP_PREFIX = "member_authorization_whitelist_"; 
	public static final String PROP_WHITELIST_DONATE = PROP_PREFIX + "donate_to"; 
	public static final String PROP_WHITELIST_RECEIVE = PROP_PREFIX + "receive_from";
	
	private List<String> donateTo = new LinkedList<String>();
	private List<String> receiveFrom = new LinkedList<String>();
	
	public WhitelistMemberValidator(Properties properties) {
		this.donateTo = parseWhitelist(properties, PROP_WHITELIST_DONATE);
		this.receiveFrom = parseWhitelist(properties, PROP_WHITELIST_RECEIVE);
	}

	private List<String> parseWhitelist(Properties properties, String propName) {
		String whiteListStr = properties.getProperty(propName);
		List<String> authorizedJids = new LinkedList<String>();
		if (whiteListStr == null) {
			return authorizedJids;
		}
		for (String eachMember : whiteListStr.split(",")) {
			authorizedJids.add(eachMember.trim());
		}
		return authorizedJids;
	}
	
	@Override
	public boolean canDonateTo(FederationMember member,
			Token requestingUserToken) {
		return donateTo.contains(member.getResourcesInfo().getId());
	}

	@Override
	public boolean canReceiveFrom(FederationMember member) {
		return receiveFrom.contains(member.getResourcesInfo().getId());
	}

}
