package org.fogbowcloud.manager.xmpp.core;

import java.util.LinkedList;
import java.util.List;

public class ManagerModel {
	
	private List<ManagerItem> managers = new LinkedList<ManagerItem>();
	
	public void update(List<ManagerItem> managers) {
		if (managers == null) {
			throw new IllegalArgumentException();
		}
		this.managers = managers;
	}

	public List<ManagerItem> getMembers() {
		return managers;
	}
}
