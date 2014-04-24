package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.jamppa.component.PacketSender;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;

public class ManagerPacketHelper {

	public static void iAmAlive(ResourcesInfo resourcesInfo,
			String rendezvousAddress, PacketSender packetSender) {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAddress);
		Element statusEl = iq.getElement()
				.addElement("query", ManagerXmppComponent.IAMALIVE_NAMESPACE)
				.addElement("status");
		statusEl.addElement("cpu-idle").setText(resourcesInfo.getCpuIdle());
		statusEl.addElement("cpu-inuse").setText(resourcesInfo.getCpuInUse());
		statusEl.addElement("mem-idle").setText(resourcesInfo.getMemIdle());
		statusEl.addElement("mem-inuse").setText(resourcesInfo.getMemInUse());
		List<Flavor> flavours = resourcesInfo.getFlavours();
		for (Flavor f : flavours) {
			Element flavorElement = statusEl.addElement("flavor");
			flavorElement.addElement("name").setText(f.getName());
			flavorElement.addElement("cpu").setText(f.getCpu());
			flavorElement.addElement("mem").setText(f.getMem());
			flavorElement.addElement("capacity").setText(
					f.getCapacity().toString());
		}
		statusEl.addElement("cert");
		packetSender.syncSendPacket(iq);
	}

	public static List<FederationMember> whoIsalive(String rendezvousAddress,
			PacketSender packetSender) {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAddress);
		iq.getElement().addElement("query",
				ManagerXmppComponent.WHOISALIVE_NAMESPACE);
		IQ response = (IQ) packetSender.syncSendPacket(iq);
		ArrayList<FederationMember> members = getMembersFromIQ(response);
		return members;
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<FederationMember> getMembersFromIQ(
			IQ responseFromWhoIsAliveIQ) {
		Element queryElement = responseFromWhoIsAliveIQ.getElement().element(
				"query");
		Iterator<Element> itemIterator = queryElement.elementIterator("item");
		ArrayList<FederationMember> aliveItems = new ArrayList<FederationMember>();

		while (itemIterator.hasNext()) {
			Element itemEl = (Element) itemIterator.next();
			Attribute id = itemEl.attribute("id");
			Element statusEl = itemEl.element("status");
			String cpuIdle = statusEl.element("cpu-idle").getText();
			String cpuInUse = statusEl.element("cpu-inuse").getText();
			String memIdle = statusEl.element("mem-idle").getText();
			String memInUse = statusEl.element("mem-inuse").getText();

			List<Flavor> flavoursList = new LinkedList<Flavor>();
			Iterator<Element> flavourIterator = statusEl
					.elementIterator("flavor");
			while (flavourIterator.hasNext()) {
				Element flavour = (Element) flavourIterator.next();
				String name = flavour.element("name").getText();
				String cpu = flavour.element("cpu").getText();
				String mem = flavour.element("mem").getText();
				int capacity = Integer.parseInt(flavour.element("capacity")
						.getText());
				Flavor flavor = new Flavor(name, cpu, mem, capacity);
				flavoursList.add(flavor);
			}

			ResourcesInfo resources = new ResourcesInfo(id.getValue(), cpuIdle,
					cpuInUse, memIdle, memInUse, flavoursList);
			FederationMember item = new FederationMember(resources);
			aliveItems.add(item);
		}
		return aliveItems;
	}

	public static String remoteRequest(Request request,
			String memberAddress, PacketSender packetSender) {
		IQ iq = new IQ();
		iq.setTo(memberAddress);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.REQUEST_NAMESPACE);
		for (Category category : request.getCategories()) {
			Element categoryEl = queryEl.addElement("category");
			categoryEl.addElement("class").setText(category.getCatClass());
			categoryEl.addElement("term").setText(category.getTerm());
			categoryEl.addElement("scheme").setText(category.getScheme());
		}
		for (Entry<String, String> xOCCIEntry : request.getxOCCIAtt()
				.entrySet()) {
			Element attributeEl = queryEl.addElement("attribute");
			attributeEl.addAttribute("var", xOCCIEntry.getKey());
			attributeEl.addElement("value").setText(xOCCIEntry.getValue());
		}
		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			return null;
		}
		return response.getElement().element("query")
				.element("instance")
				.elementText("id");
	}
	
	public static Instance getRemoteInstance(Request request,
			String memberAddress, PacketSender packetSender) {
		IQ iq = new IQ();
		iq.setTo(memberAddress);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(request.getInstanceId());
		
		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			return null;
		}
		
		return parseInstance(response.getElement().element("query")
				.element("instance"));
	}	
	
	private static Instance parseInstance(Element instanceEl) {
		return null;
	}
}
