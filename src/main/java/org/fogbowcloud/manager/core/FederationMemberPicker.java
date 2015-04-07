package org.fogbowcloud.manager.core;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;

public interface FederationMemberPicker {

	public FederationMember pick(List<FederationMember> members);
	
}
