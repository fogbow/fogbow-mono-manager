package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;

public class GlobalFairnessDrivenController extends FairnessDrivenCapacityController{
	
	private long lastUpdated;	
	private HillClimbingAlgorithm controller;
	
	public GlobalFairnessDrivenController(Properties properties, AccountingPlugin accountingPlugin) {
		super(properties, accountingPlugin);
		lastUpdated = -1;		
		
		double deltaC, minimumThreshold, maximumThreshold, maximumCapacityOfPeer;
		deltaC = Double.parseDouble(properties.getProperty(CONTROLLER_DELTA));
		minimumThreshold = Double.parseDouble(properties.getProperty(CONTROLLER_MINIMUM_THRESHOLD));
		maximumThreshold = Double.parseDouble(properties.getProperty(CONTROLLER_MAXIMUM_THRESHOLD));
		maximumCapacityOfPeer = Double.parseDouble(properties.getProperty(CONTROLLER_MAXIMUM_CAPACITY));
		controller = new HillClimbingAlgorithm(deltaC, minimumThreshold, maximumThreshold, maximumCapacityOfPeer);
	}

	@Override
	public double getMaxCapacityToSupply(FederationMember member) {
		return controller.getMaximumCapacityToSupply();	
	}
	
	@Override
	public void updateCapacity(FederationMember member) {
		if(lastUpdated!=dateUtils.currentTimeMillis()){
			//time is different, then we must compute the new maxCapacity
			lastUpdated = dateUtils.currentTimeMillis();
			updateFairness();
			controller.updateCapacity();
		}	
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

	@Override
	public void setMaxCapacityController(int maxCapacity) {
		// TODO Auto-generated method stub
		
	}

}
