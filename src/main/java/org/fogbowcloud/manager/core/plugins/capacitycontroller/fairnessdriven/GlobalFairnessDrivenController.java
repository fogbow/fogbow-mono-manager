package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;

//TODO review all class
public class GlobalFairnessDrivenController extends FairnessDrivenCapacityController{
	
	private HillClimbingAlgorithm hillClimbingController;
	
	public GlobalFairnessDrivenController(Properties properties, AccountingPlugin accountingPlugin) {
		super(properties, accountingPlugin);
		
		double deltaC, minimumThreshold, maximumThreshold;
		deltaC = Double.parseDouble(properties.getProperty(CONTROLLER_DELTA));
		minimumThreshold = Double.parseDouble(properties.getProperty(CONTROLLER_MINIMUM_THRESHOLD));
		maximumThreshold = Double.parseDouble(properties.getProperty(CONTROLLER_MAXIMUM_THRESHOLD));
		this.hillClimbingController = new HillClimbingAlgorithm(deltaC, minimumThreshold, maximumThreshold);
	}

	public double getMaxCapacityToSupply(FederationMember member) {
		return this.hillClimbingController.getMaximumCapacityToSupply();	
	}
	
	public void updateCapacity(FederationMember member, double maximumCapacity) {
		maximumCapacity = normalizeMaximumCapacity(maximumCapacity);
		updateFairness();
		this.hillClimbingController.updateCapacity(maximumCapacity);
	}
	
	protected void updateFairness() {
		this.hillClimbingController.setLastFairness(
				this.hillClimbingController.getCurrentFairness());
		double currentConsumed = 0;
		double currentDonated = 0;
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

}