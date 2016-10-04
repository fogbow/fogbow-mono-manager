package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;

public class PairwiseFairnessDrivenController extends FairnessDrivenCapacityController {	
	
	private double deltaC; 
	private double minimumThreshold;
	private double maximumThreshold;	
	
	private Map<FederationMember, HillClimbingAlgorithm> hillClimbingControllers;	
	
	public PairwiseFairnessDrivenController(Properties properties, AccountingPlugin accountingPlugin) {
		super(properties, accountingPlugin);
		this.hillClimbingControllers = new HashMap<FederationMember, HillClimbingAlgorithm>();	
		
		this.deltaC = Double.parseDouble(properties.getProperty(CONTROLLER_DELTA));
		this.minimumThreshold = Double.parseDouble(properties
				.getProperty(CONTROLLER_MINIMUM_THRESHOLD));
		this.maximumThreshold = Double.parseDouble(properties
				.getProperty(CONTROLLER_MAXIMUM_THRESHOLD));
	}

	@Override
	public double getMaxCapacityToSupply(FederationMember member) {
		return this.hillClimbingControllers.get(member).getMaximumCapacityToSupply();				
	}
	
	@Override
	public void updateCapacity(FederationMember member, double maximumCapacity) {
		maximumCapacity = normalizeMaximumCapacity(maximumCapacity);
		
		if (this.hillClimbingControllers.containsKey(member) && 
				this.hillClimbingControllers.get(member).getLastUpdated() == this.dateUtils.currentTimeMillis()) {
			throw new IllegalStateException("The controller of member (" + 
				properties.getProperty(ConfigurationConstants.XMPP_JID_KEY) + 
				") is running more than once at the same time step for member(" + member.getId() + ").");
		} else if (!this.hillClimbingControllers.containsKey(member)) {
			this.hillClimbingControllers.put(member, new HillClimbingAlgorithm(
					this.deltaC, this.minimumThreshold, this.maximumThreshold));
		}
		
		HillClimbingAlgorithm hillClimbingMember = this.hillClimbingControllers.get(member);
		hillClimbingMember.setLastUpdated(this.dateUtils.currentTimeMillis());
		updateFairness(member);	
		hillClimbingMember.updateCapacity(maximumCapacity);		
	}
	
	protected void updateFairness(FederationMember member){
		//update last fairness
		HillClimbingAlgorithm memberHillClimbing = this.hillClimbingControllers.get(member);
		memberHillClimbing.setLastFairness(memberHillClimbing.getCurrentFairness());
		double currentDonated = getCurrentDonated(member);
		double currentConsumed = getCurrentConsumed(member);
		this.hillClimbingControllers.get(member).setCurrentFairness(
				getFairness(currentConsumed, currentDonated));
	}
	
	private double getCurrentDonated(FederationMember member){
		double donated = 0;
		for(AccountingInfo accountingInfo : this.accountingPlugin.getAccountingInfo())
			if ( accountingInfo.getProvidingMember().equals(this.properties.getProperty(
					ConfigurationConstants.XMPP_JID_KEY)) 
					&& accountingInfo.getRequestingMember().equals(member.getId()))
				donated += accountingInfo.getUsage();
		return donated;
	}
	
	private double getCurrentConsumed(FederationMember member){
		double consumed = 0;
		for (AccountingInfo accountingInfo : this.accountingPlugin.getAccountingInfo())
			if (accountingInfo.getRequestingMember().equals(
					this.properties.getProperty(ConfigurationConstants.XMPP_JID_KEY)) 
					&& accountingInfo.getProvidingMember().equals(member.getId())) {				
				consumed += accountingInfo.getUsage();				
			}
		return consumed;
	}
	
	@Override
	public double getCurrentFairness(FederationMember member) {		
		return this.hillClimbingControllers.get(member) == null ? -1
				: this.hillClimbingControllers.get(member).getCurrentFairness();
	}
	
	@Override
	public double getLastFairness(FederationMember member) {
		return this.hillClimbingControllers.get(member) == null ? -1
				: this.hillClimbingControllers.get(member).getLastFairness();
	}
	
	protected Map<FederationMember, HillClimbingAlgorithm> getControllers() {
		return hillClimbingControllers;
	}
	
}
