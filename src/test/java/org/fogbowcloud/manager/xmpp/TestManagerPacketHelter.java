package org.fogbowcloud.manager.xmpp;

import org.jamppa.component.PacketSender;
import org.junit.Test;
import org.mockito.Mockito;
import org.xmpp.packet.IQ;

public class TestManagerPacketHelter {

	@Test(expected=Exception.class)
	public void testIAmAliveWithRendezvousNotSpecified() throws Exception {
		ManagerPacketHelper.iAmAlive(null, null, null);
	}

	@Test(expected=Exception.class)
	public void testWhoIsAliveWithRendezvousNotSpecified() throws Exception {
		ManagerPacketHelper.whoIsalive(null, null, 0);
	}
	
	@Test(expected=Exception.class)
	public void testWhoIsAlive() throws Exception {
		PacketSender packetSender = Mockito.mock(PacketSender.class);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(null);
		ManagerPacketHelper.whoIsalive("rendezvous", packetSender, 10);
	}
	
}
