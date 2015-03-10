package org.fogbowcloud.manager.core.plugins.accounting;

public class ResourceUsage {
	
	String memberId;
	double consumed = 0;
	double donated = 0;
	
	public ResourceUsage(String memberId) {
		this.memberId = memberId;
	}
				
	public void addDonation(double donation) {
		this.donated += donation;
	}
	
	public void addConsumption(double consumption) {
		this.consumed += consumption;
	}

	public double getConsumed() {
		return consumed;
	}

	public double getDonated() {
		return donated;
	}
		
	public String getMemberId() {
		return memberId;
	}

	public String toString() {
		return "consumed:" + consumed + ", donated:" + donated;
	}
}
