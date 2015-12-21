package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.AsynchronousRequestCallback;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.jamppa.component.PacketCallback;
import org.jamppa.component.PacketSender;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketError.Condition;

public class ManagerPacketHelper {

	public static final String I_AM_ALIVE_PERIOD = "iamalive-period";
	private final static Logger LOGGER = Logger.getLogger(ManagerPacketHelper.class.getName());

	public static String iAmAlive(String rendezvousAddress, Properties properties,
			PacketSender packetSender) throws Exception {
		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}
		IQ iq = new IQ(Type.get);
		if (rendezvousAddress == null) {
			LOGGER.warn("Rendezvous not specified.");
			throw new IllegalArgumentException("Rendezvous address has not been specified.");
		}
		iq.setTo(rendezvousAddress);
		iq.getElement().addElement("query", ManagerXmppComponent.IAMALIVE_NAMESPACE);

		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response == null) {
			LOGGER.warn("Error while received the iamalive response");
			return null;
		}

		return response.getElement().element("query").element(I_AM_ALIVE_PERIOD).getText();
	}

	public static void wakeUpSleepingHost(int minCPU, int minRAM, String greenAddress, PacketSender packetSender) {
		IQ iq = new IQ(Type.set);
		iq.setTo(greenAddress);
		Element query = iq.getElement().addElement("query");
		query.addElement("minCPU").setText(Integer.toString(minCPU));
		query.addElement("minRAM").setText(Integer.toString(minRAM));
		packetSender.sendPacket(iq);
	}

	public static List<FederationMember> whoIsalive(String rendezvousAddress, PacketSender packetSender,
			int maxWhoIsAliveManagerCount, String after) throws Exception {
		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}
		IQ iq = new IQ(Type.get);
		if (rendezvousAddress == null) {
			LOGGER.warn("Rendezvous not especified.");
			throw new Exception();
		}
		iq.setTo(rendezvousAddress);
		Element queryEl = iq.getElement().addElement("query", ManagerXmppComponent.WHOISALIVE_NAMESPACE);
		Element setEl = queryEl.addElement("set");
		setEl.addElement("max").setText(Integer.toString(maxWhoIsAliveManagerCount));
		if (after != null) {
			setEl.addElement("after").setText(after);
		}
		IQ response = (IQ) packetSender.syncSendPacket(iq);

		if (response == null
				|| (response.toString().contains("error") && response.toString().contains("remote-server-not-found"))) {
			LOGGER.warn("Remote server (Rendezvous) not found.");
			throw new Exception();
		}

		ArrayList<FederationMember> members = getMembersFromIQ(response);
		return members;
	}

	public static List<FederationMember> whoIsalive(String rendezvousAddress, PacketSender packetSender,
			int maxWhoIsAliveManagerCount) throws Exception {
		return whoIsalive(rendezvousAddress, packetSender, maxWhoIsAliveManagerCount, null);
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<FederationMember> getMembersFromIQ(IQ responseFromWhoIsAliveIQ) {
		Element queryElement = responseFromWhoIsAliveIQ.getElement().element("query");
		Iterator<Element> itemIterator = queryElement.elementIterator("item");
		ArrayList<FederationMember> aliveItems = new ArrayList<FederationMember>();

		while (itemIterator.hasNext()) {
			Element itemEl = (Element) itemIterator.next();
			Attribute id = itemEl.attribute("id");
			FederationMember item = new FederationMember(id.getValue());
			aliveItems.add(item);
		}
		return aliveItems;
	}

	public static void asynchronousRemoteRequest(String requestId, List<Category> categories,
			Map<String, String> xOCCIAttr, String memberAddress, Token userFederationToken,
			AsyncPacketSender packetSender, final AsynchronousRequestCallback callback) {

		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}

		IQ iq = new IQ();
		iq.setID(requestId);
		iq.setTo(memberAddress);
		iq.setType(Type.set);
		Element queryEl = iq.getElement().addElement("query", ManagerXmppComponent.REQUEST_NAMESPACE);
		for (Category category : categories) {
			Element categoryEl = queryEl.addElement("category");
			categoryEl.addElement("class").setText(category.getCatClass());
			categoryEl.addElement("term").setText(category.getTerm());
			categoryEl.addElement("scheme").setText(category.getScheme());
		}
		for (Entry<String, String> xOCCIEntry : xOCCIAttr.entrySet()) {
			Element attributeEl = queryEl.addElement("attribute");
			attributeEl.addAttribute("var", xOCCIEntry.getKey());
			attributeEl.addElement("value").setText(xOCCIEntry.getValue());
		}
		Element requestEl = queryEl.addElement("request");
		requestEl.addElement("id").setText(requestId);

		if (userFederationToken != null) {
			Element tokenEl = queryEl.addElement("token");
			tokenEl.addElement("accessId").setText(userFederationToken.getAccessId());
			tokenEl.addElement("user").setText(userFederationToken.getUser());
		}

		packetSender.addPacketCallback(iq, new PacketCallback() {

			@Override
			public void handle(Packet response) {
				if (response.getError() != null) {
					if (response.getError().getCondition().equals(Condition.item_not_found)) {
						callback.success(null);
					} else {
						callback.error(createException(response.getError()));
					}
				} else {
					callback.success(response.getElement().element("query").element("instance").elementText("id"));
				}
			}
		});
		packetSender.sendPacket(iq);
	}

	protected static Instance getRemoteInstance(Request request, PacketSender packetSender) {
		return getRemoteInstance(request.getProvidingMemberId(), request.getInstanceId(), packetSender);
	}

	public static Instance getRemoteInstance(String memberId, String instanceId, PacketSender packetSender) {

		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}

		IQ iq = new IQ();
		iq.setTo(memberId);
		iq.setType(Type.get);
		Element queryEl = iq.getElement().addElement("query", ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		try {
			instanceEl.addElement("id").setText(instanceId);
		} catch (Exception e) {
			// TODO: handle exception
		}

		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}

		if (response.getError() != null) {
			raiseException(response.getError());
		}

		return parseInstance(response.getElement().element("query").element("instance"));
	}

	public static void deleteRemoteInstace(Request request, PacketSender packetSender) {

		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}

		IQ iq = new IQ();
		iq.setTo(request.getProvidingMemberId());
		iq.setType(Type.set);
		Element queryEl = iq.getElement().addElement("query", ManagerXmppComponent.REMOVEINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(request.getInstanceId());

		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			raiseException(response.getError());
		}
	}

	private static void raiseException(PacketError error) {
		throw createException(error);
	}

	private static OCCIException createException(PacketError error) {
		Condition condition = error.getCondition();
		if (condition.equals(Condition.item_not_found)) {
			return new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
		if (condition.equals(Condition.not_authorized)) {
			return new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		return new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
	}

	@SuppressWarnings("unchecked")
	private static Instance parseInstance(Element instanceEl) {
		String id = instanceEl.element("id").getText();

		InstanceState state = InstanceState.valueOf(instanceEl.elementText("state"));

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

			Iterator<Element> resourceActionsIterator = itemResourseEl.element("category").elementIterator("action");
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
			String key = attributeEl.attributeValue("val");
			String value = attributeEl.getText();
			attributes.put(key, value);
		}

		return new Instance(id, resources, attributes, links, state);
	}

	public static Condition getCondition(OCCIException e) {
		// TODO check if it is not needs other codes, e.g. quota exceeded
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

	public static void checkIfInstanceIsBeingUsedByRemoteMember(String instanceId, Request servedRequest,
			PacketSender packetSender) {

		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}

		IQ iq = new IQ();
		iq.setTo(servedRequest.getRequestingMemberId());
		iq.setType(Type.get);
		Element queryEl = iq.getElement().addElement("query", ManagerXmppComponent.INSTANCEBEINGUSED_NAMESPACE);
		Element requestEl = queryEl.addElement("request");
		requestEl.addElement("id").setText(servedRequest.getId());
		if (instanceId != null) {
			Element instanceEl = queryEl.addElement("instance");
			instanceEl.addElement("id").setText(instanceId);
		}
		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			raiseException(response.getError());
		}
	}

	public static void replyToServedRequest(Request request, PacketSender packetSender) {

		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}

		IQ response = new IQ(Type.result, request.getId());
		response.setFrom(request.getProvidingMemberId());
		response.setTo(request.getRequestingMemberId());

		if (request.getInstanceId() == null) {
			response.setError(Condition.item_not_found);
		} else {
			Element queryResponseEl = response.getElement().addElement("query", ManagerXmppComponent.REQUEST_NAMESPACE);
			queryResponseEl.addElement("instance").addElement("id").setText(request.getInstanceId());
		}
		packetSender.sendPacket(response);
	}

	public static ResourcesInfo getRemoteUserQuota(String accessId, String memberId, PacketSender packetSender)
			throws Exception {
		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}

		IQ iq = new IQ();
		iq.setTo(memberId);
		iq.setType(Type.get);
		Element queryEl = iq.getElement().addElement("query", ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
		Element userEl = queryEl.addElement("token");
		userEl.addElement("accessId").setText(accessId);

		IQ response = (IQ) packetSender.syncSendPacket(iq);
		if (response.getError() != null) {
			raiseException(response.getError());
		}

		return parseResourcesInfo(response.getElement().element("query").element("resourcesInfo"));
		
	}
	
	private static ResourcesInfo parseResourcesInfo(Element instanceEl) {
		String id = instanceEl.element("id").getText();
		String cpuIdle = instanceEl.element("cpuIdle").getText();
		String cpuInUse = instanceEl.element("cpuInUse").getText();
		String instancesIdle = instanceEl.element("instancesIdle").getText();
		String instancesInUse = instanceEl.element("instancesInUse").getText();
		String memIdle = instanceEl.element("memIdle").getText();
		String memInUse = instanceEl.element("memInUse").getText();

		return new ResourcesInfo(id, cpuIdle, cpuInUse, memIdle, memInUse, instancesIdle, instancesInUse);
	}
	
	public static void deleteRemoteRequest(String providingMember, Request request, 
			AsyncPacketSender packetSender, final AsynchronousRequestCallback callback) {
		if (packetSender == null) {
			LOGGER.warn("Packet sender not set.");
			throw new IllegalArgumentException("Packet sender not set.");
		}
		
		IQ iq = new IQ();
		iq.setTo(providingMember);
		iq.setType(Type.set);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.REMOVEREQUEST_NAMESPACE);
		Element requestEl = queryEl.addElement("request");
		requestEl.addElement("id").setText(request.getId());
		Element tokenEl = queryEl.addElement("token");
		tokenEl.addElement("accessId").setText(request.getFederationToken().getAccessId());
		
		packetSender.addPacketCallback(iq, new PacketCallback() {		
			@Override
			public void handle(Packet response) {
				if (response.getError() != null) {
					callback.error(null);
				} else {
					callback.success(null);
				}
			}
		});
		packetSender.sendPacket(iq);
	}
}
