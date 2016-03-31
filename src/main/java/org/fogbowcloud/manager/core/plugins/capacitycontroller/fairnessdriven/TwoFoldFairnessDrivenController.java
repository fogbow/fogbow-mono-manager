package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;

public class TwoFoldFairnessDrivenController implements CapacityControllerPlugin{

	private FairnessDrivenCapacityController pairwiseController;
	private FairnessDrivenCapacityController globalController;
		
	public TwoFoldFairnessDrivenController(FairnessDrivenCapacityController pairwiseController, FairnessDrivenCapacityController globalController) {
		this.pairwiseController = pairwiseController;
		this.globalController = globalController;
	}
	
	@Override
	public double getMaxCapacityToSupply(FederationMember member) {
		double pairwiseLimit = 0, globalLimit = 0;
		pairwiseLimit = pairwiseController.getMaxCapacityToSupply(member);
		globalLimit = globalController.getMaxCapacityToSupply(member);
		if(pairwiseController.getCurrentFairness(member)>=0)
			return pairwiseLimit;
		else
			return globalLimit;
	}
	
	

}
