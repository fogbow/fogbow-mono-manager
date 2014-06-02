package org.fogbowcloud.manager.core;

import org.fogbowcloud.manager.core.model.FederationMember;

public interface FederationMemberPicker {

	public FederationMember pick(ManagerController facade);
	
}
