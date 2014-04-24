package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
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

	public static void getRemoteResponseDelete(Request request,
			String memberAddress, PacketSender packetSender) {
		IQ iq = new IQ();
		iq.setTo(memberAddress);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.REMOVEINSTACE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(request.getInstanceId());
		
		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
		}	
		
		String error = response.getElement().element("error").getText();
		if(error.equals("NOT_FOUND")){
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static Instance parseInstance(Element instanceEl) {
		String id = instanceEl.element("id").getText();

		Element linkEl = instanceEl.element("link");		
		String linkName = linkEl.elementText("name");
		Iterator<Element> linkAttributeIterator = linkEl.elementIterator("attribute");
		
		Map<String, String> attributesLink = new HashMap<String, String>();
		while (linkAttributeIterator.hasNext()) {
			Element itemAttributeEl = (Element) linkAttributeIterator.next();
			String key = itemAttributeEl.getStringValue();
			String value = itemAttributeEl.getText();
			attributesLink.put(key, value);
		}		
		Link link = new Link(linkName, attributesLink);
				
		Iterator<Element> resourceIterator = instanceEl.elementIterator("resource");
		List<Resource> resources = new ArrayList<Resource>();
		while (resourceIterator.hasNext()) {
			Element itemResourseEl = (Element) resourceIterator.next();
			String termCategory = itemResourseEl.element("category").element("term").getText();
			String schemeCategory = itemResourseEl.element("category").element("scheme").getText();
			String classCategory = itemResourseEl.element("category").element("class").getText();
			Category category = new Category(termCategory, schemeCategory, classCategory);

			Iterator<Element> resourceAttributeIterator = instanceEl.elementIterator("attribute");
			List<String> resourceAttributes = new ArrayList<String>();
			while (resourceAttributeIterator.hasNext()) {
				Element itemResourseAttributeEl = (Element) resourceAttributeIterator.next();				
				resourceAttributes.add(itemResourseAttributeEl.getText());
			}
			
			Iterator<Element> resourceActionsIterator = instanceEl.elementIterator("action");
			List<String> resourceActions = new ArrayList<String>();
			while (resourceActionsIterator.hasNext()) {
				Element itemResourseActionEl = (Element) resourceActionsIterator.next();				
				resourceActions.add(itemResourseActionEl.getText());
			}			
			
			String location = itemResourseEl.element("location").getText();
			String title = itemResourseEl.element("title").getText();
			String rel = itemResourseEl.element("rel").getText();
			
			resources.add(new Resource(category, resourceAttributes, resourceActions, location, title, rel));
		}				
		
		Iterator<Element> attributesIterator = instanceEl.elementIterator("attribute");
		
		Map<String, String> attributes = new HashMap<String, String>();
		while (attributesIterator.hasNext()) {
			Element attributeEl = (Element) attributesIterator.next();
			String key = attributeEl.getStringValue();
			String value = attributeEl.getText();
			attributes.put(key, value);
		}
		
		return new Instance(id, resources, attributes, link);
	}
}
