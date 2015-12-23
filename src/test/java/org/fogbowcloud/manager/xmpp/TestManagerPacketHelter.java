package org.fogbowcloud.manager.xmpp;

import java.util.Properties;

import org.jamppa.component.PacketSender;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;

public class TestManagerPacketHelter {

	@Test(expected = Exception.class)
	public void testIAmAliveWithRendezvousNotSpecified() throws Exception {
		ManagerPacketHelper.iAmAlive(null, null, null);
	}

	@Test(expected = Exception.class)
	public void testWhoIsAliveWithRendezvousNotSpecified() throws Exception {
		ManagerPacketHelper.whoIsalive(null, null, 0);
	}

	@Test(expected = Exception.class)
	public void testWhoIsAlive() throws Exception {
		PacketSender packetSender = Mockito.mock(PacketSender.class);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(null);
		ManagerPacketHelper.whoIsalive("rendezvous", packetSender, 10);
	}

	@Test
	public void testIAmAliveReponseWithoutIAmAlivePeriod() throws Exception {
		PacketSender packetSender = Mockito.mock(PacketSender.class);
		IQ iq = new IQ(Type.get, "abc");
		IQ createResultIQ = IQ.createResultIQ(iq);
		createResultIQ.getElement().addElement("query");
		
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(createResultIQ);
		Assert.assertEquals("", ManagerPacketHelper.iAmAlive("abc", new Properties(), packetSender));
	}
	
	@Test
	public void testIAmAliveReponseWithinIAmAlivePeriod() throws Exception {
		PacketSender packetSender = Mockito.mock(PacketSender.class);
		IQ iq = new IQ(Type.get, "abc");
		IQ createResultIQ = IQ.createResultIQ(iq);
		String value = "10";
		createResultIQ.getElement().addElement("query").addElement("iamalive-period").setText(value);
		
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(createResultIQ);
		Assert.assertEquals(value, ManagerPacketHelper.iAmAlive("abc", new Properties(), packetSender));
	}	

}
