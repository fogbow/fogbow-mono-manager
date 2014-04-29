package org.fogbowcloud.manager.xmpp;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Timer;
import java.util.TimerTask;

import org.fogbowcloud.manager.core.ManagerFacade;
import org.jamppa.component.XMPPComponent;
import org.xmpp.packet.Packet;

public class ManagerXmppComponent extends XMPPComponent {

	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";
	public static final String REQUEST_NAMESPACE = "http://fogbowcloud.org/manager/request";
	public static final String GETINSTANCE_NAMESPACE = "http://fogbowcloud.org/manager/getinstance";
	public static final String REMOVEINSTANCE_NAMESPACE = "http://fogbowcloud.org/manager/removeinstance";

	private static long PERIOD = 100;
	private ManagerFacade managerFacade;
	private final Timer timer = new Timer();
	private String rendezvousAddress;

	public ManagerXmppComponent(String jid, String password, String server,
			int port, ManagerFacade managerFacade) {
		super(jid, password, server, port);
		this.managerFacade = managerFacade;
		addGetHandler(new GetInstanceHandler(managerFacade));
		addSetHandler(new RemoveInstanceHandler(managerFacade));
		addSetHandler(new RequestInstanceHandler(managerFacade));
	}

	public void init() {
		scheduleIamAlive();
	}

	public void iAmAlive() throws CertificateException, IOException {
		ManagerPacketHelper.iAmAlive(managerFacade.getResourcesInfo(),
				rendezvousAddress, this);
	}

	@Override
	protected void send(Packet packet) {
		packet.setFrom(getJID());
		super.send(packet);
	}

	public void whoIsalive() throws CertificateException {
		managerFacade.updateMembers(ManagerPacketHelper.whoIsalive(
				rendezvousAddress, this));
	}

	private void scheduleIamAlive() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					iAmAlive();
				} catch (CertificateException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					whoIsalive();
				} catch (CertificateException e) {
					e.printStackTrace();
				}
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
