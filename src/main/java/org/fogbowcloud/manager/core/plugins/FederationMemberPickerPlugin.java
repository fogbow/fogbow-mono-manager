package org.fogbowcloud.manager.core.plugins;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;

public interface FederationMemberPickerPlugin {

	public FederationMember pick(List<FederationMember> members);
	
}
