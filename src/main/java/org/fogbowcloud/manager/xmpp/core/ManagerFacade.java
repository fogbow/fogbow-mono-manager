package org.fogbowcloud.manager.xmpp.core;

import java.util.ArrayList;
import java.util.Iterator;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.model.ManagerItem;
import org.fogbowcloud.manager.core.model.ManagerModel;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.xmpp.packet.IQ;

public class ManagerFacade {
	
	private ManagerModel managerModel;
	
	public ManagerFacade(ManagerModel managerModel) {
		if (managerModel == null) throw new IllegalArgumentException();
		this.managerModel = managerModel;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<ManagerItem> getItemsFromIQ(
			IQ responseFromWhoIsAliveIQ) {
		Element queryElement = responseFromWhoIsAliveIQ.getElement().element(
				"query");
		Iterator<Element> itemIterator = queryElement.elementIterator("item");
		ArrayList<ManagerItem> aliveItems = new ArrayList<ManagerItem>();

		while (itemIterator.hasNext()) {
			Element itemEl = (Element) itemIterator.next();
			Attribute id = itemEl.attribute("id");
			Element statusEl = itemEl.element("status");
			String cpuIdle = statusEl.element("cpu-idle").getText();
			String cpuInUse = statusEl.element("cpu-inuse").getText();
			String memIdle = statusEl.element("mem-idle").getText();
			String memInUse = statusEl.element("mem-inuse").getText();
			ResourcesInfo resources = new ResourcesInfo(id.getValue(), cpuIdle,
					cpuInUse, memIdle, memInUse);
			ManagerItem item = new ManagerItem(resources);
			aliveItems.add(item);
		}
		managerModel.update(aliveItems);
		return aliveItems;
	}

	public ManagerModel getManagerModel() {
		return managerModel;
	}
}
