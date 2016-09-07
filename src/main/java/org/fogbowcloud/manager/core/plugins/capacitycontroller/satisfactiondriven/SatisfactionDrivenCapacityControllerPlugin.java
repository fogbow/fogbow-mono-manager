package org.fogbowcloud.manager.core.plugins.capacitycontroller.satisfactiondriven;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;

public class SatisfactionDrivenCapacityControllerPlugin implements CapacityControllerPlugin{
	
	@Override
	public double getMaxCapacityToSupply(FederationMember member) {
		return Double.MAX_VALUE;
	}
	
	@Override
	public void updateCapacity(FederationMember member) {}

	@Override
	public void setMaxCapacityController(int maxCapacity) {
		// TODO Auto-generated method stub
		
	}

}
