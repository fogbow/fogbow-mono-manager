package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.Properties;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;

public class TwoFoldCapacityController implements CapacityControllerPlugin{

	private FairnessDrivenCapacityController pairwiseController;
	private FairnessDrivenCapacityController globalController;
		
	public TwoFoldCapacityController(Properties properties, AccountingPlugin accountingPlugin){
		this.pairwiseController = new PairwiseFairnessDrivenController(properties, accountingPlugin);
		this.globalController = new GlobalFairnessDrivenController(properties, accountingPlugin);
	}
	
	@Override
	public double getMaxCapacityToSupply(FederationMember member) {
		if(pairwiseController.getCurrentFairness(member)>=0)
			return pairwiseController.getMaxCapacityToSupply(member);
		else
			return globalController.getMaxCapacityToSupply(member);
	}
	
	@Override
	public void updateCapacity(FederationMember member) {
		pairwiseController.updateCapacity(member);
		globalController.updateCapacity(member);		
	}
	
	public void setDateUtils(DateUtils dateUtils){
		pairwiseController.setDateUtils(dateUtils);
		((GlobalFairnessDrivenController)globalController).setDateUtils(dateUtils);
	}
}
