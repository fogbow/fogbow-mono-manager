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
	public static final String ORDER_NAMESPACE = "http://fogbowcloud.org/manager/order";
	public static final String STORAGE_LINK_NAMESPACE = "http://fogbowcloud.org/manager/storagelink";
	public static final String GETINSTANCE_NAMESPACE = "http://fogbowcloud.org/manager/getinstance";
	public static final String REMOVEINSTANCE_NAMESPACE = "http://fogbowcloud.org/manager/removeinstance";
	public static final String REMOVESTORAGELINK_NAMESPACE = "http://fogbowcloud.org/manager/removestoragelink";
	public static final String INSTANCEBEINGUSED_NAMESPACE = "http://fogbowcloud.org/manager/instancebeingused";
	public static final String REMOVEORDER_NAMESPACE = "http://fogbowcloud.org/manager/removeorder";
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
		addSetHandler(new OrderInstanceHandler(managerFacade));
		addGetHandler(new InstanceBeingUsedHandler(managerFacade));
		addSetHandler(new RemoveOrderHandler(managerFacade));
		addGetHandler(new GetRemoteUserQuotaHandler(managerFacade));
		addSetHandler(new StorageLinkHandler(managerFacade));	
		addSetHandler(new RemoveStorageLinkHandler(managerFacade));	
	}

	public void init() {
		scheduleIamAlive();
	}

	public long iAmAlive() throws CertificateException, Exception {
		String iAmAlivePeriodStr = ManagerPacketHelper
				.iAmAlive(rendezvousAddress, managerFacade.getProperties(), this);
		
		try {
			return Long.parseLong(iAmAlivePeriodStr);
		} catch (Exception e) {
			LOGGER.warn("Error while trying to convert String(" + iAmAlivePeriodStr + ") to Long.",
					e);
		}	
		return PERIOD;
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
				.whoIsalive(rendezvousAddress, this, maxWhoIsAliveManagerCount, after);
		managerFacade.updateMembers(whoIsaliveResponse);
		return whoIsaliveResponse;
	}
	
	private void scheduleIamAlive() {
		scheduleIamAlive(0, PERIOD);
	}
	
	private void scheduleIamAlive(final long delay, final long period) {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					long currentIAmAlivePeriod = iAmAlive();
					
					if (period != currentIAmAlivePeriod) {
						scheduleIamAlive(currentIAmAlivePeriod, currentIAmAlivePeriod);
						this.cancel();
					}					
				} catch (Exception e) {
					LOGGER.error("Failure during IAmAlive().", e);
				}
				try {
					whoIsalive();
				} catch (Exception e) {
					LOGGER.error("Failure during whoIsAlive().", e); 
				}
			}
		}, delay, period);
	}

	public void setRendezvousAddress(String address) {
		rendezvousAddress = address;
	}

	public ManagerController getManagerFacade() {
		return managerFacade;
	}
}
