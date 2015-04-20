package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.HashMap;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.xmpp.packet.IQ;

public class TestForwardedToken {

	private ManagerTestHelper managerTestHelper;

	@Before
	public void setUp() throws Exception {
		this.managerTestHelper = new ManagerTestHelper();
	}
	
	@Test
	public void testTokenBeingForwarded() {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), 
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		request1.setState(RequestState.OPEN);
        
		ManagerPacketHelper.asynchronousRemoteRequest(request1, "member1", new Token("accessId", "user", 
				null, null), packetSender, null);
		
		Mockito.verify(packetSender).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
			@Override
			public boolean matches(Object argument) {
				IQ iq = (IQ) argument;
				Element queryEl = iq.getElement().element("query");
				if (queryEl == null) {
					return false;
				}
				Element tokenEl = queryEl.element("token");
				return tokenEl.elementText("user").equals("user") && 
						tokenEl.elementText("accessId").equals("accessId");
			}
		}));
	}
	
	@Test
	public void testTokenNotBeingForwarded() {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), 
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		request1.setState(RequestState.OPEN);
        
		ManagerPacketHelper.asynchronousRemoteRequest(request1, "member1", null, packetSender, null);
		
		Mockito.verify(packetSender).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
			@Override
			public boolean matches(Object argument) {
				IQ iq = (IQ) argument;
				Element queryEl = iq.getElement().element("query");
				Element tokenEl = queryEl.element("token");
				return tokenEl == null;
			}
		}));
	}
	
}
