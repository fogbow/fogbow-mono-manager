package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;

public class PairwiseFairnessDrivenController extends FairnessDrivenCapacityController {

	private double deltaC, minimumThreshold, maximumThreshold, maximumCapacityOfPeer;
	
	private Map<FederationMember, HillClimbingAlgorithm> controllers;
	
	public PairwiseFairnessDrivenController(AccountingPlugin accountingPlugin, Properties properties, double deltaC,
			double minimumThreshold, double maximumThreshold, double maximumCapacityOfPeer) {
		super(accountingPlugin, properties, new DateUtils());
		controllers = new HashMap<FederationMember, HillClimbingAlgorithm>();	
		
		this.deltaC = deltaC;
		this.minimumThreshold = minimumThreshold;
		this.maximumThreshold = maximumThreshold;
		this.maximumCapacityOfPeer = maximumCapacityOfPeer;
	}

	@Override
	public double getMaxCapacityToSupply(FederationMember member) {		
		if(controllers.containsKey(member) && controllers.get(member).getLastUpdated() == dateUtils.currentTimeMillis())
			throw new IllegalStateException("The controller of member ("+properties.getProperty(ConfigurationConstants.XMPP_JID_KEY)+") is running more than once at the same time step for member("+member.getId()+").");
		else if(!controllers.containsKey(member))
			controllers.put(member, new HillClimbingAlgorithm(deltaC, minimumThreshold, maximumThreshold, maximumCapacityOfPeer));		
		
		controllers.get(member).setLastUpdated(dateUtils.currentTimeMillis());
		updateFairness(member);	
		return controllers.get(member).getMaxCapacityFromFairness();				
	}
	
	protected void updateFairness(FederationMember member){
		//update last fairness
		controllers.get(member).setLastFairness(controllers.get(member).getCurrentFairness());
		double currentDonated = getCurrentDonated(member);
		double currentConsumed = getCurrentConsumed(member);
		controllers.get(member).setCurrentFairness(getFairness(currentConsumed, currentDonated));
	}
	
	private double getCurrentDonated(FederationMember member){
		double donated = 0;
		for(AccountingInfo acc : accountingPlugin.getAccountingInfo())
			if(acc.getProvidingMember().equals(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY)) &&
					acc.getRequestingMember().equals(member.getId()))
				donated += acc.getUsage();
		return donated;
	}
	
	private double getCurrentConsumed(FederationMember member){
		double consumed = 0;
		for(AccountingInfo acc : accountingPlugin.getAccountingInfo())
			if(acc.getRequestingMember().equals(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY)) &&
					acc.getProvidingMember().equals(member.getId()))
				consumed += acc.getUsage();
		return consumed;
	}
	
	@Override
	public double getCurrentFairness(FederationMember member) {		
		return controllers.get(member)==null?-1:controllers.get(member).getCurrentFairness();
	}
	
	@Override
	public double getLastFairness(FederationMember member) {
		return controllers.get(member)==null?-1:controllers.get(member).getLastFairness();
	}
	
	protected Map<FederationMember, HillClimbingAlgorithm> getControllers() {
		return controllers;
	}
	
}
