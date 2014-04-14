package org.fogbowcloud.manager.xmpp.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.fogbowcloud.manager.xmpp.core.ResourcesInfo;
import org.jamppa.client.XMPPClient;
import org.jamppa.client.plugin.xep0077.XEP0077;
import org.jivesoftware.smack.XMPPException;
import org.xbill.DNS.tests.primary;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class ManagerTestHelper {

	private static final int SERVER_CLIENT_PORT = 5222;
	private static final int SERVER_COMPONENT_PORT = 5347;
	private static final String SERVER_HOST = "localhost";
	private static final String CLIENT_ADRESS = "client@test.com";
	private static final String CLIENT_PASS = "password";
	private static final String SMACK_ENDING = "/Smack";
	
	public static final String MANAGER_COMPONENT_URL = "manager.test.com";
	public static final String MANAGER_COMPONENT_PASS = "password";

	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";

	public static final int TEST_DEFAULT_TIMEOUT = 10000;
	public static final int TIMEOUT_GRACE = 500;

	private ManagerXmppComponent managerXmppComponent;

	public ResourcesInfo getResources() {
		ResourcesInfo resources = new ResourcesInfo("abc", "value1", "value2",
				"value3", "value4");
		return resources;
	}

	public IQ createWhoIsAliveResponse(ArrayList<RendezvousItemCopy> aliveIds,
			IQ iq) {
		IQ resultIQ = IQ.createResultIQ(iq);
		Element queryElement = resultIQ.getElement().addElement("query",
				WHOISALIVE_NAMESPACE);
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

	public XMPPClient createXMPPClient() throws XMPPException {

		XMPPClient xmppClient = new XMPPClient(CLIENT_ADRESS, CLIENT_PASS,
				SERVER_HOST, SERVER_CLIENT_PORT);
		XEP0077 register = new XEP0077();
		xmppClient.registerPlugin(register);
		xmppClient.connect();
		try {
			register.createAccount(CLIENT_ADRESS, CLIENT_PASS);
		} catch (XMPPException e) {
		}

		xmppClient.login();
		xmppClient.process(false);

		return xmppClient;
	}

	public ManagerXmppComponent initializeXMPPManagerComponent(boolean init)
			throws ComponentException {
		managerXmppComponent = new ManagerXmppComponent(MANAGER_COMPONENT_URL,
				MANAGER_COMPONENT_PASS, SERVER_HOST, SERVER_COMPONENT_PORT);
		managerXmppComponent.setDescription("Manager Component");
		managerXmppComponent.setName("Manager");
		managerXmppComponent.setRendezvousAdress(CLIENT_ADRESS + SMACK_ENDING);
		managerXmppComponent.connect();
		managerXmppComponent.process();
		if (init) {
			managerXmppComponent.init();
		}
		return managerXmppComponent;
	}

	public IQ CreateImAliveResponse(IQ iq) {
		IQ response = IQ.createResultIQ(iq);
		return response;
	}

	public void shutdown() throws ComponentException {
		managerXmppComponent.disconnect();
	}

	@SuppressWarnings("unchecked")
	public List<RendezvousItemCopy> getItemsFromIQ(Packet response) {
		Element queryElement = response.getElement().element(
				"query");
		Iterator<Element> itemIterator = queryElement.elementIterator("item");
		ArrayList<RendezvousItemCopy> aliveItems = new ArrayList<RendezvousItemCopy>();

		while (itemIterator.hasNext()) {
			Element itemEl = (Element) itemIterator.next();
			Attribute id = itemEl.attribute("id");
			Element statusEl = itemEl.element("status");
			String cpuIdle = statusEl.element("cpu-idle").getText();
			String cpuInUse = statusEl.element("cpu-inuse").getText();
			String memIdle = statusEl.element("mem-idle").getText();
			String memInUse = statusEl.element("mem-inuse").getText();
			//String updated = statusEl.element("updated").getText();

			ResourcesInfo resources = new ResourcesInfo(id.getValue(), cpuIdle,
					cpuInUse, memIdle, memInUse);
			RendezvousItemCopy item = new RendezvousItemCopy(resources);
			aliveItems.add(item);
		}
		return aliveItems;
	}

	/*
	 * public static IQ createIAmAliveIQ() { IQ iq = new IQ(Type.get);
	 * iq.setTo(RENDEZVOUS_COMPONENT_URL); Element statusEl = iq.getElement()
	 * .addElement("query", IAMALIVE_NAMESPACE).addElement("status");
	 * statusEl.addElement("cpu-idle").setText("valor1");
	 * statusEl.addElement("cpu-inuse").setText("valor2");
	 * statusEl.addElement("mem-idle").setText("valor3");
	 * statusEl.addElement("mem-inuse").setText("valor4"); return iq; }
	 */
}
