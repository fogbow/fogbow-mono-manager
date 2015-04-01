package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;

public class FederationMemberDebt {

	private FederationMember member;
	private double debt;

	public FederationMemberDebt(String memberId, double debt) {
		this(new FederationMember(new ResourcesInfo(memberId, "", "", "", "", null)), debt);
	}

	public FederationMemberDebt(FederationMember member, double debt) {
		this.member = member;
		this.debt = debt;
	}

	public FederationMember getMember() {
		return member;
	}

	public double getDebt() {
		return debt;
	}
	
	public String toString() {
		return "member=" + member.getResourcesInfo().getId() + ", debt=" + debt;
	}
}