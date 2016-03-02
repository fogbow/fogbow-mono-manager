package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;

public class NoFHelper {

	public static Map<String, ResourceUsage> calculateMembersUsage(String localMemberId,
			List<AccountingInfo> accounting) {
		Map<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();

		List<String> consumedFromMembers = getMembersWhereConsumedFrom(localMemberId, accounting);
		// calculating provision
		for (String consumedFrom : consumedFromMembers) {
			if (!membersUsage.containsKey(consumedFrom)) {
				membersUsage.put(consumedFrom, new ResourceUsage(consumedFrom));
			}

			membersUsage.get(consumedFrom).addConsumption(
					calculateConsuptionFrom(localMemberId, consumedFrom, accounting));
		}

		List<String> donatedToMembers = getMembersWhereDonatedTo(localMemberId, accounting);
		// calculating donation
		for (String donatedTo : donatedToMembers) {
			if (!membersUsage.containsKey(donatedTo)) {
				membersUsage.put(donatedTo, new ResourceUsage(donatedTo));
			}

			membersUsage.get(donatedTo).addDonation(
					calculateDonationTo(localMemberId, donatedTo, accounting));
		}

		return membersUsage;
	}

	private static double calculateDonationTo(String localMemberId, String donatedTo,
			List<AccountingInfo> accounting) {
		double donation = 0;
		for (AccountingInfo accountingEntry : accounting) {
			if (localMemberId.equals(accountingEntry.getProvidingMember())
					&& donatedTo.equals(accountingEntry.getRequestingMember())) {
				donation += accountingEntry.getUsage();
			}
		}
		return donation;
	}

	private static double calculateConsuptionFrom(String localMemberId, String consumedFrom,
			List<AccountingInfo> accounting) {
		double consuption = 0;
		for (AccountingInfo accountingEntry : accounting) {
			if (localMemberId.equals(accountingEntry.getRequestingMember())
					&& consumedFrom.equals(accountingEntry.getProvidingMember())) {
				consuption += accountingEntry.getUsage();
			}
		}
		return consuption;
	}

	private static List<String> getMembersWhereDonatedTo(String localMemberId,
			List<AccountingInfo> accounting) {
		List<String> donatedToMembers = new ArrayList<String>();
		for (AccountingInfo accountingEntry : accounting) {
			if (localMemberId.equals(accountingEntry.getProvidingMember())
					&& !donatedToMembers.contains(accountingEntry.getRequestingMember())) {
				donatedToMembers.add(accountingEntry.getRequestingMember());
			}
		}
		return donatedToMembers;
	}

	private static List<String> getMembersWhereConsumedFrom(String localMemberId,
			List<AccountingInfo> accounting) {
		List<String> consumedFromMembers = new ArrayList<String>();
		for (AccountingInfo accountingEntry : accounting) {
			if (localMemberId.equals(accountingEntry.getRequestingMember())
					&& !consumedFromMembers.contains(accountingEntry.getProvidingMember())) {
				consumedFromMembers.add(accountingEntry.getProvidingMember());
			}
		}
		return consumedFromMembers;
	}

}
