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

	public double getConsumption() {
		return consumption;
	}

	public double getDonation() {
		return donation;
	}
	
	public String toString() {
		return "consumption:" + consumption + ", donation:" + donation;
	}
}
