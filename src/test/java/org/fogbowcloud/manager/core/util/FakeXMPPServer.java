package org.fogbowcloud.manager.core.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jamppa.client.XMPPClient;
import org.jamppa.component.XMPPComponent;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.util.SyncPacketSend;
import org.jivesoftware.whack.ExternalComponentManager;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xmpp.component.Component;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

public class FakeXMPPServer {

	private Map<String, Object> onlineEntities = new HashMap<String, Object>();
	private Executor executor = Executors.newCachedThreadPool();
	
	private void send(final Packet p) throws Exception {
		Object obj = onlineEntities.get(p.getTo().toBareJID());
		if (obj instanceof Component) {
			Component comp = (Component) obj;
			comp.processPacket(p);
		} else if (obj instanceof XMPPClient) {
			XMPPClient xmpp = (XMPPClient) obj;
			XMPPConnection conn = xmpp.getConnection();
			Collection<PacketCollector> collectors = getField(Connection.class, conn, "collectors");
			for (PacketCollector packetCollector : collectors) {
				Method processPacketMethod = PacketCollector.class.getDeclaredMethod(
						"processPacket", Packet.class);
				processPacketMethod.setAccessible(true);
				processPacketMethod.invoke(packetCollector, p);
			}
			
			Class<?> listenerWrapperClass = Class.forName("org.jivesoftware.smack.Connection$ListenerWrapper");
			Map<PacketListener, ?> recvListeners =  getField(Connection.class, conn, "recvListeners");
			for (final Object listenerWrapper : recvListeners.values()) {
				final Method notifyListenerMethod = listenerWrapperClass.getDeclaredMethod(
						"notifyListener", Packet.class);
				notifyListenerMethod.setAccessible(true);
				executor.execute(new Runnable() {
					@Override
					public void run() {
						try {
							notifyListenerMethod.invoke(listenerWrapper, p);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
            }
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T getField(Class<?> clazz, Object o, String fieldName)
			throws Exception {
		Field field = clazz.getDeclaredField(fieldName);
		field.setAccessible(true);
		return (T) field.get(o);
	}
	
	private void connect(String jid, Object o) {
		onlineEntities.put(jid, o);
	}
	
	public void connect(final XMPPClient client) throws XMPPException {
		final String from = client.getJid().toBareJID();
		connect(from, client);
		Mockito.doAnswer(createAnswer(from, 0)).when(
				client).send(Mockito.any(Packet.class));
		
		Mockito.doAnswer(new Answer<Packet>() {
			@Override
			public Packet answer(InvocationOnMock invocation) throws Throwable {
				Packet p = (Packet) invocation.getArguments()[0];
				XMPPConnection conn = Mockito.spy(client.getConnection());
				Mockito.doAnswer(createAnswer(from, 0)).when(
						conn).sendPacket(Mockito.any(Packet.class));
				return SyncPacketSend.getReply(conn, p);
			}
		}).when(client).syncSend(Mockito.any(Packet.class));
	}

	private Answer<Void> createAnswer(final String from, final int packetArgIdx) {
		return new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				Packet p = (Packet) invocation.getArguments()[packetArgIdx];
				p.setFrom(from);
				send(p);
				return null;
			}
		};
	}
	
	public void connect(XMPPComponent component) throws Exception {
		String jid = getField(XMPPComponent.class, component, "jid");
		connect(jid, component);
		ExternalComponentManager manager = Mockito.mock(ExternalComponentManager.class);
		component.initialize(new JID(jid), manager);
		Mockito.doAnswer(createAnswer(jid, 1)).when(manager).sendPacket(
				Mockito.any(Component.class), Mockito.any(Packet.class));
	}
	
	public void disconnect(String jid) {
		onlineEntities.remove(jid);
	}
}
