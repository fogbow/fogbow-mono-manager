package org.fogbowcloud.manager.core.plugins.accounting;

public class ResourceUsage {
	
	double consumption = 0;
	double donation = 0;
		
	public void addDonation(double donation) {
		this.donation += donation;
	}
	
	public void addConsumption(double consumption) {
		this.consumption += consumption;
	}
}
