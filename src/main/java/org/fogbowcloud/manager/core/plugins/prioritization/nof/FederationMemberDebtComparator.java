package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import java.util.Comparator;

public class FederationMemberDebtComparator implements Comparator<FederationMemberDebt> {
	@Override
	public int compare(FederationMemberDebt firstMemberDebt,
			FederationMemberDebt secondMemberDebt) {
		
		return new Double(firstMemberDebt.getDebt()).compareTo(new Double(
				secondMemberDebt.getDebt()));
	}
}