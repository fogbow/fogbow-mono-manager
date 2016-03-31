package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.core.model.FederationMember;

public interface CapacityControllerPlugin {
	
	public double getMaxCapacityToSupply(FederationMember member);

}
