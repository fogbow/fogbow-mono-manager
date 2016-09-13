package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.Properties;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;

public class TwoFoldCapacityController implements CapacityControllerPlugin{

	private FairnessDrivenCapacityController pairwiseController;
	private FairnessDrivenCapacityController globalController;
	private double maxCapacity;
		
	public TwoFoldCapacityController(Properties properties, AccountingPlugin accountingPlugin){
		this.pairwiseController = new PairwiseFairnessDrivenController(properties, accountingPlugin);
		this.globalController = new GlobalFairnessDrivenController(properties, accountingPlugin);
	}
	
	@Override
	public double getMaxCapacityToSupply(FederationMember member) {
		if (this.pairwiseController.getCurrentFairness(member) >= 0) {
			return this.pairwiseController.getMaxCapacityToSupply(member);
		} else {
			return this.globalController.getMaxCapacityToSupply(member);
		}
	}
	
	@Override
	public void updateCapacity(FederationMember member) {
		this.pairwiseController.setMaximumCapacity(this.maxCapacity);
		this.pairwiseController.updateCapacity(member);
		
		this.globalController.setMaximumCapacity(this.maxCapacity);
		this.globalController.updateCapacity(member);		
	}
	
	public void setDateUtils(DateUtils dateUtils){
		this.pairwiseController.setDateUtils(dateUtils);
		this.globalController.setDateUtils(dateUtils);
	}

	@Override
	public void setMaximumCapacity(double maxCapacity) {
		this.maxCapacity = maxCapacity;
	}
}
