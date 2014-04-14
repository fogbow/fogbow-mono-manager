package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.dom4j.Element;
import org.fogbowcloud.manager.xmpp.model.ManagerTestHelper;
import org.fogbowcloud.manager.xmpp.model.RendezvousItemCopy;
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

public class TestManagerComponent {
	
	private ManagerTestHelper managerTestHelper;
	private ManagerXmppComponent managerXmppComponent;
	
	@Before
	public void setUp() throws ComponentException {
		managerTestHelper = new ManagerTestHelper();
	}

	@Test
	public void testIAmAlive() throws Exception {
		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(false);
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();

		final BlockingQueue<Packet> blockingQueue = new LinkedBlockingQueue<Packet>(
				1);

		final PacketListener callback = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ iAmAlive = (IQ) packet;
				try {
					blockingQueue.put(packet);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				xmppClient.send(IQ.createResultIQ(iAmAlive));
			}
		};

		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				return packet.getFrom().toBareJID()
						.equals(ManagerTestHelper.MANAGER_COMPONENT_URL);
			}
		}, callback);

		managerXmppComponent.iAmAlive();

		Packet packet = blockingQueue.poll(5, TimeUnit.SECONDS);
		Element element = packet.getElement().element("query");
		Element iqelement = element.element("status");
		String cpuIdle = iqelement.element("cpu-idle").getText();
		String cpuInUse = iqelement.element("cpu-inuse").getText();
		String memIdle = iqelement.element("mem-idle").getText();
		String memInUse = iqelement.element("mem-inuse").getText();
		Assert.assertEquals(cpuIdle, managerTestHelper.getResources()
				.getCpuIdle());
		Assert.assertEquals(cpuInUse, managerTestHelper.getResources()
				.getCpuInUse());
		Assert.assertEquals(memIdle, managerTestHelper.getResources()
				.getMemIdle());
		Assert.assertEquals(memInUse, managerTestHelper.getResources()
				.getMemInUse());

		xmppClient.disconnect();
	}

	@Test
	public void testWhoIsAlive() throws XMPPException, InterruptedException,
			ComponentException {
		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(false);
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();
		final BlockingQueue<Packet> blockingQueue = new LinkedBlockingQueue<Packet>(
				1);

		final PacketListener callback = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ whoIsAlive = (IQ) packet;
				try {
					blockingQueue.put(packet);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				List<RendezvousItemCopy> aliveIds = new ArrayList<RendezvousItemCopy>();
				aliveIds.add(new RendezvousItemCopy(managerTestHelper
						.getResources()));
				IQ iq = managerTestHelper.createWhoIsAliveResponse(
						(ArrayList<RendezvousItemCopy>) aliveIds, whoIsAlive);
				xmppClient.send(iq);
			}
		};

		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				return packet.getFrom().toBareJID()
						.equals(ManagerTestHelper.MANAGER_COMPONENT_URL);
			}
		}, callback);
		managerXmppComponent.whoIsalive();

		Packet packet = blockingQueue.poll(5, TimeUnit.SECONDS);
		Element element = packet.getElement().element("query");
		Assert.assertEquals(element.getNamespaceURI(),
				ManagerTestHelper.WHOISALIVE_NAMESPACE);

		Assert.assertEquals(1, managerXmppComponent.getManagerFacade()
				.getManagerModel().getMembers().size());
		xmppClient.disconnect();
	}

	@Test
	public void testCallIAmAlive() throws XMPPException, InterruptedException,
			ComponentException {
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();
		final Semaphore semaphore = new Semaphore(0);
		
		final PacketListener callbackIAmAlive = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ iAmAlive = (IQ) packet;
				semaphore.release();
				xmppClient.send(IQ.createResultIQ(iAmAlive));
			}
		};
		
		final PacketListener callbackWhoIsAlive = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ whoIsAlive = (IQ) packet;
				List<RendezvousItemCopy> aliveIds = new ArrayList<RendezvousItemCopy>();
				aliveIds.add(new RendezvousItemCopy(managerTestHelper
						.getResources()));
				IQ iq = managerTestHelper.createWhoIsAliveResponse(
						(ArrayList<RendezvousItemCopy>) aliveIds, whoIsAlive);
				try {
					xmppClient.syncSend(iq);
				} catch (XMPPException e) {
					//No problem if exception is throwed
				}

			}
		};
		
		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				Element element = packet.getElement().element("query");
				return element.getNamespaceURI().equals(
						ManagerTestHelper.IAMALIVE_NAMESPACE);
			}
		}, callbackIAmAlive);
		
		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				Element element = packet.getElement().element("query");
				return element.getNamespaceURI().equals(
						ManagerTestHelper.WHOISALIVE_NAMESPACE);
			}
		}, callbackWhoIsAlive);
		
		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(true);
		Assert.assertTrue(semaphore.tryAcquire(10000, TimeUnit.MILLISECONDS));
		xmppClient.disconnect();
	}

	@Test
	public void testCallWhoIsAlive() throws XMPPException,
			InterruptedException, ComponentException {
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();
		final Semaphore semaphore = new Semaphore(0);
		final PacketListener callbackWhoIsAlive = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ whoIsAlive = (IQ) packet;
				List<RendezvousItemCopy> aliveIds = new ArrayList<RendezvousItemCopy>();
				aliveIds.add(new RendezvousItemCopy(managerTestHelper
						.getResources()));
				IQ iq = managerTestHelper.createWhoIsAliveResponse(
						(ArrayList<RendezvousItemCopy>) aliveIds, whoIsAlive);
				try {
					xmppClient.syncSend(iq);
				} catch (XMPPException e) {
					//No problem if exception is throwed
				}
				semaphore.release();
			}
		};
		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				Element element = packet.getElement().element("query");
				return element.getNamespaceURI().equals(
						ManagerTestHelper.WHOISALIVE_NAMESPACE);
			}
		}, callbackWhoIsAlive);

		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(true);
		Assert.assertTrue(semaphore.tryAcquire(20000, TimeUnit.MILLISECONDS));
		Assert.assertEquals(1, managerXmppComponent.getManagerFacade()
				.getManagerModel().getMembers().size());
		xmppClient.disconnect();
	}

	@After
	public void tearDown() throws ComponentException {
		managerTestHelper.shutdown();
	}
}
