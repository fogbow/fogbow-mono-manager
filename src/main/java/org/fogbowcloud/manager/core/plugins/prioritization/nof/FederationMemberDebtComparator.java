package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import java.util.Comparator;

public class FederationMemberDebtComparator implements Comparator<FederationMemberDebt> {
	@Override
	public int compare(FederationMemberDebt firstReputableMember,
			FederationMemberDebt secondReputableMember) {
		
		return new Double(firstReputableMember.getDebt()).compareTo(new Double(
				secondReputableMember.getDebt()));
	}
}