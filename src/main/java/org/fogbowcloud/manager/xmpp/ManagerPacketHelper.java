package org.fogbowcloud.manager.xmpp;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.CertificateHandlerHelper;
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
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketError.Condition;

public class ManagerPacketHelper {

	private final static Logger LOGGER = Logger.getLogger(ManagerPacketHelper.class.getName());

	public static void iAmAlive(ResourcesInfo resourcesInfo, String rendezvousAddress,
			Properties properties, PacketSender packetSender) throws IOException {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAddress);
		Element statusEl = iq.getElement()
				.addElement("query", ManagerXmppComponent.IAMALIVE_NAMESPACE).addElement("status");

		try {
			FileInputStream input = new FileInputStream(properties.getProperty("cert_path"));
			properties.load(input);
			iq.getElement().element("query").addElement("cert")
					.setText(CertificateHandlerHelper.getBase64Certificate(properties));
		} catch (Exception e) {
			LOGGER.warn("Could not load certificate");
		}

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
			flavorElement.addElement("capacity").setText(f.getCapacity().toString());
		}
		packetSender.syncSendPacket(iq);
	}

	public static List<FederationMember> whoIsalive(String rendezvousAddress,
			PacketSender packetSender) throws CertificateException {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAddress);
		iq.getElement().addElement("query", ManagerXmppComponent.WHOISALIVE_NAMESPACE);
		IQ response = (IQ) packetSender.syncSendPacket(iq);
		ArrayList<FederationMember> members = getMembersFromIQ(response);
		return members;
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<FederationMember> getMembersFromIQ(IQ responseFromWhoIsAliveIQ) {
		Element queryElement = responseFromWhoIsAliveIQ.getElement().element("query");
		Iterator<Element> itemIterator = queryElement.elementIterator("item");
		ArrayList<FederationMember> aliveItems = new ArrayList<FederationMember>();

		while (itemIterator.hasNext()) {
			Element itemEl = (Element) itemIterator.next();
			Attribute id = itemEl.attribute("id");
			X509Certificate cert = null;

			try {
				cert = CertificateHandlerHelper.parseCertificate(itemEl.element("cert").getText());
			} catch (Exception e) {
				LOGGER.warn("Certificate could not be parsed.");
			}

			Element statusEl = itemEl.element("status");
			String cpuIdle = statusEl.element("cpu-idle").getText();
			String cpuInUse = statusEl.element("cpu-inuse").getText();
			String memIdle = statusEl.element("mem-idle").getText();
			String memInUse = statusEl.element("mem-inuse").getText();

			List<Flavor> flavoursList = new LinkedList<Flavor>();
			Iterator<Element> flavourIterator = statusEl.elementIterator("flavor");
			while (flavourIterator.hasNext()) {
				Element flavour = (Element) flavourIterator.next();
				String name = flavour.element("name").getText();
				String cpu = flavour.element("cpu").getText();
				String mem = flavour.element("mem").getText();
				int capacity = Integer.parseInt(flavour.element("capacity").getText());
				Flavor flavor = new Flavor(name, cpu, mem, capacity);
				flavoursList.add(flavor);
			}

			ResourcesInfo resources = new ResourcesInfo(id.getValue(), cpuIdle, cpuInUse, memIdle,
					memInUse, flavoursList, cert);
			FederationMember item = new FederationMember(resources);
			aliveItems.add(item);
		}
		return aliveItems;
	}

	public static String remoteRequest(Request request, String memberAddress,
			PacketSender packetSender) {
		IQ iq = new IQ();
		iq.setTo(memberAddress);
		iq.setType(Type.set);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.REQUEST_NAMESPACE);
		for (Category category : request.getCategories()) {
			Element categoryEl = queryEl.addElement("category");
			categoryEl.addElement("class").setText(category.getCatClass());
			categoryEl.addElement("term").setText(category.getTerm());
			categoryEl.addElement("scheme").setText(category.getScheme());
		}
		for (Entry<String, String> xOCCIEntry : request.getxOCCIAtt().entrySet()) {
			Element attributeEl = queryEl.addElement("attribute");
			attributeEl.addAttribute("var", xOCCIEntry.getKey());
			attributeEl.addElement("value").setText(xOCCIEntry.getValue());
		}
		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			if (response.getError().getCondition().equals(Condition.item_not_found)) {
				return null;
			}
			raiseException(response.getError());
		}
		return response.getElement().element("query").element("instance").elementText("id");
	}

	public static Instance getRemoteInstance(Request request, PacketSender packetSender) {
		IQ iq = new IQ();
		iq.setTo(request.getMemberId());
		iq.setType(Type.get);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		try {
			instanceEl.addElement("id").setText(request.getInstanceId());
		} catch (Exception e) {
			// TODO: handle exception
		}

		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			if (response.getError().getCondition().equals(Condition.item_not_found)) {
				return null;
			}
			raiseException(response.getError());
		}

		return parseInstance(response.getElement().element("query").element("instance"));
	}

	public static void deleteRemoteInstace(Request request, PacketSender packetSender) {
		IQ iq = new IQ();
		iq.setTo(request.getMemberId());
		iq.setType(Type.set);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.REMOVEINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(request.getInstanceId());

		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			raiseException(response.getError());
		}
	}

	private static void raiseException(PacketError error) {
		Condition condition = error.getCondition();
		if (condition.equals(Condition.item_not_found)) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
		if (condition.equals(Condition.not_authorized)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
	}

	@SuppressWarnings("unchecked")
	private static Instance parseInstance(Element instanceEl) {
		String id = instanceEl.element("id").getText();

		Iterator<Element> linkIterator = instanceEl.elementIterator("link");
		List<Link> links = new ArrayList<Link>();
		
		while (linkIterator.hasNext()) {
			Element linkEl = (Element) linkIterator.next();
			String linkName = linkEl.element("link").getText();
			Iterator<Element> linkAttributeIterator = linkEl.elementIterator("attribute");

			Map<String, String> attributesLink = new HashMap<String, String>();
			while (linkAttributeIterator.hasNext()) {
				Element itemAttributeEl = (Element) linkAttributeIterator.next();
				String key = itemAttributeEl.attributeValue("val");
				String value = itemAttributeEl.getText();
				attributesLink.put(key, value);
			}
			Link link = new Link(linkName, attributesLink);
			links.add(link);
		}
		
		Iterator<Element> resourceIterator = instanceEl.elementIterator("resource");
		List<Resource> resources = new ArrayList<Resource>();
		while (resourceIterator.hasNext()) {
			Element itemResourseEl = (Element) resourceIterator.next();
			String termCategory = itemResourseEl.element("category").element("term").getText();
			String schemeCategory = itemResourseEl.element("category").element("scheme").getText();
			String classCategory = itemResourseEl.element("category").element("class").getText();
			Category category = new Category(termCategory, schemeCategory, classCategory);

			Iterator<Element> resourceAttributeIterator = itemResourseEl.element("category")
					.elementIterator("attribute");
			List<String> resourceAttributes = new ArrayList<String>();
			while (resourceAttributeIterator.hasNext()) {
				Element itemResourseAttributeEl = (Element) resourceAttributeIterator.next();
				resourceAttributes.add(itemResourseAttributeEl.getText());
			}

			Iterator<Element> resourceActionsIterator = itemResourseEl.element("category")
					.elementIterator("action");
			List<String> resourceActions = new ArrayList<String>();
			while (resourceActionsIterator.hasNext()) {
				Element itemResourseActionEl = (Element) resourceActionsIterator.next();
				resourceActions.add(itemResourseActionEl.getText());
			}

			String location = itemResourseEl.element("location").getText();
			String title = itemResourseEl.element("title").getText();
			String rel = itemResourseEl.element("rel").getText();

			resources.add(new Resource(category, resourceAttributes, resourceActions, location,
					title, rel));
		}

		Iterator<Element> attributesIterator = instanceEl.elementIterator("attribute");

		Map<String, String> attributes = new HashMap<String, String>();
		while (attributesIterator.hasNext()) {
			Element attributeEl = (Element) attributesIterator.next();
			String key = attributeEl.attributeValue("val");
			String value = attributeEl.getText();
			attributes.put(key, value);
		}

		return new Instance(id, resources, attributes, links);
	}

	public static Condition getCondition(OCCIException e) {
		//TODO check if it is not needs other codes, e.g. quota exceeded
		switch (e.getStatus().getCode()) {
		case HttpStatus.SC_NOT_FOUND:
			return Condition.item_not_found;
		case HttpStatus.SC_UNAUTHORIZED:
			return Condition.not_authorized;
		case HttpStatus.SC_BAD_REQUEST:
			return Condition.bad_request;
		default:
			return Condition.internal_server_error;
		}
	}
}
