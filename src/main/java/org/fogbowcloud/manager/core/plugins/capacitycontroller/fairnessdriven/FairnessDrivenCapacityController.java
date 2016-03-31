package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.Properties;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;

public abstract class FairnessDrivenCapacityController implements CapacityControllerPlugin{

	protected AccountingPlugin accountingPlugin;
	protected Properties properties;
	protected DateUtils dateUtils; 
	
	public FairnessDrivenCapacityController(AccountingPlugin accountingPlugin, Properties properties, DateUtils dateUtils) {
		this.accountingPlugin = accountingPlugin;
		this.properties = properties;
		this.dateUtils = dateUtils;
	}
	
	public abstract double getCurrentFairness(FederationMember member);
	public abstract double getLastFairness(FederationMember member);
	
	public double getFairness(double consumed, double donated){
		if(donated < 0 || consumed < 0)
			throw new IllegalArgumentException("Donated and consumed must be >=0. It should never be <0.\n"+
					"Donated("+donated+") and Consumed("+consumed+")");
		else if(donated == 0)
			return -1;		
		else
			return consumed/donated;
	}	
}
