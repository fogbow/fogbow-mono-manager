package org.fogbowcloud.manager.xmpp.core;

import java.util.ArrayList;
import java.util.List;

public class ManagerModel {
	
	private List<ManagerItem> managers = new ArrayList<ManagerItem>();
	
	public void update(List<ManagerItem> managers) {
		if (managers == null) throw new IllegalArgumentException();
		this.managers = managers;
	}

	public List<ManagerItem> getMembers() {
		return managers;
	}

}
