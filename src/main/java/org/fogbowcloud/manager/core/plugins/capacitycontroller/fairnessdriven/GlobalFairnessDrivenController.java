package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;

public class GlobalFairnessDrivenController extends FairnessDrivenCapacityController{
	
	private long lastUpdated;	
	private HillClimbingAlgorithm hillClimbingController;
	private double maxCapacity;
	
	public GlobalFairnessDrivenController(Properties properties, AccountingPlugin accountingPlugin) {
		super(properties, accountingPlugin);
		this.lastUpdated = -1;		
		
		double deltaC, minimumThreshold, maximumThreshold;
		deltaC = Double.parseDouble(properties.getProperty(CONTROLLER_DELTA));
		minimumThreshold = Double.parseDouble(properties.getProperty(CONTROLLER_MINIMUM_THRESHOLD));
		maximumThreshold = Double.parseDouble(properties.getProperty(CONTROLLER_MAXIMUM_THRESHOLD));
		this.hillClimbingController = new HillClimbingAlgorithm(deltaC, minimumThreshold, maximumThreshold);
	}

	public double getMaxCapacityToSupply(FederationMember member) {
		return this.hillClimbingController.getMaximumCapacityToSupply();	
	}
	
	public void updateCapacity(FederationMember member) {
		if (this.lastUpdated != this.dateUtils.currentTimeMillis()) {
			//time is different, then we must compute the new maxCapacity
			this.lastUpdated = this.dateUtils.currentTimeMillis();
			updateFairness();
			this.hillClimbingController.updateCapacity(this.maxCapacity);
		}	
	}
	
	protected void updateFairness() {
		this.hillClimbingController.setLastFairness(
				this.hillClimbingController.getCurrentFairness());
		double currentConsumed = 0, currentDonated = 0;
		List<AccountingInfo> accountingList = accountingPlugin.getAccountingInfo();
		for(AccountingInfo accountingInfo : accountingList){
			if (accountingInfo.getProvidingMember().equals(properties
					.getProperty(ConfigurationConstants.XMPP_JID_KEY))) {						
				currentDonated += accountingInfo.getUsage();
			} else {
				currentConsumed += accountingInfo.getUsage();
			}
		}
		this.hillClimbingController.setCurrentFairness(
				getFairness(currentConsumed, currentDonated));
	}

	@Override
	public double getCurrentFairness(FederationMember member) {
		return hillClimbingController.getCurrentFairness();
	}

	@Override
	public double getLastFairness(FederationMember member) {
		return hillClimbingController.getLastFairness();
	}

	@Override
	public void setMaximumCapacity(double maxCapacity) {
		this.maxCapacity = maxCapacity;
	}

}