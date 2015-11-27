package org.fogbowcloud.manager.xmpp;

import java.security.cert.CertificateException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.jamppa.component.XMPPComponent;
import org.xmpp.packet.Packet;

public class ManagerXmppComponent extends XMPPComponent implements AsyncPacketSender {

	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";
	public static final String REQUEST_NAMESPACE = "http://fogbowcloud.org/manager/request";
	public static final String GETINSTANCE_NAMESPACE = "http://fogbowcloud.org/manager/getinstance";
	public static final String REMOVEINSTANCE_NAMESPACE = "http://fogbowcloud.org/manager/removeinstance";
	public static final String INSTANCEBEINGUSED_NAMESPACE = "http://fogbowcloud.org/manager/instancebeingused";
	public static final String GETREMOTEUSERQUOTA_NAMESPACE = "http://fogbowcloud.org/manager/getremoteuserquota";

	private static long PERIOD = 30000;
	private static Logger LOGGER = Logger.getLogger(ManagerXmppComponent.class);
	private ManagerController managerFacade;
	private final Timer timer = new Timer();
	private String rendezvousAddress;
	private int maxWhoIsAliveManagerCount = 100;
	
	public ManagerXmppComponent(String jid, String password, String server,
			int port, ManagerController managerFacade) {
		super(jid, password, server, port);
		this.managerFacade = managerFacade;
		if (managerFacade.getMaxWhoIsAliveManagerCount() != null) {
			this.maxWhoIsAliveManagerCount = managerFacade.getMaxWhoIsAliveManagerCount();
		}
		addGetHandler(new GetInstanceHandler(managerFacade));
		addSetHandler(new RemoveInstanceHandler(managerFacade));
		addSetHandler(new RequestInstanceHandler(managerFacade));
		addGetHandler(new InstanceBeingUsedHandler(managerFacade));
		addGetHandler(new GetRemoteUserQuotaHandler(managerFacade));
		
	}

	public void init() {
		scheduleIamAlive();
	}

	public void iAmAlive() throws CertificateException, Exception {
		ManagerPacketHelper.iAmAlive(managerFacade.getResourcesInfo(),
				rendezvousAddress, managerFacade.getProperties(), this);
	}
	
	@Override
	protected void send(Packet packet) {
		packet.setFrom(getJID());
		LOGGER.debug("(sending IQ to " + packet.getTo() + ", packetId " + packet.getID() + ", XML "
				+ packet.toXML());
		super.send(packet);
	}

	public void whoIsalive() throws Exception {
		managerFacade.updateMembers(ManagerPacketHelper.whoIsalive(
				rendezvousAddress, this, maxWhoIsAliveManagerCount));
	}
	
	public List<FederationMember> whoIsalive(String after)
			throws Exception {
		List<FederationMember> whoIsaliveResponse = ManagerPacketHelper
				.whoIsalive(rendezvousAddress, this, maxWhoIsAliveManagerCount,
						after);
		managerFacade.updateMembers(whoIsaliveResponse);
		return whoIsaliveResponse;
	}
	
	private void scheduleIamAlive() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					iAmAlive();
				} catch (Exception e) {
					LOGGER.error("Failure during IAmAlive().");
				}
				try {
					whoIsalive();
				} catch (Exception e) {
					LOGGER.error("Failure during whoIsAlive()."); 
				}
			}
		}, 0, PERIOD);
	}

	public void setRendezvousAddress(String address) {
		rendezvousAddress = address;
	}

	public ManagerController getManagerFacade() {
		return managerFacade;
	}
}
