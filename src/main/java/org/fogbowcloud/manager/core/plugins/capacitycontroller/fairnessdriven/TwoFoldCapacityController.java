package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.Main;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.prioritization.TwoFoldPrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.prioritization.fcfs.FCFSPrioritizationPlugin;

public class TwoFoldCapacityController implements CapacityControllerPlugin{

	protected static final String LONG_KNOWN_PEER_CAPACITY_CONTROLLER_PLUGIN_CLASS = "long_known_peer_capacity_controller_plugin_class";
	protected static final String NEWCOMERS_CAPACITY_CONTROLLER_PLUGIN_CLASS = "newcomers_capacity_controller_plugin_class";
	
	private static final Logger LOGGER = Logger.getLogger(TwoFoldCapacityController.class);
	
	private FairnessDrivenCapacityController longKnownPeerCapacityController;
	private CapacityControllerPlugin newcomerCapacityController;
		
	public TwoFoldCapacityController(Properties properties, AccountingPlugin accountingPlugin) {
		try {
			longKnownPeerCapacityController = (FairnessDrivenCapacityController) Main.createInstanceWithAccountingPlugin(
					LONG_KNOWN_PEER_CAPACITY_CONTROLLER_PLUGIN_CLASS, properties, accountingPlugin);
		} catch (Exception e) {
			LOGGER.warn("A valid long known peer (fairness driven) capacity controller plugin was not specified in properties. "
					+ "Using the default one.",	e);
			longKnownPeerCapacityController = new PairwiseFairnessDrivenController(properties, accountingPlugin);
		}
		
		try {
			newcomerCapacityController = (CapacityControllerPlugin) Main.createInstanceWithAccountingPlugin(
					NEWCOMERS_CAPACITY_CONTROLLER_PLUGIN_CLASS, properties, accountingPlugin);
		} catch (Exception e) {
			LOGGER.warn("A valid newcomer peer capacity controller plugin was not specified in properties. "
					+ "Using the default one.",	e);
			newcomerCapacityController = new GlobalFairnessDrivenController(properties, accountingPlugin);
		}		
	}	
	
	@Override
	public double getMaxCapacityToSupply(FederationMember member) {
		double longKnownPeerLimit = 0, newcomerLimit = 0;
		longKnownPeerLimit = longKnownPeerCapacityController.getMaxCapacityToSupply(member);
		newcomerLimit = newcomerCapacityController.getMaxCapacityToSupply(member);
		if(longKnownPeerCapacityController.getCurrentFairness(member)>=0)
			return longKnownPeerLimit;
		else
			return newcomerLimit;
	}
	
	public void setDateUtils(DateUtils dateUtils){
		longKnownPeerCapacityController.setDateUtils(dateUtils);
		((GlobalFairnessDrivenController)newcomerCapacityController).setDateUtils(dateUtils);
	}
}
