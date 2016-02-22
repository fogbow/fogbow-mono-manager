package org.fogbowcloud.manager.core.plugins.accounting;


public class AccountingInfo {
	
	private String user;
	private String requestingMember;
	private String providingMember;
	private double usage;
	
	public AccountingInfo(String user, String requestingMember, String providingMember) {
		this.user = user;
		this.requestingMember = requestingMember;
		this.providingMember = providingMember;
		this.usage = 0;
	}
	
	public void addConsuption(double consuption) {
		this.usage += consuption;
	}

	public String getUser() {
		return user;
	}

	public String getRequestingMember() {
		return requestingMember;
	}

	public String getProvidingMember() {
		return providingMember;
	}

	public double getUsage() {
		return usage;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AccountingInfo) {
			AccountingInfo other = (AccountingInfo) obj;
			return getUser().equals(other.getUser())
					&& getRequestingMember().equals(other.getRequestingMember())
					&& getProvidingMember().equals(other.getProvidingMember())
					&& (getUsage() - other.getUsage() <= 0.00000001); 
		}
		return false;
	}
}