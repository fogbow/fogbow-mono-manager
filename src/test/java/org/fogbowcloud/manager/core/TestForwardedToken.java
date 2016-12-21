package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.HashMap;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
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
		
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		order1.setState(OrderState.OPEN);
        
		final String userId = "user_id";
		final String username = "user";
		ManagerPacketHelper.asynchronousRemoteOrder(order1.getId(), order1.getCategories(), 
				order1.getxOCCIAtt(), "member1", new Token("accessId", new Token.User(userId, username), 
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
				Element userEl = tokenEl.element("user");
				return userEl.elementText(ManagerPacketHelper.ID_EL).equals(userId) &&
						userEl.elementText(ManagerPacketHelper.NAME_EL).equals(username) &&
						tokenEl.elementText("accessId").equals("accessId");
			}
		}));
	}
	
	@Test
	public void testTokenNotBeingForwarded() {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		order1.setState(OrderState.OPEN);
        
		ManagerPacketHelper.asynchronousRemoteOrder(order1.getId(), order1.getCategories(), 
				order1.getxOCCIAtt(), "member1", null, packetSender, null);
		
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
