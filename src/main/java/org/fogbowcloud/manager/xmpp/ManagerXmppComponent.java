package org.fogbowcloud.manager.xmpp;

import java.util.Timer;
import java.util.TimerTask;

import org.fogbowcloud.manager.core.ManagerFacade;
import org.jamppa.component.XMPPComponent;
import org.xmpp.packet.Packet;

public class ManagerXmppComponent extends XMPPComponent {

	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";
	public static final String REQUEST_NAMESPACE = "http://fogbowcloud.org/manager/request";

	private static long PERIOD = 100;
	private ManagerFacade managerFacade;
	private final Timer timer = new Timer();
	private String rendezvousAddress;

	public ManagerXmppComponent(String jid, String password, String server,
			int port, ManagerFacade managerFacade) {
		super(jid, password, server, port);
		this.managerFacade = managerFacade;
	}

	public void init() {
		scheduleIamAlive();
	}

	public void iAmAlive() {
		ManagerPacketHelper.iAmAlive(managerFacade.getResourcesInfo(),
				rendezvousAddress, this);
	}

	@Override
	protected void send(Packet packet) {
		packet.setFrom(getJID());
		super.send(packet);
	}

	public void whoIsalive() {
		managerFacade.updateMembers(ManagerPacketHelper.whoIsalive(
				rendezvousAddress, this));
	}

	private void scheduleIamAlive() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				iAmAlive();
				whoIsalive();
			}
		}, 0, PERIOD);
	}

	public void setRendezvousAddress(String address) {
		rendezvousAddress = address;
	}

	public ManagerFacade getManagerFacade() {
		return managerFacade;
	}

}
