package org.fogbowcloud.manager.core.plugins.accounting;


public class AccountingInfo {
	
	private String user;
	private String requestingMember;
	private String providingMember;
	private double consuption;
	
	public AccountingInfo(String user, String requestingMember, String providingMember) {
		this.user = user;
		this.requestingMember = requestingMember;
		this.providingMember = providingMember;
		this.consuption = 0;
	}
	
	public void addConsuption(double consuption) {
		this.consuption += consuption;
	}
}