package org.fogbowcloud.manager.xmpp;

import org.jamppa.component.PacketSender;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.xmpp.packet.IQ;

public class TestManagerPacketHelter {

	private ManagerPacketHelper managerPacketHelper;
	
	@Before
	public void setUp() {
		managerPacketHelper = new ManagerPacketHelper();
	}
	
	@SuppressWarnings("static-access")
	@Test(expected=Exception.class)
	public void testIAmAliveWithRendezvousNotSpecified() throws Exception {
		managerPacketHelper.iAmAlive(null, null, null, null);
	}

	@SuppressWarnings("static-access")
	@Test(expected=Exception.class)
	public void testWhoIsAliveWithRendezvousNotSpecified() throws Exception {
		managerPacketHelper.whoIsalive(null, null, 0);
	}
	
	@SuppressWarnings("static-access")
	@Test(expected=Exception.class)
	public void testWhoIsAlive() throws Exception {
		PacketSender packetSender = Mockito.mock(PacketSender.class);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(null);
		managerPacketHelper.whoIsalive("rendezvous", packetSender, 10);
	}
	
}
