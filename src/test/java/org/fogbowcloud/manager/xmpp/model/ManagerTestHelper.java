package org.fogbowcloud.manager.xmpp.model;

import java.util.List;

import org.dom4j.Element;
import org.fogbowcloud.manager.xmpp.core.ResourcesInfo;
import org.xmpp.packet.IQ;

public class ManagerTestHelper {

	public static final String NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";

	public static ResourcesInfo getResources() {
		ResourcesInfo resources = new ResourcesInfo("abc", "value1", "value2",
				"value3", "value4");
		return resources;
	}

	public static IQ createResponse(List<RendezvousItemCopy> aliveIds) {
		IQ iq = new IQ();
		IQ resultIQ = IQ.createResultIQ(iq);

		Element queryElement = resultIQ.getElement().addElement("query",
				NAMESPACE);
		for (RendezvousItemCopy rendezvouItem : aliveIds) {
			Element itemEl = queryElement.addElement("item");
			itemEl.addAttribute("id", rendezvouItem.getResourcesInfo().getId());

			Element statusEl = itemEl.addElement("status");
			statusEl.addElement("cpu-idle").setText(
					rendezvouItem.getResourcesInfo().getCpuIdle());
			statusEl.addElement("cpu-inuse").setText(
					rendezvouItem.getResourcesInfo().getCpuInUse());
			statusEl.addElement("mem-idle").setText(
					rendezvouItem.getResourcesInfo().getMemIdle());
			statusEl.addElement("mem-inuse").setText(
					rendezvouItem.getResourcesInfo().getMemInUse());
			statusEl.addElement("updated").setText(
					String.valueOf(rendezvouItem.getFormattedTime()));
		}
		return resultIQ;
	}
}
