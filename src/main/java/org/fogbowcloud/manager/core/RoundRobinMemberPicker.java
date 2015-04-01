package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;

public class RoundRobinMemberPicker implements FederationMemberPicker {

	private String lastMember = null;

	public RoundRobinMemberPicker(Properties properties, AccountingPlugin accountingPlugin) {
	}

	@Override
	public FederationMember pick(List<FederationMember> members) {
		if (members == null) {
			return null;
		}
		ArrayList<FederationMember> membersListCopy = new ArrayList<FederationMember>(members);

		boolean containsInList = false;
		if (lastMember != null) {
			for (FederationMember federationMember : membersListCopy) {
				if (federationMember.getResourcesInfo().getId().equals(lastMember)) {
					containsInList = true;
					break;
				}
			}
			if (!containsInList) {
				membersListCopy.add(new FederationMember(new ResourcesInfo(lastMember, "", "", "",
						"", null)));
			}
		}

		Collections.sort(membersListCopy, new FederationMemberComparator());

		if (lastMember == null && !membersListCopy.isEmpty()) {
			FederationMember federationMember = membersListCopy.get(0);
			lastMember = federationMember.getResourcesInfo().getId();
			return federationMember;
		}
		
		for (int i = 0; i < membersListCopy.size(); i++) {
			FederationMember federationMember = membersListCopy.get(i);
			String memberName = federationMember.getResourcesInfo().getId();
			if (memberName.equals(lastMember)) {
				FederationMember nextMember = membersListCopy.get((i + 1) % membersListCopy.size());
				lastMember = nextMember.getResourcesInfo().getId();
				return nextMember;
			}
		}
		return null;
	}

	public static class FederationMemberComparator implements Comparator<FederationMember> {
		@Override
		public int compare(FederationMember federationMemberOne,
				FederationMember federationMemberTwo) {
			String memberNameOne = federationMemberOne.getResourcesInfo().getId();
			String memberNameTwo = federationMemberTwo.getResourcesInfo().getId();
			return memberNameOne.compareTo(memberNameTwo);
		}
	}
}
