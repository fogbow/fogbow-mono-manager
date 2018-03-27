package org.fogbowcloud.manager.xmpp;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerTestHelper;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.jamppa.client.XMPPClient;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class TestWhoIsAlive {

	private ManagerTestHelper managerTestHelper;
	private ManagerXmppComponent managerXmppComponent;

	@Before
	public void setUp() throws ComponentException {
		managerTestHelper = new ManagerTestHelper();
	}

	@Test
	public void testWhoIsAlive() throws Exception {
		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(false);
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();
		final BlockingQueue<Packet> blockingQueue = new LinkedBlockingQueue<Packet>(1);

		final PacketListener callback = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ whoIsAlive = (IQ) packet;
				try {
					blockingQueue.put(packet);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				List<FederationMember> aliveIds = new ArrayList<FederationMember>();
				aliveIds.add(new FederationMember("One"));
				aliveIds.add(new FederationMember(ManagerTestHelper.MANAGER_TEST_JID));
				IQ iq = managerTestHelper.createWhoIsAliveResponse(
                        (ArrayList<FederationMember>) aliveIds, whoIsAlive);
				xmppClient.send(iq);
			}
		};

		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				if (packet.getFrom() == null) {
					return false;
				}
				return packet.getFrom().toBareJID()
						.equals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
			}
		}, callback);
		managerXmppComponent.whoIsalive();

		Packet packet = blockingQueue.poll(5, TimeUnit.SECONDS);
		Element element = packet.getElement().element("query");
		Assert.assertEquals(element.getNamespaceURI(),
				DefaultDataTestHelper.WHOISALIVE_NAMESPACE);

		// The other member and the manager itself
		Assert.assertEquals(2, managerXmppComponent.getManagerFacade()
				.getRendezvousMembers().size());
		xmppClient.disconnect();
	}

	@Test
	public void testCallWhoIsAlive() throws Exception {
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();
		final Semaphore semaphore = new Semaphore(0);
		final PacketListener callbackWhoIsAlive = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ whoIsAlive = (IQ) packet;
				List<FederationMember> aliveIds = new ArrayList<FederationMember>();
				aliveIds.add(new FederationMember("One"));
				aliveIds.add(new FederationMember(ManagerTestHelper.MANAGER_TEST_JID));				
				IQ iq = managerTestHelper.createWhoIsAliveResponse(
                        (ArrayList<FederationMember>) aliveIds, whoIsAlive);
				try {
					xmppClient.syncSend(iq);
				} catch (XMPPException e) {
					// No problem if exception is throwed
				}
				semaphore.release();
			}
		};
		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				Element element = packet.getElement().element("query");
				if (element == null) {
					return false;
				}
				return element.getNamespaceURI().equals(
						DefaultDataTestHelper.WHOISALIVE_NAMESPACE);
			}
		}, callbackWhoIsAlive);

		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(true);
		Assert.assertTrue(semaphore.tryAcquire(20000, TimeUnit.MILLISECONDS));
		// The other member and the manager itself
		Assert.assertEquals(2, managerXmppComponent.getManagerFacade()
				.getRendezvousMembers().size());
		xmppClient.disconnect();
	}
	
	@Test
	public void testWhoIsAlivePagination() throws Exception {
		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(false);
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();
		final Semaphore semaphore = new Semaphore(0);

		final PacketListener callback = new PacketListener() {
			public void processPacket(Packet packet) {
				Element queryEl = ((IQ) packet).getElement().element("query");
				Element setEl = queryEl.element("set");
				String maxEl = setEl.element("max").getText();
				if (Integer.parseInt(maxEl) == ManagerTestHelper.MAX_WHOISALIVE_MANAGER_COUNT) {
					semaphore.release();
				}

				// also respond
				ArrayList<FederationMember> aliveIds = new ArrayList<FederationMember>();
				try {
					aliveIds.add(new FederationMember(managerTestHelper
							.getResources()));
				} catch (Exception e) {
				}
				IQ iq = null;
				try {
					iq = managerTestHelper.createWhoIsAliveResponse(
							 aliveIds, (IQ) packet);
				} catch (Exception e) {
				} 
				
				xmppClient.send(iq);
			}
		};

		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				if (packet.getFrom() == null) {
					return false;
				}
				return packet.getFrom().toBareJID()
						.equals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
			}
		}, callback);
		managerXmppComponent.whoIsalive();
		Assert.assertTrue(semaphore.tryAcquire());
	}
	
	@Test
	public void testWhoIsAliveOrderSecondPage() throws Exception {
		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(false);
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();
		final Semaphore semaphore = new Semaphore(0);

		final PacketListener callback = new PacketListener() {
			public void processPacket(Packet packet) {
				Element queryEl = ((IQ) packet).getElement().element("query");
				Element setEl = queryEl.element("set");
				String maxEl = setEl.element("max").getText();
				if (Integer.parseInt(maxEl) == ManagerTestHelper.MAX_WHOISALIVE_MANAGER_COUNT) {
					semaphore.release();
				}
				String afterEl = setEl.element("after").getText();
				if(afterEl.equals("after")) {
					semaphore.release();
				}
				// also respond
				ArrayList<FederationMember> aliveIds = new ArrayList<FederationMember>();
				try {
					aliveIds.add(new FederationMember(managerTestHelper
							.getResources()));
				} catch (Exception e) {
				}
				IQ iq = null;
				try {
					iq = managerTestHelper.createWhoIsAliveResponse(
							 aliveIds, (IQ) packet);
				} catch (Exception e) {
				} 
				
				xmppClient.send(iq);
			}
		};

		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				if (packet.getFrom() == null) {
					return false;
				}
				return packet.getFrom().toBareJID()
						.equals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
			}
		}, callback);
		managerXmppComponent.whoIsalive("after");
		Assert.assertTrue(semaphore.tryAcquire(2));
	}
	
	@After
	public void tearDown() throws ComponentException {
		managerTestHelper.shutdown();
	}
}
