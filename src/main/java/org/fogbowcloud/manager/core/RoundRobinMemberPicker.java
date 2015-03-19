package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;

public class RoundRobinMemberPicker implements FederationMemberPicker {

	private String lastMember = null;

	@Override
	public FederationMember pick(List<FederationMember> members) {
		if (members == null) {
			return null;
		}
		ArrayList<FederationMember> listMembers = new ArrayList<FederationMember>(members);

		boolean containsInList = false;
		if (lastMember != null) {
			for (FederationMember federationMember : listMembers) {
				if (federationMember.getResourcesInfo().getId().equals(lastMember)) {
					containsInList = true;
				}
			}
			if (!containsInList) {
				listMembers.add(new FederationMember(new ResourcesInfo(lastMember, "", "", "", "",
						null, null)));
			}
		}

		Collections.sort(listMembers, new FederationMemberComparator());

		for (int i = 0; i < listMembers.size(); i++) {
			FederationMember federationMember = listMembers.get(i);
			String memberName = federationMember.getResourcesInfo().getId();
			if (lastMember == null) {
				lastMember = memberName;
				return federationMember;
			}
			if (i == listMembers.size() - 1) {
				i = -1;
			}
			if (memberName.equals(lastMember)) {
				FederationMember nextMember = listMembers.get(i + 1);
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
