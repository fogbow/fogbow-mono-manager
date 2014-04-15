package org.fogbowcloud.manager.xmpp;

import java.util.Timer;
import java.util.TimerTask;

import org.dom4j.Element;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.xmpp.core.ManagerFacade;
import org.fogbowcloud.manager.xmpp.core.ManagerModel;
import org.jamppa.component.XMPPComponent;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;

public class ManagerXmppComponent extends XMPPComponent {

	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";
	private static long PERIOD = 100;
	private ManagerFacade managerFacade;
	private final Timer timer = new Timer();
	private String rendezvousAdress;
	private ComputePlugin plugin;
	
	public ManagerXmppComponent(String jid, String password, String server,
			int port, ComputePlugin plugin) {
		super(jid, password, server, port);
		managerFacade = new ManagerFacade(new ManagerModel());
		this.plugin = plugin;
	}

	@Override
	public void connect() throws ComponentException {
		super.connect();
	}

	public void init(String authToken) {
		callIamAlive(authToken);
	}
	
	public void iAmAlive(String authToken) {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAdress);
		iq.setFrom(getJID());
		Element statusEl = iq.getElement()
				.addElement("query", IAMALIVE_NAMESPACE).addElement("status");
		statusEl.addElement("cpu-idle").setText(plugin.getResourcesInfo(authToken).getCpuIdle());
		statusEl.addElement("cpu-inuse").setText(plugin.getResourcesInfo(authToken).getCpuInUse());
		statusEl.addElement("mem-idle").setText(plugin.getResourcesInfo(authToken).getMemIdle());
		statusEl.addElement("mem-inuse").setText(plugin.getResourcesInfo(authToken).getMemInUse());
		this.syncSendPacket(iq);
	}

	public void whoIsalive() {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAdress);
		
		iq.getElement().addElement("query", WHOISALIVE_NAMESPACE);
		IQ response = (IQ) this.syncSendPacket(iq);
		managerFacade.getItemsFromIQ(response);
	}

	private void callIamAlive(final String authToken) {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				iAmAlive(authToken);
				whoIsalive();
			}
		}, 0, PERIOD);
	}

	public void setRendezvousAdress(String adress) {
		rendezvousAdress = adress;
	}

	public ManagerFacade getManagerFacade() {
		return managerFacade;
	}

}
