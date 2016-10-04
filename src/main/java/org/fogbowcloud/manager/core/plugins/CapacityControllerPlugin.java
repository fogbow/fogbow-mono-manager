package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.core.model.FederationMember;

public interface CapacityControllerPlugin {
	
	// Used when is not possible get maximum capacity
	public static int MAXIMUM_CAPACITY_VALUE_ERROR = -1;
	
	public double getMaxCapacityToSupply(FederationMember member);
	
	public void updateCapacity(FederationMember member, double maximumCapacity);

}
