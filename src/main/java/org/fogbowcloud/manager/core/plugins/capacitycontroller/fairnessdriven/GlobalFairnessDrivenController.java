package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;

public class GlobalFairnessDrivenController extends FairnessDrivenCapacityController{
	
	private long lastUpdated;	
	private HillClimbingAlgorithm controller;
	
	public GlobalFairnessDrivenController(AccountingPlugin accountingPlugin, Properties properties, double deltaC,
			double minimumThreshold, double maximumThreshold, double maximumCapacityOfPeer, DateUtils dateUtils) {
		super(accountingPlugin, properties, dateUtils);
		lastUpdated = -1;		
		controller = new HillClimbingAlgorithm(deltaC, minimumThreshold, maximumThreshold, maximumCapacityOfPeer);
	}

	@Override
	public double getMaxCapacityToSupply(FederationMember member) {
		if(lastUpdated!=dateUtils.currentTimeMillis()){
			//time is different, then we must compute the new maxCapacity
			lastUpdated = dateUtils.currentTimeMillis();
			updateFairness();
			return controller.getMaxCapacityFromFairness();
		}	
		//time is equal, then the maxCapacity was already computed, here we just return
		return controller.getMaximumCapacityToSupply();	
	}
	
	protected void updateFairness(){
		controller.setLastFairness(controller.getCurrentFairness());
		double currentConsumed = 0, currentDonated = 0;
		List<AccountingInfo> accountingList = accountingPlugin.getAccountingInfo();
		for(AccountingInfo acc : accountingList){
			if(acc.getProvidingMember().equals(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY)))
				currentDonated += acc.getUsage();
			else
				currentConsumed += acc.getUsage();			
		}
		controller.setCurrentFairness(getFairness(currentConsumed, currentDonated));
	}

	@Override
	public double getCurrentFairness(FederationMember member) {
		return controller.getCurrentFairness();
	}

	@Override
	public double getLastFairness(FederationMember member) {
		return controller.getLastFairness();
	}

}
