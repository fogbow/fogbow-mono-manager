package org.fogbowcloud.manager.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.MessagingException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController.FailedBatchType;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenStackOCCIComputePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.ManagerDataStore;
import org.fogbowcloud.manager.occi.ManagerDataStoreController;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.model.Token.User;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.order.OrderType;
import org.fogbowcloud.manager.occi.storage.StorageLink;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.jamppa.component.PacketCallback;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketError.Condition;

public class TestManagerController {

	public static final String ACCESS_TOKEN_ID_2 = "secondAccessToken";

	private ManagerController managerController;
	private ManagerTestHelper managerTestHelper;
	private Map<String, String> xOCCIAtt;

	@Before
	public void setUp() throws Exception {
		this.managerTestHelper = new ManagerTestHelper();
		
		/*
		 * Default manager controller: 
		 *  computePlugin.requestInstance always throws QuotaExceededException 
		 *  identityPlugin.getToken(AccessId) always returns DefaultDataTestHelper.ACCESS_TOKEN_ID
		 *  schedulerPeriod and monitoringPeriod are long enough (a day) to avoid reeschudeling
		 */

		this.managerController = managerTestHelper.createDefaultManagerController();
		// default instance count value is 1
		this.xOCCIAtt = new HashMap<String, String>();
		this.xOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(OrderConstants.DEFAULT_INSTANCE_COUNT));
		this.xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		
		TestDataStorageHelper.clearManagerDataStore(this.managerController
				.getManagerDataStoreController().getManagerDatabase());
	}
	
	@After
	public void tearDown() {
		TestDataStorageHelper.clearManagerDataStore(this.managerController
				.getManagerDataStoreController().getManagerDatabase());
	}

	@Test
	public void testInitializeWithErrorDataStore() {
		try {
			Properties properties = new Properties();
			// to force error with "/dev/null"
			properties.put(ManagerDataStore.MANAGER_DATASTORE_URL, "/dev/null");
			new ManagerController(properties);
			Assert.fail();
		} catch (Error e) {
			Assert.assertEquals(ManagerDataStore.ERROR_WHILE_INITIALIZING_THE_DATA_STORE, 
					e.getMessage());
		}
	}
	
	@Test
	public void testAuthorizedUser() {		
		Token tokenFromFederationIdP = managerController
				.getTokenFromFederationIdP(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		
		Assert.assertEquals(managerTestHelper.getDefaultFederationToken().getAccessId(),
				tokenFromFederationIdP.getAccessId());
	}
	
	@Test(expected=OCCIException.class)
	public void testUnauthorizedUser() {
		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(false);
		managerController.setAuthorizationPlugin(authorizationPlugin);
		
		managerController.getTokenFromFederationIdP(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);		
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitFederationUserOrders() throws InterruptedException {
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class),
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString()))
				.thenReturn("newinstanceid")
				.thenReturn("newinstanceid");
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourcesInfo);
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		FederationMember federationMember = new FederationMember(resourcesInfo);
		listMembers.add(federationMember);
		managerController.updateMembers(listMembers);

		checkOrderPerUserToken(managerTestHelper.getDefaultFederationToken());
	}
	
	private void checkOrderPerUserToken(Token token) {
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		Order orderOne = new Order("id1", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.OPEN);
		Order orderTwo = new Order("id2", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderTwo.setState(OrderState.OPEN);
		managerController.getManagerDataStoreController().addOrder(orderOne);
		managerController.getManagerDataStoreController().addOrder(orderTwo);

		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(token.getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.FULFILLED, order.getState());
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitToGreenSitterWithNoRequirements() {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		FederationMemberPickerPlugin memberPickerPlugin = Mockito.mock(FederationMemberPickerPlugin.class);
		Mockito.when(memberPickerPlugin.pick(Mockito.any(List.class)))
				.thenReturn(null);
		managerController.setMemberPickerPlugin(memberPickerPlugin);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);

		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NO_VALID_HOST_FOUND, ""));
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				localResourcesInfo);
		
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		managerController.updateMembers(listMembers);
		
		HashMap<String, String> attributes = new HashMap<String, String>(xOCCIAtt);
		attributes.put(OrderAttribute.REQUIREMENTS.getValue(), "true");
		Order orderOne = new Order("id1", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				attributes, true, "");
		orderOne.setState(OrderState.OPEN);
		this.managerController.getManagerDataStoreController().addOrder(orderOne);
		managerController.checkAndSubmitOpenOrders();
		        
		Mockito.verify(packetSender, VerificationModeFactory.times(1)).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
			@Override
			public boolean matches(Object argument) {
				IQ iq = (IQ) argument;
				Element queryEl = iq.getElement().element("query");
				if (queryEl == null) {
					return false;
				}
				String minCPU = queryEl.elementText("minCPU");
				String minRAM = queryEl.elementText("minRAM");
				
				if ((!minCPU.equals("0")) || (!minRAM.equals("0"))){
					return false;
				}
				return iq.getTo().toBareJID().equals("green.server.com");
			}
		}));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitToGreenSitterWithRequirements() {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		FederationMemberPickerPlugin memberPickerPlugin = Mockito.mock(FederationMemberPickerPlugin.class);
		Mockito.when(memberPickerPlugin.pick(Mockito.any(List.class)))
				.thenReturn(null);
		managerController.setMemberPickerPlugin(memberPickerPlugin);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);

		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NO_VALID_HOST_FOUND, ""));
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				localResourcesInfo);
		
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		managerController.updateMembers(listMembers);
		
		HashMap<String, String> attributes = new HashMap<String, String>(xOCCIAtt);
		attributes.put(OrderAttribute.REQUIREMENTS.getValue(), "Glue2vCPU >= 1 && Glue2RAM >= 1024");
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				attributes, true, "");
		order1.setState(OrderState.OPEN);
		managerController.getManagerDataStoreController().addOrder(order1);
		managerController.checkAndSubmitOpenOrders();
		        
		Mockito.verify(packetSender, VerificationModeFactory.times(1)).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
			@Override
			public boolean matches(Object argument) {
				IQ iq = (IQ) argument;
				Element queryEl = iq.getElement().element("query");
				if (queryEl == null) {
					return false;
				}
				String minCPU = queryEl.elementText("minCPU");
				String minRAM = queryEl.elementText("minRAM");
				
				if ((!minCPU.equals("1")) || (!minRAM.equals("1024"))){
					return false;
				}
				return iq.getTo().toBareJID().equals("green.server.com");
			}
		}));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRemoveLocalAndRemoteOrders() {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		// mocking getRemoteInstance for running benchmarking
		IQ response = new IQ(); 
		response.setType(Type.result);
		Element queryEl = response.getElement().addElement("query", 
				ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		instanceEl.addElement("state").setText(InstanceState.RUNNING.toString());
		
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);

		final List<PacketCallback> callbacks = new LinkedList<PacketCallback>();
		
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				callbacks.add((PacketCallback) invocation.getArguments()[1]);
				return null;
			}
		}).when(packetSender).addPacketCallback(Mockito.any(Packet.class), Mockito.any(PacketCallback.class));
		
		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfoOne = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfoOne.setId(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfoTwo = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfoTwo.setId(DefaultDataTestHelper.REMOTE_MANAGER_TWO_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfoThree = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfoThree.setId(DefaultDataTestHelper.REMOTE_MANAGER_THREE_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));

		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				localResourcesInfo);
		managerController.setComputePlugin(computePlugin);

		final List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		listMembers.add(new FederationMember(remoteResourcesInfoOne));
		listMembers.add(new FederationMember(remoteResourcesInfoTwo));
		listMembers.add(new FederationMember(remoteResourcesInfoThree));
		managerController.updateMembers(listMembers);
		
		FederationMemberPickerPlugin memberPicker = Mockito.mock(FederationMemberPickerPlugin.class);
		Mockito.when(memberPicker.pick(Mockito.anyList())).thenReturn(
				new FederationMember(remoteResourcesInfoOne), new FederationMember(remoteResourcesInfoTwo),
				new FederationMember(remoteResourcesInfoThree));
		managerController.setMemberPickerPlugin(memberPicker );

		final String orderId = "orderId";
		Order orderOne = new Order(orderId, managerTestHelper.getDefaultFederationToken(),
				new ArrayList<Category>(),
				new HashMap<String, String>(), true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		orderOne.setState(OrderState.OPEN);
		managerController.getManagerDataStoreController().addOrder(orderOne);

		// mocking date
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);
		
		// Send to first member
		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				now + ManagerController.DEFAULT_ASYNC_ORDER_WAITING_INTERVAL + 100);		
		
		// Timeout expired
		managerController.checkPedingOrders();		

		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.OPEN, order.getState());
			Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));
		
		// mocking date
		now = System.currentTimeMillis();
		dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);
		
		// Send to second member
		managerController.checkAndSubmitOpenOrders();

		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));		

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				now + ManagerController.DEFAULT_ASYNC_ORDER_WAITING_INTERVAL + 100);		
		
		// Timeout expired
		managerController.checkPedingOrders();		

		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.OPEN, order.getState());
			Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));
		
		// Send to third member
		managerController.checkAndSubmitOpenOrders();

		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));			
		
		AsyncPacketSender packetSenderTwo = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSenderTwo);				
		
		managerController.removeOrder(managerTestHelper.getDefaultFederationToken().getAccessId(), orderId);
		
		Mockito.verify(packetSenderTwo, VerificationModeFactory.times(3)).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
			@Override
			public boolean matches(Object argument) {
				IQ iq = (IQ) argument;
				Element queryEl = iq.getElement().element("query");
				if (queryEl == null) {
					return false;
				}
				String orderId = queryEl.element(ManagerPacketHelper.ORDER_EL).elementText("id");
				String accessId = queryEl.element("token").elementText("accessId");			
				
				if (orderId.equals(orderId) 
						&& iq.getTo().toBareJID().equals(DefaultDataTestHelper.REMOTE_MANAGER_TWO_COMPONENT_URL)
						&& accessId.equals(managerTestHelper.getDefaultFederationToken().getAccessId())) {
					return true;
				}
				
				if (orderId.equals(orderId) 
						&& iq.getTo().toBareJID().equals(DefaultDataTestHelper.REMOTE_MANAGER_THREE_COMPONENT_URL)
						&& accessId.equals(managerTestHelper.getDefaultFederationToken().getAccessId())) {
					return true;
				}
				
				if (orderId.equals(orderId) 
						&& iq.getTo().toBareJID().equals(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL)
						&& accessId.equals(managerTestHelper.getDefaultFederationToken().getAccessId())) {
					return true;
				}				
				
				return false;
			}
		}));			
		
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitOrderToRemoteMemberAndRemovingAllRequestsInOthersMembers() throws InterruptedException {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		// mocking getRemoteInstance for running benchmarking
		IQ response = new IQ(); 
		response.setType(Type.result);
		Element queryEl = response.getElement().addElement("query", 
				ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		instanceEl.addElement("state").setText(InstanceState.RUNNING.toString());
		
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);

		final List<PacketCallback> callbacks = new LinkedList<PacketCallback>();
		
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				callbacks.add((PacketCallback) invocation.getArguments()[1]);
				return null;
			}
		}).when(packetSender).addPacketCallback(Mockito.any(Packet.class), Mockito.any(PacketCallback.class));
		
		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfoOne = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfoOne.setId(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfoTwo = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfoTwo.setId(DefaultDataTestHelper.REMOTE_MANAGER_TWO_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfoThree = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfoThree.setId(DefaultDataTestHelper.REMOTE_MANAGER_THREE_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));

		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				localResourcesInfo);
		managerController.setComputePlugin(computePlugin);

		final List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		listMembers.add(new FederationMember(remoteResourcesInfoOne));
		listMembers.add(new FederationMember(remoteResourcesInfoTwo));
		listMembers.add(new FederationMember(remoteResourcesInfoThree));
		managerController.updateMembers(listMembers);
		
		FederationMemberPickerPlugin memberPicker = Mockito.mock(FederationMemberPickerPlugin.class);
		Mockito.when(memberPicker.pick(Mockito.anyList())).thenReturn(
				new FederationMember(remoteResourcesInfoOne), new FederationMember(remoteResourcesInfoTwo),
				new FederationMember(remoteResourcesInfoThree));
		managerController.setMemberPickerPlugin(memberPicker );

		final String orderId = "orderId";
		Order orderOne = new Order(orderId, managerTestHelper.getDefaultFederationToken(),
				new ArrayList<Category>(),
				new HashMap<String, String>(), true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		orderOne.setState(OrderState.OPEN);
		managerController.getManagerDataStoreController().addOrder(orderOne);

		// mocking date
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);
		
		// Send to first member
		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				now + ManagerController.DEFAULT_ASYNC_ORDER_WAITING_INTERVAL + 100);		
		
		// Timeout expired
		managerController.checkPedingOrders();		
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.OPEN, order.getState());
			Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));
		
		// mocking date
		now = System.currentTimeMillis();
		dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);
		
		// Send to second member
		managerController.checkAndSubmitOpenOrders();

		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));		

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				now + ManagerController.DEFAULT_ASYNC_ORDER_WAITING_INTERVAL + 100);		
		
		// Timeout expired
		managerController.checkPedingOrders();		
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.OPEN, order.getState());
			Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));
		
		// Send to third member
		managerController.checkAndSubmitOpenOrders();

		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));			
		
		AsyncPacketSender packetSenderTwo = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSenderTwo);
		
		IQ iq = new IQ();
		queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.ORDER_NAMESPACE);
		instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		callbacks.get(0).handle(iq);
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.FULFILLED, order.getState());
			Assert.assertFalse(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
		
		Mockito.verify(packetSenderTwo, VerificationModeFactory.times(2)).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
			@Override
			public boolean matches(Object argument) {
				IQ iq = (IQ) argument;
				Element queryEl = iq.getElement().element("query");
				if (queryEl == null) {
					return false;
				}
				String orderId = queryEl.element(ManagerPacketHelper.ORDER_EL).elementText("id");
				String accessId = queryEl.element("token").elementText("accessId");			
				
				if (orderId.equals(orderId) 
						&& iq.getTo().toBareJID().equals(DefaultDataTestHelper.REMOTE_MANAGER_TWO_COMPONENT_URL)
						&& accessId.equals(managerTestHelper.getDefaultFederationToken().getAccessId())) {
					return true;
				}
				
				if (orderId.equals(orderId) 
						&& iq.getTo().toBareJID().equals(DefaultDataTestHelper.REMOTE_MANAGER_THREE_COMPONENT_URL)
						&& accessId.equals(managerTestHelper.getDefaultFederationToken().getAccessId())) {
					return true;
				}
				
				return false;
			}
		}));		
		
	}	
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitOrderToRemoteMember() throws InterruptedException {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		FederationMemberPickerPlugin memberPicker = Mockito.mock(FederationMemberPickerPlugin.class);
		Mockito.when(memberPicker.pick(Mockito.anyList())).thenReturn(new FederationMember("fedMember"));
		managerController.setMemberPickerPlugin(memberPicker);

		// mocking getRemoteInstance for running benchmarking
		IQ response = new IQ(); 
		response.setType(Type.result);
		Element queryEl = response.getElement().addElement("query", 
				ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		instanceEl.addElement("state").setText(InstanceState.RUNNING.toString());
		
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);

		final List<PacketCallback> callbacks = new LinkedList<PacketCallback>();
		
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				callbacks.add((PacketCallback) invocation.getArguments()[1]);
				return null;
			}
		}).when(packetSender).addPacketCallback(Mockito.any(Packet.class), Mockito.any(PacketCallback.class));
		
		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfo.setId(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));

		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				localResourcesInfo);
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		listMembers.add(new FederationMember(remoteResourcesInfo));
		managerController.updateMembers(listMembers);

		Order orderOne = new Order("id1", managerTestHelper.getDefaultFederationToken(),
				new ArrayList<Category>(),
				new HashMap<String, String>(), true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		orderOne.setState(OrderState.OPEN);
		managerController.getManagerDataStoreController().addOrder(orderOne);

		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));

		IQ iq = new IQ();
		queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.ORDER_NAMESPACE);
		instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		callbacks.get(0).handle(iq);
	
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.FULFILLED, order.getState());
			Assert.assertFalse(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRemoveForwardedOrderAfterTimeout() throws InterruptedException {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfo.setId(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(), 
						Mockito.anyMap(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""));
				
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(localResourcesInfo);
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		listMembers.add(new FederationMember(remoteResourcesInfo));
		managerController.updateMembers(listMembers);

		Order orderOne = new Order("id1", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				this.xOCCIAtt, true, "");
		orderOne.setState(OrderState.OPEN);
		this.managerController.getManagerDataStoreController().addOrder(orderOne);

		// mocking date
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);

		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(
				managerTestHelper.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(
				orderOne.getId()));
		
		//updating time
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				now + ManagerController.DEFAULT_ASYNC_ORDER_WAITING_INTERVAL + 100);
		
		managerController.checkPedingOrders();
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.OPEN, order.getState());
			Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
	}
		
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitOrderToRemoteMemberReturningNotFound() throws InterruptedException {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);

		final List<PacketCallback> callbacks = new LinkedList<PacketCallback>();
		
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				callbacks.add((PacketCallback) invocation.getArguments()[1]);
				return null;
			}
		}).when(packetSender).addPacketCallback(Mockito.any(Packet.class), Mockito.any(PacketCallback.class));
		
		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfo.setId(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));

		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				localResourcesInfo);
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		listMembers.add(new FederationMember(remoteResourcesInfo));
		managerController.updateMembers(listMembers);

		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(),
				new ArrayList<Category>(), new HashMap<String, String>(), true, "");
		order1.setState(OrderState.OPEN);
		managerController.getManagerDataStoreController().addOrder(order1);

		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(
				order1.getId()));
		
		IQ iq = new IQ();
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.ORDER_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		iq.setError(Condition.item_not_found);
		callbacks.get(0).handle(iq);
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.OPEN, order.getState());
			Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitOrderToRemoteMemberReturningException() throws InterruptedException {
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);

		final List<PacketCallback> callbacks = new LinkedList<PacketCallback>();
		
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				callbacks.add((PacketCallback) invocation.getArguments()[1]);
				return null;
			}
		}).when(packetSender).addPacketCallback(Mockito.any(Packet.class), Mockito.any(PacketCallback.class));
		
		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfo.setId(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(), 
						Mockito.anyMap(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""));
				
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(localResourcesInfo);
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		listMembers.add(new FederationMember(remoteResourcesInfo));
		managerController.updateMembers(listMembers);

		IdentityPlugin identityPlugin = managerTestHelper.getIdentityPlugin();
		IdentityPlugin federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(
				federationIdentityPlugin.getToken(managerTestHelper.getDefaultFederationToken()
						.getAccessId())).thenReturn(managerTestHelper.getDefaultFederationToken());
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(
				managerTestHelper.getDefaultFederationToken());
		managerController.setLocalIdentityPlugin(identityPlugin);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);

		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(),
				new ArrayList<Category>(), new HashMap<String, String>(), true, "");
		order1.setState(OrderState.OPEN);
		managerController.getManagerDataStoreController().addOrder(order1);

		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(
				managerTestHelper.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(
				order1.getId()));
		
		IQ iq = new IQ();
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.ORDER_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		iq.setError(Condition.bad_request);
		callbacks.get(0).handle(iq);
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.OPEN, order.getState());
			Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
	}
	
	@Test
	public void testDeleteClosedOrder() throws InterruptedException {
		// setting order repository
		Order orderOne = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, null, true, "");
		orderOne.setState(OrderState.CLOSED);
		Order orderTwo = new Order("id2", managerTestHelper.getDefaultFederationToken(), null, null, true, "");
		orderTwo.setState(OrderState.CLOSED);

		managerController.getManagerDataStoreController().addOrder(orderOne);
		managerController.getManagerDataStoreController().addOrder(orderTwo);

		// checking closed orders
		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		Assert.assertEquals(2, ordersFromUser.size());
		Assert.assertEquals(OrderState.CLOSED, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.CLOSED, ordersFromUser.get(1).getState());

		managerController.removeOrder(managerTestHelper.getDefaultFederationToken().getAccessId(), "id1");

		// making sure one order was removed
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		Assert.assertEquals(1, ordersFromUser.size());
		Assert.assertEquals(OrderState.CLOSED, ordersFromUser.get(0).getState());
		Assert.assertEquals("id2", ordersFromUser.get(0).getId());
		
		managerController.removeOrder(managerTestHelper.getDefaultFederationToken().getAccessId(), "id2");

		// making sure the last order was removed
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		Assert.assertEquals(0, ordersFromUser.size());
	}

	@Test
	public void testMonitorDeletedOrderWithInstance() throws InterruptedException {
		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order1.setState(OrderState.DELETED);
		order1.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		Order order2 = new Order("id2", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order2.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order2.setState(OrderState.DELETED);
		order2.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		managerController.getManagerDataStoreController().addOrder(order1);
		managerController.getManagerDataStoreController().addOrder(order2);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenReturn(
				new Instance(DefaultDataTestHelper.INSTANCE_ID));

		// checking deleted orders
		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		Assert.assertEquals(2, ordersFromUser.size());
		Assert.assertEquals(OrderState.DELETED, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.DELETED, ordersFromUser.get(1).getState());

		managerController.monitorInstancesForLocalOrders();

		// making sure the orders were not removed
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		Assert.assertEquals(2, ordersFromUser.size());
		Assert.assertEquals(OrderState.DELETED, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.DELETED, ordersFromUser.get(1).getState());
	}

	@Test
	public void testMonitorDeletedOrderWithoutInstance() throws InterruptedException {
		// setting order repository
		Order order1 = new Order("id1",
				managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order1.setState(OrderState.DELETED);
		Order order2 = new Order("id2",
				managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order2.setState(OrderState.DELETED);
		Order order3 = new Order("id3",
				managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order3.setState(OrderState.OPEN);

		managerController.getManagerDataStoreController().addOrder(order1);
		managerController.getManagerDataStoreController().addOrder(order2);
		managerController.getManagerDataStoreController().addOrder(order3);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if orders still have the initial state
		Assert.assertEquals(
				3,
				managerController.getOrdersFromUser(
						managerTestHelper.getDefaultFederationToken().getAccessId()).size());
		Assert.assertEquals(
				OrderState.DELETED,
				managerController.getOrder(managerTestHelper.getDefaultFederationToken().getAccessId(),
						"id1").getState());
		Assert.assertEquals(
				OrderState.DELETED,
				managerController.getOrder(managerTestHelper.getDefaultFederationToken().getAccessId(),
						"id2").getState());
		Assert.assertEquals(
				OrderState.OPEN,
				managerController.getOrder(managerTestHelper.getDefaultFederationToken().getAccessId(),
						"id3").getState());

		managerController.monitorInstancesForLocalOrders();

		// checking if deleted orders were removed
		Assert.assertEquals(
				1,
				managerController.getOrdersFromUser(
						managerTestHelper.getDefaultFederationToken().getAccessId()).size());
		Assert.assertEquals(
				OrderState.OPEN,
				managerController
						.getOrdersFromUser(managerTestHelper.getDefaultFederationToken().getAccessId())
						.get(0).getState());
	}

	@Test
	public void testMonitorFulfilledOrderWithoutInstance() throws InterruptedException {
		// setting orders repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order1.setState(OrderState.FULFILLED);
		Order order2 = new Order("id2", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order2.setState(OrderState.FULFILLED);

		managerController.getManagerDataStoreController().addOrder(order1);
		managerController.getManagerDataStoreController().addOrder(order2);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if orders were fulfilled
		List<Order> ordersFromUser = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(2, ordersFromUser.size());
		for (Order order : ordersFromUser) {
			Assert.assertTrue(order.getState().equals(OrderState.FULFILLED));
		}

		managerController.monitorInstancesForLocalOrders();

		// checking if orders were closed
		ordersFromUser = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(2, ordersFromUser.size());
		for (Order order : ordersFromUser) {
			Assert.assertTrue(order.getState().equals(OrderState.CLOSED));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMonitorFulfilledAndPersistentOrder() throws InterruptedException {
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(OrderAttribute.TYPE.getValue(), OrderType.PERSISTENT.getValue());
		attributes.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);

		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), 
				new ArrayList<Category>(), attributes, true, "");
		order1.setState(OrderState.FULFILLED);

		managerController.getManagerDataStoreController().addOrder(order1);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if order is fulfilled
		List<Order> ordersFromUser = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, ordersFromUser.size());
		Assert.assertEquals(OrderState.FULFILLED, ordersFromUser.get(0).getState());

		managerController.monitorInstancesForLocalOrders();

		// checking if order has lost its instance
		ordersFromUser = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, ordersFromUser.size());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(0).getState());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenReturn(
				DefaultDataTestHelper.INSTANCE_ID);
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.eq(DefaultDataTestHelper.INSTANCE_ID))).thenReturn(
				new Instance(DefaultDataTestHelper.INSTANCE_ID));

		// getting instance for order
		managerController.checkAndSubmitOpenOrders();

		// checking if order has been fulfilled again
		ordersFromUser = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, ordersFromUser.size());
		Assert.assertEquals(OrderState.FULFILLED, ordersFromUser.get(0).getState());
	}

	@Test
	public void testMonitorFulfilledOrderWithInstance() throws InterruptedException {
		final String SECOND_INSTANCE_ID = "secondInstanceId";

		// setting order repository
		HashMap<String, String> xOCCIAttr = new HashMap<String, String>();
		xOCCIAttr.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order1.setState(OrderState.FULFILLED);
		order1.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		Order order2 = new Order("id2", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order2.setInstanceId(SECOND_INSTANCE_ID);
		order2.setState(OrderState.FULFILLED);
		order2.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		managerController.getManagerDataStoreController().addOrder(order1);
		managerController.getManagerDataStoreController().addOrder(order2);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenReturn(
				new Instance(DefaultDataTestHelper.INSTANCE_ID));

		managerController.monitorInstancesForLocalOrders();

		// checking if orders are still fulfilled
		List<Order> ordersFromUser = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(2, ordersFromUser.size());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.FULFILLED, order.getState());
		}

		managerController.monitorInstancesForLocalOrders();

		// checking if orders' state haven't been changed
		ordersFromUser = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(2, ordersFromUser.size());
		for (Order order : ordersFromUser) {
			Assert.assertTrue(order.getState().equals(OrderState.FULFILLED));
		}
	}
	
	@Test
	public void testMonitorWontRethrowException() throws InterruptedException {
		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order1.setState(OrderState.FULFILLED);

		managerController.getManagerDataStoreController().addOrder(order1);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(new RuntimeException());

		managerController.monitorInstancesForLocalOrders();
	}
	
	@Test
	public void testMonitorWillRemoveLocalFailedInstance() throws InterruptedException {
		// setting order repository
		HashMap<String, String> xOCCIAttr = new HashMap<String, String>();
		xOCCIAttr.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order1.setState(OrderState.FULFILLED);
		order1.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		managerController.getManagerDataStoreController().addOrder(order1);

		// updating compute mock
		Instance expectedInstance = new Instance(DefaultDataTestHelper.INSTANCE_ID, new LinkedList<Resource>(), 
				new HashMap<String, String>(), new LinkedList<Link>(), InstanceState.FAILED);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenReturn(expectedInstance);

		managerController.monitorInstancesForLocalOrders();
		
		// checking if instance was properly removed
		Mockito.verify(managerTestHelper.getComputePlugin()).removeInstance(
				Mockito.any(Token.class), Mockito.eq(DefaultDataTestHelper.INSTANCE_ID));
		
		// checking if order is closed
		List<Order> ordersFromUser = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, ordersFromUser.size());
		for (Order order : ordersFromUser) {
			Assert.assertTrue(order.getState().equals(OrderState.CLOSED));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorException() throws Exception {
		new ManagerController(null);
	}

	@Test
	public void testGet1ItemsFromRendezvous() {
		managerController.updateMembers(new LinkedList<FederationMember>());
		// There is a single member which is the manager itself
		Assert.assertEquals(1, managerController.getRendezvousMembers().size());
	}

	@Test
	public void testGet2ItemFromRendezvous() throws CertificateException, IOException {
		String id = "abc";	
		managerController.updateMembers(Arrays.asList(new FederationMember[] {new FederationMember(id)}));
		List<FederationMember> members = managerController.getRendezvousMembers();
		Assert.assertEquals(2, members.size());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
				members.get(members.size() - 1).getId());
		Assert.assertEquals(id, members.get(0).getId());
	}
	

	private AsyncPacketSender mockGetRemoteResourceInfo(List<FederationMember> members) {
		// mocking packet sender
		List<IQ> responses = new ArrayList<IQ>();

		for (FederationMember member : members) {
			ResourcesInfo resourcesInfo = member.getResourcesInfo();
			IQ iq = new IQ();
			iq.setTo(resourcesInfo.getId());
			iq.setType(Type.get);
			Element queryEl = iq.getElement().addElement("query",
					ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
			Element tokenEl = queryEl.addElement("token");
			tokenEl.addElement("accessId").setText("x_federation_auth_token");
			tokenEl.addElement("user").setText("user");

			IQ response = IQ.createResultIQ(iq);
			queryEl = response.getElement().addElement("query",
					ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
			Element resourceEl = queryEl.addElement("resourcesInfo");

			resourceEl.addElement("id").setText(resourcesInfo.getId());
			resourceEl.addElement("cpuIdle").setText(resourcesInfo.getCpuIdle());
			resourceEl.addElement("cpuInUse").setText(resourcesInfo.getCpuInUse());
			resourceEl.addElement("instancesIdle").setText(resourcesInfo.getInstancesIdle());
			resourceEl.addElement("instancesInUse").setText(resourcesInfo.getInstancesInUse());
			resourceEl.addElement("memIdle").setText(resourcesInfo.getMemIdle());
			resourceEl.addElement("memInUse").setText(resourcesInfo.getMemInUse());
			responses.add(response);
		}

		IQ firstResponse = responses.remove(0);
		IQ[] nextResponses = new IQ[responses.size()];
		responses.toArray(nextResponses);
		
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(firstResponse,
				nextResponses);
		return packetSender;
	}
	
	@Test
	public void testGetManyItemsFromIQ() throws CertificateException, IOException {
		ArrayList<FederationMember> items = new ArrayList<FederationMember>();
		for (int i = 0; i < 10; i++) {
			items.add(new FederationMember(managerTestHelper.getResources()));
		}
		
		AsyncPacketSender packetSender = mockGetRemoteResourceInfo(items);
		managerController.setPacketSender(packetSender);
		managerController.updateMembers(items);

		List<FederationMember> members = managerController.getRendezvousMembers();
		Assert.assertEquals(11, members.size());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
				members.get(members.size() -1 ).getId());
		for (int i = 0; i < 10; i++) {
			Assert.assertEquals("abc", members.get(i).getId());
		}
	}	

	@Test
	public void testGetOrdersByUser() throws InterruptedException {
		mockOrderInstance();

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenOrders();		

		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);

		// checking if order was created
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(DefaultDataTestHelper.FED_USER_NAME, 
				orders.get(0).getFederationToken().getUser().getId());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());
	}


	@Test
	public void testOneTimeOrderSetFulfilledAndClosed() throws InterruptedException {
		mockOrderInstance();

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenOrders();
		
		// checking if order was properly created
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());

		// updating compute mock
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				managerTestHelper.getDefaultFederationToken(), DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID  + Order.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, OrderConstants.COMPUTE_TERM);

		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);

		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderState.CLOSED, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentOrderSetFulfilledAndOpen() throws InterruptedException {
		mockOrderInstance();
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(),
				String.valueOf(OrderType.PERSISTENT.getValue()));

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenOrders();

		// checking if order was properly created
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				managerTestHelper.getDefaultFederationToken(), DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID + Order.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, OrderConstants.COMPUTE_TERM);

		// checking order state was set to open
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentOrderSetFulfilledAndOpenAndFulfilled() throws InterruptedException {
		final String SECOND_INSTANCE_ID = "rt22e67-5fgt-457a-3rt6-gt78124fhj9p";
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(),
				String.valueOf(OrderType.PERSISTENT.getValue()));

		// mocking compute
		mockOrderInstance();

		// creating orders
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
 		managerController.checkAndSubmitOpenOrders();

		// checking if order was fulfilled with instanceID
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				managerTestHelper.getDefaultFederationToken(), DefaultDataTestHelper.INSTANCE_ID);
		
		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID  + Order.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, OrderConstants.COMPUTE_TERM);

		// checking if order state was set to open
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenReturn(
				SECOND_INSTANCE_ID);

		// getting second instance
		managerController.checkAndSubmitOpenOrders();

		// checking if order was fulfilled with secondInstance
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(SECOND_INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());
	}

	@Test
	public void testPersistentOrderSetOpenAndClosed() throws InterruptedException {
		long expirationOrderTime = System.currentTimeMillis()
				+ DefaultDataTestHelper.SCHEDULER_PERIOD;

		// setting order attributes
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(),
				String.valueOf(OrderType.PERSISTENT.getValue()));
		xOCCIAtt.put(OrderAttribute.VALID_UNTIL.getValue(),
				String.valueOf(DateUtils.getDateISO8601Format(expirationOrderTime)));

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if order is OPEN
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// waiting expiration time
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenOrders();
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);

		// checking if order was closed
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.CLOSED, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentOrderSetFulfilledAndClosed() throws InterruptedException {
		long expirationOrderTime = System.currentTimeMillis()
				+ DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME;

		// setting order attributes
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(),
				String.valueOf(OrderType.PERSISTENT.getValue()));
		xOCCIAtt.put(OrderAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationOrderTime));
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);

		mockOrderInstance();

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD);
		managerController.checkAndSubmitOpenOrders();

		// checking order is fulfilled
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());

		// waiting expiration time
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD);

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				managerTestHelper.getDefaultFederationToken(), DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID  + Order.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, OrderConstants.COMPUTE_TERM);
		managerController.checkAndSubmitOpenOrders();

		// checking if order state was set to closed
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.CLOSED, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	private void mockOrderInstance() {
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenReturn(
				DefaultDataTestHelper.INSTANCE_ID);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(Mockito.anyString())).thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""));
		managerController.setLocalIdentityPlugin(identityPlugin);
	}

	@Test
	public void testOneTimeOrderSetOpenAndClosed() throws InterruptedException {
		long expirationOrderTime = System.currentTimeMillis()
				+ DefaultDataTestHelper.SCHEDULER_PERIOD;

		// setting order attributes
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(), OrderType.ONE_TIME.getValue());
		xOCCIAtt.put(OrderAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationOrderTime));

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if order was properly created
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.ONE_TIME.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// waiting expiration time
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenOrders();
		
		// checking if order state was set to closed
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.ONE_TIME.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.CLOSED, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());
	}

	@Test
	public void testOneTimeOrderWithValidFromAttInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long timeAddedInStartOrderTime = DefaultDataTestHelper.SCHEDULER_PERIOD * 3;
		long startOrderTime = now + timeAddedInStartOrderTime;
		long expirationOrderTime = now + DefaultDataTestHelper.LONG_TIME;

		// setting order attributes
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(),
				String.valueOf(OrderType.ONE_TIME.getValue()));
		xOCCIAtt.put(OrderAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startOrderTime));
		xOCCIAtt.put(OrderAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationOrderTime));

		mockOrderInstance();

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if order was properly created
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.ONE_TIME.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// sleeping for a time and order not valid yet
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenOrders();

		// check that order is not in valid period yet
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.ONE_TIME.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// sleeping for the scheduler period and submitting order
		Thread.sleep(timeAddedInStartOrderTime);
		managerController.checkAndSubmitOpenOrders();

		// check if order is in valid period
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.ONE_TIME.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());
	}

	@Test
	public void testPersistentOrderWithValidFromAttInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long timeAddedInStartOrderTime = DefaultDataTestHelper.SCHEDULER_PERIOD * 3;
		long startOrderTime = now + timeAddedInStartOrderTime;
		long expirationOrderTime = now + DefaultDataTestHelper.LONG_TIME;

		// setting order attributes
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(), OrderType.PERSISTENT.getValue());
		xOCCIAtt.put(OrderAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startOrderTime));
		xOCCIAtt.put(OrderAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationOrderTime));
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);

		mockOrderInstance();

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if order was rightly created
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// sleeping for a time and order not valid yet
		managerController.checkAndSubmitOpenOrders();

		// check order is not in valid period yet
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// sleeping for the scheduler period and submitting order
		Thread.sleep(timeAddedInStartOrderTime);
		managerController.checkAndSubmitOpenOrders();

		// check if order is in valid period
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOneTimeOrderValidityPeriodInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startOrderTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 3);
		long expirationOrderTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 6);

		// setting order attributes
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(), OrderType.ONE_TIME.getValue());
		xOCCIAtt.put(OrderAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startOrderTime));
		xOCCIAtt.put(OrderAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationOrderTime));

		mockOrderInstance();

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// order is not in valid period yet
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.ONE_TIME.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// sleeping for the scheduler period and submitting order
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 3 + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenOrders();
		
		// checking is order is fulfilled
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.ONE_TIME.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				managerTestHelper.getDefaultFederationToken(), DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID  + Order.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, OrderConstants.COMPUTE_TERM);

		// waiting for a time and order is not into valid period anymore
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);

		// checking if order is not in valid period anymore
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.ONE_TIME.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.CLOSED, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentOrderValidityPeriodInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startOrderTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 2);
		long expirationOrderTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 4);

		// setting order attributes
		xOCCIAtt.put(OrderAttribute.TYPE.getValue(), OrderType.PERSISTENT.getValue());
		xOCCIAtt.put(OrderAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startOrderTime));
		xOCCIAtt.put(OrderAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationOrderTime));

		mockOrderInstance();

		// creating order
		managerController.createOrders(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// order is not in valid period yet
		List<Order> orders = managerController
				.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.OPEN, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());

		// waiting for a time and order is into valid period
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 2 + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenOrders();

		// checking is order is fulfilled
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.FULFILLED, orders.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, orders.get(0).getInstanceId());
		Assert.assertNotNull(orders.get(0).getProvidingMemberId());

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing()
				.when(managerTestHelper.getComputePlugin())
				.removeInstance(
						managerTestHelper.getDefaultFederationToken(),
						DefaultDataTestHelper.INSTANCE_ID + Order.SEPARATOR_GLOBAL_ID
								+ DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID  + Order.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, OrderConstants.COMPUTE_TERM);

		// waiting for the scheduler period so that order is not into valid period anymore
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 2 + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenOrders();
		
		// checking if order is not in valid period anymore
		orders = managerController.getOrdersFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(OrderType.PERSISTENT.getValue(),
				orders.get(0).getAttValue(OrderAttribute.TYPE.getValue()));
		Assert.assertEquals(OrderState.CLOSED, orders.get(0).getState());
		Assert.assertNull(orders.get(0).getInstanceId());
		Assert.assertNull(orders.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitOrderForRemoteMemberValidation() {

		FederationMember member = Mockito.mock(FederationMember.class);
		Mockito.doReturn("abc").when(member).getId();
		List<FederationMember> list = new LinkedList<FederationMember>();
		list.add(member);
		managerController.updateMembers(list);

		FederationMemberAuthorizationPlugin validatorMock = Mockito.mock(FederationMemberAuthorizationPlugin.class);
		Mockito.doReturn(true).when(validatorMock).canDonateTo(Mockito.eq(member), Mockito.any(Token.class));
		managerController.setValidator(validatorMock);

		ComputePlugin computePlugin = Mockito.mock(OpenStackOCCIComputePlugin.class);
		Mockito.doReturn("answer").when(computePlugin)
				.requestInstance(Mockito.any(Token.class), Mockito.anyList(), Mockito.anyMap(), Mockito.anyString());

		managerController.setComputePlugin(computePlugin);

		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		Token token = new Token("accessId", new Token.User("", ""), new Date(), null);
		Order order = new Order("id1", token, new ArrayList<Category>(), xOCCIAtt, false, "abc");
		
		Assert.assertTrue(managerController.createLocalInstanceWithFederationUser(order));

		Mockito.doReturn(false).when(validatorMock).canDonateTo(Mockito.eq(member), Mockito.any(Token.class));
		managerController.setValidator(validatorMock);
		
		Assert.assertFalse(managerController.createLocalInstanceWithFederationUser(order));
	}
		
	@SuppressWarnings("unchecked")
	@Test
	public void testReplyToServedOrderWithSuccess() {
		FederationMember member = Mockito.mock(FederationMember.class);
		Mockito.doReturn("abc").when(member).getId();
		List<FederationMember> list = new LinkedList<FederationMember>();
		list.add(member);
		managerController.updateMembers(list);

		FederationMemberAuthorizationPlugin validatorMock = Mockito.mock(FederationMemberAuthorizationPlugin.class);
		Mockito.doReturn(true).when(validatorMock).canDonateTo(Mockito.eq(member), Mockito.any(Token.class));
		managerController.setValidator(validatorMock);

		ComputePlugin computePlugin = Mockito.mock(OpenStackOCCIComputePlugin.class);
		Mockito.doReturn("answer").when(computePlugin)
				.requestInstance(Mockito.any(Token.class), Mockito.anyList(), Mockito.anyMap(), Mockito.anyString());

		managerController.setComputePlugin(computePlugin);

		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		Token token = new Token("accessId", new Token.User("", ""), new Date(), null);
		Order order = new Order("id1", token, new ArrayList<Category>(), xOCCIAtt,
				false, "abc");
		
		Assert.assertTrue(managerController.createLocalInstanceWithFederationUser(order));

		Mockito.verify(packetSender).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
			@Override
			public boolean matches(Object argument) {
				IQ iq = (IQ) argument;
				if (!"id1".equals(iq.getID())) {
					return false;
				}				
				String instanceId = iq.getElement().element("query").element("instance").elementText("id");
				if (!"answer".equals(instanceId)){
					return false;
				}
				return true;
			}
		}));		
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testReplyToServedOrderWithoutSuccess() {
		FederationMember member = Mockito.mock(FederationMember.class);
		Mockito.doReturn("abc").when(member).getId();
		List<FederationMember> list = new LinkedList<FederationMember>();
		list.add(member);
		managerController.updateMembers(list);

		FederationMemberAuthorizationPlugin validatorMock = Mockito.mock(FederationMemberAuthorizationPlugin.class);
		Mockito.doReturn(true).when(validatorMock).canDonateTo(Mockito.eq(member), Mockito.any(Token.class));
		managerController.setValidator(validatorMock);

		ComputePlugin computePlugin = Mockito.mock(OpenStackOCCIComputePlugin.class);
		Mockito.doReturn(null).when(computePlugin)
				.requestInstance(Mockito.any(Token.class), Mockito.anyList(), Mockito.anyMap(), Mockito.anyString());

		managerController.setComputePlugin(computePlugin);

		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		Token token = new Token("accessId", new Token.User("", ""), new Date(), null);
		Order order = new Order("id1", token, new ArrayList<Category>(), xOCCIAtt,false, "abc");
		
		Assert.assertFalse(managerController.createLocalInstanceWithFederationUser(order));

		Mockito.verify(packetSender).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
			@Override
			public boolean matches(Object argument) {
				IQ iq = (IQ) argument;
				if (!"id1".equals(iq.getID())) {
					return false;
				}				
				if (!iq.getError().getCondition().equals(Condition.item_not_found)){
					return false;
				}				
				return true;
			}
		}));		
	}
	
	@Test
	public void testRemoveAllOpenOrders() {
		
		managerTestHelper.useSameThreadExecutor();
		
		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order1.setState(OrderState.OPEN);
		Order order2 = new Order("id2", managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order2.setState(OrderState.OPEN);

		managerController.getManagerDataStoreController().addOrder(order1);
		managerController.getManagerDataStoreController().addOrder(order2);

		// checking open orders
		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		Assert.assertEquals(2, ordersFromUser.size());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(1).getState());

		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		// removing all orders
		managerController.removeAllOrders(managerTestHelper.getDefaultFederationToken().getAccessId());
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		
		Assert.assertEquals(0, ordersFromUser.size());
	}
	
	@Test
	public void testRemoveOneOpenOrderAndAfterThatRemoveAllOpenOrders() {
		
		managerTestHelper.useSameThreadExecutor();
		
		// setting order repository
		String id1 = "id1";
		String id2 = "id2";
		String id3 = "id3";
		Order order1 = new Order(id1, managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order1.setState(OrderState.OPEN);
		Order order2 = new Order(id2, managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order2.setState(OrderState.OPEN);
		Order order3 = new Order(id3, managerTestHelper.getDefaultFederationToken(), null, xOCCIAtt, true, "");
		order3.setState(OrderState.OPEN);

		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);
		managerDataStoreController.addOrder(order2);
		managerDataStoreController.addOrder(order3);

		// checking open orders
		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		Assert.assertEquals(3, ordersFromUser.size());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(1).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(2).getState());

		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		// removing one orders 
		managerController.removeOrder(managerTestHelper.getDefaultFederationToken().getAccessId(), id1);

		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		Assert.assertEquals(2, ordersFromUser.size());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(1).getState());
	
		// removing the rest of orders
		managerController.removeOrder(managerTestHelper.getDefaultFederationToken().getAccessId(), id2);
		managerController.removeOrder(managerTestHelper.getDefaultFederationToken().getAccessId(), id3);
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		
		Assert.assertEquals(0, ordersFromUser.size());
	}
	
	@Test
	public void testGetFlavors() {
		List<Flavor> flavors = managerController.getFlavorsProvided();
		String[] verifyFlavors = new String[] { ManagerTestHelper.VALUE_FLAVOR_SMALL,
				ManagerTestHelper.VALUE_FLAVOR_MEDIUM, ManagerTestHelper.VALUE_FLAVOR_LARGE};
		for (Flavor flavor : flavors) {
			boolean thereIs = false;
			for (String valueFlavor : verifyFlavors) {
				if (flavor.getMem().equals(ResourceRepository.getAttValue("mem", valueFlavor))
						&& flavor.getCpu()
								.equals(ResourceRepository.getAttValue("cpu", valueFlavor))) {
					thereIs = true;
				}
			}
			if (!thereIs) {
				Assert.fail();
			}
		}
	}
	
	@Test
	public void testGetAllowedFederationMembers() {
		ResourcesInfo resourcesInfoOne = new ResourcesInfo("id1","", "", "", "", "", "");		
		ResourcesInfo resourcesInfoTwo = new ResourcesInfo("id2","", "", "", "", "", "");		
		ResourcesInfo resourcesInfoThree = new ResourcesInfo("id3","", "", "", "", "", "");

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(resourcesInfoOne));
		listMembers.add(new FederationMember(resourcesInfoTwo));
		listMembers.add(new FederationMember(resourcesInfoThree));
		managerController.updateMembers(listMembers);
		
		String requirements = null;
		List<FederationMember> allowedFederationMembers = managerController.getAllowedFederationMembers(requirements);
		Assert.assertEquals(3, allowedFederationMembers.size());				
	}
	
	@Test
	public void testGetAllowedFederationMembersWithRequirements() {
		ResourcesInfo resourcesInfoOne = new ResourcesInfo("id1","", "", "", "", "", "");		
		ResourcesInfo resourcesInfoTwo = new ResourcesInfo("id2","", "", "", "", "", "");		
		ResourcesInfo resourcesInfoThree = new ResourcesInfo("id3","", "", "", "", "", "");

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(resourcesInfoOne));
		listMembers.add(new FederationMember(resourcesInfoTwo));
		listMembers.add(new FederationMember(resourcesInfoThree));
		managerController.updateMembers(listMembers);
		
		String requirements = RequirementsHelper.GLUE_LOCATION_TERM + "==\"id1\"";
		List<FederationMember> allowedFederationMembers = managerController.getAllowedFederationMembers(requirements);
		Assert.assertEquals(1, allowedFederationMembers.size());
		
		requirements = RequirementsHelper.GLUE_LOCATION_TERM + " == \"id1\" || " + RequirementsHelper.GLUE_LOCATION_TERM + " == \"id2\"";
		allowedFederationMembers = managerController.getAllowedFederationMembers(requirements);
		Assert.assertEquals(2, allowedFederationMembers.size());	
	}	
	
	public void testInstanceIsBeingUsedByFulfilledOrder(){
		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, null, true, "");
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order1.setProvidingMemberId("remote-manager.test.com");
		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);
		
		Assert.assertTrue(managerController.instanceHasOrderRelatedTo(order1.getId(), DefaultDataTestHelper.INSTANCE_ID
				+ Order.SEPARATOR_GLOBAL_ID + "remote-manager.test.com"));
		Assert.assertFalse(managerController.instanceHasOrderRelatedTo(order1.getId(), "any_value"
				+ Order.SEPARATOR_GLOBAL_ID + "remote-manager.test.com"));
	}
	
	@Test
	public void testInstanceIsBeingUsedByDeletedOrder(){
		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, null, true, "");
		order1.setState(OrderState.DELETED);
		order1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		order1.setProvidingMemberId("remote-manager.test.com");
		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);
				
		Assert.assertTrue(managerController.instanceHasOrderRelatedTo(order1.getId(), DefaultDataTestHelper.INSTANCE_ID
				+ Order.SEPARATOR_GLOBAL_ID + "remote-manager.test.com"));
		Assert.assertFalse(managerController.instanceHasOrderRelatedTo(order1.getId(), "any_value"
				+ Order.SEPARATOR_GLOBAL_ID + "remote-manager.test.com"));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testInstanceIsNotBeingUsedButOrderWasForwarded(){
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfo.setId(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(), 
						Mockito.anyMap(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""));
				
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(localResourcesInfo);
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		listMembers.add(new FederationMember(remoteResourcesInfo));
		managerController.updateMembers(listMembers);

		Order orderOne = new Order("id1", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true, "");
		orderOne.setState(OrderState.OPEN);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);

		// mocking date
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);

		// submiting orders
		managerController.checkAndSubmitOpenOrders();

		// checking if order was forwarded to remote member
		List<Order> ordersFromUser = managerController.getOrdersFromUser(
				managerTestHelper.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(
				orderOne.getId()));
		
		// checking if forwarded orders is being considered while checking if
		// instance is being used
		Assert.assertTrue(managerController.instanceHasOrderRelatedTo(orderOne.getId(),
				DefaultDataTestHelper.INSTANCE_ID + Order.SEPARATOR_GLOBAL_ID
						+ DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL));
		Assert.assertTrue(managerController.instanceHasOrderRelatedTo(orderOne.getId(),
				"any_value" + Order.SEPARATOR_GLOBAL_ID
						+ DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL));
	}
	
	@Test
	public void testInstanceIsNotBeingUsed(){
		// setting orders repository
		Order order1 = new Order("id1",
				managerTestHelper.getDefaultFederationToken(), null, null, true, "");
		order1.setState(OrderState.OPEN);
		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);
		
		Assert.assertFalse(managerController.instanceHasOrderRelatedTo(order1.getId(), "instanceId"));
	}
	
	@Test
	public void testMonitorServedOrderRemovingOrder() throws InterruptedException{
		
		ManagerController managerControllerSpy = this.managerController;		
		
		// checking there is not served order
		Assert.assertEquals(0, managerControllerSpy.getServedOrders().size());
		
		mockOrderInstance();
		
		// mocking packet sender
		IQ iq = new IQ();
		iq.setTo("manager1-test.com");
		iq.setType(Type.get);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.INSTANCEBEINGUSED_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(DefaultDataTestHelper.INSTANCE_ID);

		IQ response = IQ.createResultIQ(iq);
		response.setError(Condition.item_not_found);
		
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);
		managerControllerSpy.setPacketSender(packetSender);
				
		Mockito.doReturn(true).when(managerControllerSpy).isThereEnoughQuota("manager1-test.com");
		managerControllerSpy.queueServedOrder("manager1-test.com", new ArrayList<Category>(),
				xOCCIAtt, "id1", managerTestHelper.getDefaultFederationToken());
		managerControllerSpy.checkAndSubmitOpenOrders();
		
		// checking there is one served order
		Assert.assertEquals(1, managerControllerSpy.getServedOrders().size());
		Assert.assertEquals("manager1-test.com",
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
		Assert.assertEquals("id1", getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getId());

		// monitoring served orders
		managerControllerSpy.monitorServedOrders();
	
		// checking there is not served order		
		Assert.assertEquals(0, managerControllerSpy.getServedOrders().size());		
	}
	
	@Test
	public void testMonitorServedOrderKeepingOrder() throws InterruptedException{
		
		ManagerController managerControllerSpy = this.managerController;				
		
		// checking there is not served order
		Assert.assertEquals(0, managerControllerSpy.getServedOrders().size());
		
		mockOrderInstance();
		
		// mocking packet sender
		IQ iq = new IQ();
		iq.setTo("manager1-test.com");
		iq.setType(Type.get);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.INSTANCEBEINGUSED_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(DefaultDataTestHelper.INSTANCE_ID);

		IQ response = IQ.createResultIQ(iq);
		
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);
		managerControllerSpy.setPacketSender(packetSender);
		
		Mockito.doReturn(true).when(managerControllerSpy).isThereEnoughQuota("manager1-test.com");
		managerControllerSpy.queueServedOrder("manager1-test.com", new ArrayList<Category>(),
				xOCCIAtt, "id1", managerTestHelper.getDefaultFederationToken());		
		managerControllerSpy.checkAndSubmitOpenOrders();
				
		// checking there is one served order
		Assert.assertEquals(1, managerControllerSpy.getServedOrders().size());
		Assert.assertEquals("manager1-test.com",
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
		Assert.assertEquals("id1", getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getId());

		// monitoring served orders
		managerControllerSpy.monitorServedOrders();
	
		// checking there is not served order		
		Assert.assertEquals(1, managerControllerSpy.getServedOrders().size());
		Assert.assertEquals("manager1-test.com",
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
	}
	
	private Order getOrderByInstanceId(List<Order> remoteOrders, String instanceId) {
		for (Order order : remoteOrders) {
			if (instanceId.equals(order.getInstanceId())){
				return order;
			}
		}
		return null;
	}

	@Test
	public void testGetAllFogbowFedertionInstances() {
		MapperPlugin mapperPlugin = Mockito
				.mock(MapperPlugin.class);
		Map<String, Map<String, String>> fedUsersCredentials = new HashMap<String, Map<String,String>>();
		HashMap<String, String> credentialsOne = new HashMap<String, String>();
		credentialsOne.put("one", "x1");
		HashMap<String, String> credentialsTwo = new HashMap<String, String>();
		credentialsTwo.put("two", "y1");
		HashMap<String, String> credentialsThree = new HashMap<String, String>();
		credentialsThree.put("trhee", "z1");
		fedUsersCredentials.put("One", credentialsOne);
		fedUsersCredentials.put("two", credentialsTwo);
		fedUsersCredentials.put("three", credentialsThree);
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(fedUsersCredentials);
		managerController.setLocalCredentailsPlugin(mapperPlugin);
				
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenOne = new Token("One", new Token.User("", ""), null, null);
		Mockito.when(identityPlugin.createToken(credentialsOne)).thenReturn(tokenOne);
		Token tokenTwo = new Token("Two", new Token.User("", ""), null, null);
		Mockito.when(identityPlugin.createToken(credentialsTwo)).thenReturn(tokenTwo);
		Token tokenThree = new Token("Three", new Token.User("", ""), null, null);
		Mockito.when(identityPlugin.createToken(credentialsThree)).thenReturn(tokenThree);
		managerController.setLocalIdentityPlugin(identityPlugin);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		List<Instance> instancesListOne = Arrays.asList(new Instance[] {new Instance("One"), new Instance("Two")});
		Mockito.when(computePlugin.getInstances(tokenOne)).thenReturn(instancesListOne);
		List<Instance> instancesListTwo = Arrays.asList(new Instance[] {new Instance("Three"), new Instance("Four"), new Instance("Five")});
		Mockito.when(computePlugin.getInstances(tokenTwo)).thenReturn(instancesListTwo);	
		Mockito.when(computePlugin.getInstances(tokenThree)).thenReturn(instancesListTwo);
		managerController.setComputePlugin(computePlugin);
		List<Instance> allFogbowFederationInstances = managerController.getAllFogbowFederationInstances();
		Assert.assertEquals(5, allFogbowFederationInstances.size());
	}
	
	@Test
	public void testGetAllFogbowFedertionInstancesWithWrongCrendentials() {
		MapperPlugin mapperPlugin = Mockito
				.mock(MapperPlugin.class);
		Map<String, Map<String, String>> fedUsersCredentials = new HashMap<String, Map<String,String>>();
		HashMap<String, String> credentialsOne = new HashMap<String, String>();
		credentialsOne.put("one", "x1");
		HashMap<String, String> credentialsTwo = new HashMap<String, String>();
		credentialsTwo.put("two", "y1");
		HashMap<String, String> credentialsTree = new HashMap<String, String>();
		credentialsTree.put("tree", "y1");
		HashMap<String, String> credentialsFour = new HashMap<String, String>();
		fedUsersCredentials.put("one", credentialsOne);
		fedUsersCredentials.put("two",credentialsTwo);
		fedUsersCredentials.put("three",credentialsTree);
		fedUsersCredentials.put("four",credentialsFour);		
		Mockito.when(mapperPlugin.getAllLocalCredentials())
				.thenReturn(fedUsersCredentials);
		managerController.setLocalCredentailsPlugin(mapperPlugin);
				
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenOne = new Token("One", new Token.User("", ""), null, null);
		Mockito.when(identityPlugin.createToken(credentialsOne)).thenReturn(tokenOne);
		Token tokenTwo = new Token("Two", new Token.User("", ""), null, null);
		Mockito.when(identityPlugin.createToken(credentialsTwo)).thenReturn(tokenTwo);
		Mockito.when(identityPlugin.createToken(credentialsTree)).thenReturn(null);
		Mockito.when(identityPlugin.createToken(credentialsFour)).thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""));
		managerController.setLocalIdentityPlugin(identityPlugin);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		List<Instance> instancesListOne = Arrays.asList(new Instance[] {new Instance("One"), new Instance("Two")});
		Mockito.when(computePlugin.getInstances(tokenOne)).thenReturn(instancesListOne);
		List<Instance> instancesListTwo = Arrays.asList(new Instance[] {new Instance("Three")});
		Mockito.when(computePlugin.getInstances(tokenTwo)).thenReturn(instancesListTwo);
		managerController.setComputePlugin(computePlugin);
		List<Instance> allFogbowFederationInstances = managerController.getAllFogbowFederationInstances();
		Assert.assertEquals(3, allFogbowFederationInstances.size());
	}	
	
	@Test
	public void testGetTokenPerInstance() {
		MapperPlugin mapperPlugin = Mockito
				.mock(MapperPlugin.class);
		Map<String, Map<String, String>> fedUsersCredentials = new HashMap<String, Map<String,String>>();
		HashMap<String, String> credentialsOne = new HashMap<String, String>();
		credentialsOne.put("one", "x1");
		HashMap<String, String> credentialsTwo = new HashMap<String, String>();
		credentialsTwo.put("two", "y1");
		fedUsersCredentials.put("one", credentialsOne);
		fedUsersCredentials.put("two", credentialsTwo);
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(fedUsersCredentials);
		managerController.setLocalCredentailsPlugin(mapperPlugin);
				
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenOne = new Token("One", new Token.User("", ""), null, null);
		Mockito.when(identityPlugin.createToken(credentialsOne)).thenReturn(tokenOne);
		Token tokenTwo = new Token("Two", new Token.User("", ""), null, null);;
		Mockito.when(identityPlugin.createToken(credentialsTwo)).thenReturn(tokenTwo);
		managerController.setLocalIdentityPlugin(identityPlugin);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Instance instanceOne = new Instance("one");
		Instance instanceTwo = new Instance("two");
		Instance instanceThree = new Instance("three");
		List<Instance> instancesListOne = Arrays.asList(new Instance[] {instanceOne, new Instance("four")});
		Mockito.when(computePlugin.getInstances(tokenOne)).thenReturn(instancesListOne);
		List<Instance> instancesListTwo = Arrays.asList(new Instance[] {instanceTwo, new Instance("five"), instanceThree});
		Mockito.when(computePlugin.getInstances(tokenTwo)).thenReturn(instancesListTwo);
		managerController.setComputePlugin(computePlugin);
		List<Instance> allFogbowFederationInstances = managerController.getAllFogbowFederationInstances();
		Assert.assertEquals(5, allFogbowFederationInstances.size());
		
		Assert.assertEquals(tokenOne, managerController.getTokenPerInstance(instanceOne.getId()));
		Assert.assertEquals(tokenTwo, managerController.getTokenPerInstance(instanceTwo.getId()));
		Assert.assertEquals(tokenTwo, managerController.getTokenPerInstance(instanceThree.getId()));
	}	
	
	@Test
	public void testGarbageCollector(){
		// setting order repository
		Token federationToken = new Token(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new Token.User(DefaultDataTestHelper.FED_USER_NAME, DefaultDataTestHelper.FED_USER_NAME)
				, new Date(), new HashMap<String, String>());
		
		Order order1 = new Order("id1", federationToken,  null, xOCCIAtt, true, "");
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);

		// mocking getInstances
		Mockito.reset(managerTestHelper.getComputePlugin());
		List<Instance> federationInstances = new ArrayList<Instance>();
		federationInstances.add(new Instance(DefaultDataTestHelper.INSTANCE_ID));
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstances(
						managerTestHelper.getDefaultFederationToken())).thenReturn(
				federationInstances);
		
		// checking if there is one instance for federation token
		Assert.assertEquals(1, managerController.getInstances(federationToken.getAccessId(),
						OrderConstants.COMPUTE_TERM).size());
		Assert.assertEquals(
				DefaultDataTestHelper.INSTANCE_ID + Order.SEPARATOR_GLOBAL_ID
						+ DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,managerController
						.getInstances(federationToken.getAccessId(),OrderConstants.COMPUTE_TERM).get(0).getId());

		managerController.garbageCollector();

		// checking if garbage collector does not remove the instance
		Assert.assertEquals(1, managerController.getInstances(federationToken.getAccessId(),
						OrderConstants.COMPUTE_TERM).size());
		Assert.assertEquals(
				DefaultDataTestHelper.INSTANCE_ID + Order.SEPARATOR_GLOBAL_ID
						+ DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, managerController
						.getInstances(federationToken.getAccessId(), OrderConstants.COMPUTE_TERM).get(0).getId());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGarbageCollectorRemovingInstance(){
		// setting order repository
		Token federationToken = new Token(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				new Token.User(DefaultDataTestHelper.FED_USER_NAME, DefaultDataTestHelper.FED_USER_NAME),
				new Date(), new HashMap<String, String>());
		
		Order order1 = new Order("id1", federationToken, null, null, true, "");
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);

		// mocking getInstances
		Mockito.reset(managerTestHelper.getComputePlugin());
		List<Instance> twoInstances = new ArrayList<Instance>();
		twoInstances.add(new Instance(DefaultDataTestHelper.INSTANCE_ID));
		twoInstances.add(new Instance("instance2"));
		
		List<Instance> oneInstance = new ArrayList<Instance>();
		oneInstance.add(new Instance(DefaultDataTestHelper.INSTANCE_ID));
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstances(
						managerTestHelper.getDefaultFederationToken()))
						.thenReturn(twoInstances, twoInstances, oneInstance);
		// updating compute mock
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				managerTestHelper.getDefaultFederationToken(), "instance2");
		
		// checking if there is one instance for federation token
		List<Instance> resultInstances = managerTestHelper.getComputePlugin().getInstances(managerTestHelper.getDefaultFederationToken()); 
		Assert.assertEquals(2, resultInstances.size());
		for (Instance instance : twoInstances) {
			Assert.assertTrue(resultInstances.contains(instance));
		}

		managerController.garbageCollector();
		
		// checking if garbage collector does not remove the instance
		resultInstances = managerTestHelper.getComputePlugin().getInstances(managerTestHelper.getDefaultFederationToken());
		Assert.assertEquals(1, resultInstances.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, resultInstances.get(0).getId());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGarbageCollectorWithServedOrder() {
		
		ManagerController managerControllerSpy = this.managerController;			
		Mockito.doReturn(true).when(managerControllerSpy).isThereEnoughQuota("manager1-test.com");
		
		// checking there is not served order
		Assert.assertEquals(0, managerControllerSpy.getServedOrders().size());
		
		// mocking getInstances
		Mockito.reset(managerTestHelper.getComputePlugin());
		List<Instance> twoInstances = new ArrayList<Instance>();
		twoInstances.add(new Instance(DefaultDataTestHelper.INSTANCE_ID));
		twoInstances.add(new Instance("instance2"));
		
		List<Instance> oneInstance = new ArrayList<Instance>();
		oneInstance.add(new Instance(DefaultDataTestHelper.INSTANCE_ID));
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenReturn(
				DefaultDataTestHelper.INSTANCE_ID);
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstances(
						managerTestHelper.getDefaultFederationToken()))
						.thenReturn(twoInstances, twoInstances, oneInstance);
		
		// creating instance for remote member 
		managerControllerSpy.queueServedOrder("manager1-test.com", new ArrayList<Category>(),
				xOCCIAtt, "id1", managerTestHelper.getDefaultFederationToken());		
		managerControllerSpy.checkAndSubmitOpenOrders();
				
		// checking there is one served order
		Assert.assertEquals(1, managerControllerSpy.getServedOrders().size());
		Assert.assertEquals("manager1-test.com",
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
		Assert.assertEquals("id1", getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getId());
				
		// updating compute mock
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				managerTestHelper.getDefaultFederationToken(), "instance2");
	
		// checking if there is one instance for federation token
		List<Instance> resultInstances = managerTestHelper.getComputePlugin().getInstances(managerTestHelper.getDefaultFederationToken()); 
		Assert.assertEquals(2, resultInstances.size());
		for (Instance instance : twoInstances) {
			Assert.assertTrue(resultInstances.contains(instance));
		}

		managerControllerSpy.garbageCollector();
		
		// checking if garbage collector does not remove the instance
		resultInstances = managerTestHelper.getComputePlugin().getInstances(managerTestHelper.getDefaultFederationToken());
		Assert.assertEquals(1, resultInstances.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, resultInstances.get(0).getId());
		// checking there is one served order
		Assert.assertEquals(1, managerControllerSpy.getServedOrders().size());
		Assert.assertEquals("manager1-test.com",
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getOrderByInstanceId(managerControllerSpy.getServedOrders(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void testSSHKeyReplacementWhenOriginalOrderHasPublicKey() 
			throws FileNotFoundException, IOException, MessagingException {
		Map<String, String> extraProperties = new HashMap<String, String>();
		extraProperties.put(ConfigurationConstants.SSH_PUBLIC_KEY_PATH, 
				DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH);
		ManagerController localManagerController = 
				managerTestHelper.createDefaultManagerController(extraProperties);
		ManagerController spiedManageController = this.managerController;		
		
		String remoteOrderId = "id1";
		
		Map<String,String> newXOCCIAttr = new HashMap<String,String>(this.xOCCIAtt);
		ArrayList<Category> categories = new ArrayList<Category>();
		final Category publicKeyCategory = new Category(OrderConstants.PUBLIC_KEY_TERM, 
				OrderConstants.CREDENTIALS_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS);
		categories.add(publicKeyCategory);
		newXOCCIAttr.put(OrderAttribute.DATA_PUBLIC_KEY.getValue(), "public-key");
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String newInstanceId = "newinstanceid";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), 
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString())).thenReturn(newInstanceId);
		spiedManageController.setComputePlugin(computePlugin);
		
		Order servedOrder = new Order(remoteOrderId, managerTestHelper.getDefaultFederationToken(), categories,
				newXOCCIAttr, true,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setInstanceId(newInstanceId);
		servedOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Instance instance = new Instance(newInstanceId);
		instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, "127.0.0.1:5555");
		instance.addAttribute(Instance.SSH_USERNAME_ATT, "fogbow");
		
		Mockito.when(spiedManageController.waitForSSHPublicAddress(Mockito.any(Order.class))).thenReturn(instance);
		Mockito.doNothing().when(spiedManageController).waitForSSHConnectivity(instance);
		
		spiedManageController.createLocalInstanceWithFederationUser(servedOrder);
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		Mockito.verify(computePlugin).requestInstance(Mockito.any(Token.class),
				Mockito.argThat(new ArgumentMatcher<List<Category>>() {

					@Override
					public boolean matches(Object argument) {
						List<Category> categories = (List<Category>) argument;
						return !categories.contains(publicKeyCategory);
					}
				}), Mockito.argThat(new ArgumentMatcher<Map<String, String>>() {

					@Override
					public boolean matches(Object argument) {
						Map<String, String> xOCCIAttr = (Map<String, String>) argument;
						String publicKeyValue = xOCCIAttr
								.get(OrderAttribute.DATA_PUBLIC_KEY
										.getValue());
						return publicKeyValue == null;
					}
				}), Mockito.anyString());
		
		String base64UserDataCmd = new String(Base64.decodeBase64(localManagerController
				.createUserDataUtilsCommand(servedOrder)), "UTF-8");
		Assert.assertTrue(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void testSSHKeyReplacementWhenOriginalOrderHasNoPublicKey() 
			throws FileNotFoundException, IOException, MessagingException {
		Map<String, String> extraProperties = new HashMap<String, String>();
		extraProperties.put(ConfigurationConstants.SSH_PUBLIC_KEY_PATH, 
				DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH);
		ManagerController localManagerController = 
				managerTestHelper.createDefaultManagerController(extraProperties);
		String remoteOrderId = "id1";
		ManagerController spiedManageController = localManagerController;		
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String newInstanceId = "newinstanceid";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), 
				Mockito.anyList(), Mockito.anyMap(), Mockito.anyString())).thenReturn(newInstanceId);
		spiedManageController.setComputePlugin(computePlugin);
		
		Order servedOrder = new Order(remoteOrderId, managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				xOCCIAtt, true,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setInstanceId(newInstanceId);
		servedOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Instance instance = new Instance(newInstanceId);
		instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, "127.0.0.1:5555");
		instance.addAttribute(Instance.SSH_USERNAME_ATT, "fogbow");
		
		Mockito.when(spiedManageController.waitForSSHPublicAddress(Mockito.any(Order.class))).thenReturn(instance);
		Mockito.doNothing().when(spiedManageController).waitForSSHConnectivity(instance);
		
		spiedManageController.createLocalInstanceWithFederationUser(servedOrder);
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		final Category publicKeyCategory = new Category(OrderConstants.PUBLIC_KEY_TERM, 
				OrderConstants.CREDENTIALS_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS);
		
		Mockito.verify(computePlugin).requestInstance(Mockito.any(Token.class),
				Mockito.argThat(new ArgumentMatcher<List<Category>>() {

					@Override
					public boolean matches(Object argument) {
						List<Category> categories = (List<Category>) argument;
						return !categories.contains(publicKeyCategory);
					}
				}), Mockito.argThat(new ArgumentMatcher<Map<String, String>>() {

					@Override
					public boolean matches(Object argument) {
						Map<String, String> xOCCIAttr = (Map<String, String>) argument;
						String publicKeyValue = xOCCIAttr
								.get(OrderAttribute.DATA_PUBLIC_KEY
										.getValue());
						return publicKeyValue == null;
					}
				}), Mockito.anyString());
		
		String base64UserDataCmd = new String(Base64.decodeBase64(localManagerController
				.createUserDataUtilsCommand(servedOrder)), "UTF-8");
		Assert.assertTrue(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSSHKeyReplacementWhenManagerKeyIsNotDefined() 
			throws FileNotFoundException, IOException, MessagingException {
		ManagerController spiedManagerController = this.managerController;				
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String instanceId = "instance1";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenReturn(instanceId);
		spiedManagerController.setComputePlugin(computePlugin);
		
		String servedOrderId = "id1";
		Order servedOrder = new Order(servedOrderId, managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				xOCCIAtt, true,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setInstanceId(instanceId);
		servedOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		spiedManagerController.createLocalInstanceWithFederationUser(servedOrder);
		
		Mockito.verify(spiedManagerController, Mockito.never()).waitForSSHPublicAddress(Mockito.eq(servedOrder));
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		String base64UserDataCmd = new String(Base64.decodeBase64(managerController
				.createUserDataUtilsCommand(servedOrder)), "UTF-8");
		Assert.assertFalse(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void testSSHKeyReplacementLocallyWhenOriginalOrderHasPublicKey() throws FileNotFoundException, IOException, MessagingException {
		Map<String, String> extraProperties = new HashMap<String, String>();
		extraProperties.put(ConfigurationConstants.SSH_PUBLIC_KEY_PATH, 
				DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH);
		ManagerController localManagerController = 
				managerTestHelper.createDefaultManagerController(extraProperties);
		ManagerController spiedManageController = localManagerController;		
		
		String localOrderId = "id1";
		
		Map<String,String> newXOCCIAttr = new HashMap<String,String>(this.xOCCIAtt);
		ArrayList<Category> categories = new ArrayList<Category>();
		final Category publicKeyCategory = new Category(OrderConstants.PUBLIC_KEY_TERM, 
				OrderConstants.CREDENTIALS_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS);
		categories.add(publicKeyCategory);
		newXOCCIAttr.put(OrderAttribute.DATA_PUBLIC_KEY.getValue(), "public-key");
		newXOCCIAttr.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String newInstanceId = "newinstanceid";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), 
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString())).thenReturn(newInstanceId);
		spiedManageController.setComputePlugin(computePlugin);
		
		Order localOrder = new Order(localOrderId, managerTestHelper.getDefaultFederationToken(), categories,
				newXOCCIAttr, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		localOrder.setState(OrderState.FULFILLED);
		localOrder.setInstanceId(newInstanceId);
		localOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Instance instance = new Instance(newInstanceId);
		instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, "127.0.0.1:5555");
		instance.addAttribute(Instance.SSH_USERNAME_ATT, "fogbow");
		
		Mockito.when(spiedManageController.waitForSSHPublicAddress(Mockito.any(Order.class))).thenReturn(instance);
		Mockito.doNothing().when(spiedManageController).waitForSSHConnectivity(instance);
		
		spiedManageController.createLocalInstanceWithFederationUser(localOrder);
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		Mockito.verify(computePlugin).requestInstance(Mockito.any(Token.class),
				Mockito.argThat(new ArgumentMatcher<List<Category>>() {

					@Override
					public boolean matches(Object argument) {
						List<Category> categories = (List<Category>) argument;
						return !categories.contains(publicKeyCategory);
					}
				}), Mockito.argThat(new ArgumentMatcher<Map<String, String>>() {

					@Override
					public boolean matches(Object argument) {
						Map<String, String> xOCCIAttr = (Map<String, String>) argument;
						String publicKeyValue = xOCCIAttr
								.get(OrderAttribute.DATA_PUBLIC_KEY
										.getValue());
						return publicKeyValue == null;
					}
				}), Mockito.anyString());
		
		String base64UserDataCmd = new String(Base64.decodeBase64(localManagerController
				.createUserDataUtilsCommand(localOrder)), "UTF-8");
		Assert.assertTrue(base64UserDataCmd.contains(localManagerPublicKeyData));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSSHKeyReplacementLocallyWhenOriginalOrderHasNoPublicKey() 
			throws FileNotFoundException, IOException, MessagingException {
		Map<String, String> extraProperties = new HashMap<String, String>();
		extraProperties.put(ConfigurationConstants.SSH_PUBLIC_KEY_PATH, 
				DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH);
		ManagerController localManagerController = 
				managerTestHelper.createDefaultManagerController(extraProperties);
		String localOrderId = "id1";
		ManagerController spiedManageController = localManagerController;		
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String newInstanceId = "newinstanceid";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), 
				Mockito.anyList(), Mockito.anyMap(), Mockito.anyString())).thenReturn(newInstanceId);
		spiedManageController.setComputePlugin(computePlugin);
		
		Order localOrder = new Order(localOrderId, managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				xOCCIAtt, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		localOrder.setState(OrderState.FULFILLED);
		localOrder.setInstanceId(newInstanceId);
		localOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Instance instance = new Instance(newInstanceId);
		instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, "127.0.0.1:5555");
		instance.addAttribute(Instance.SSH_USERNAME_ATT, "fogbow");
		
		Mockito.when(spiedManageController.waitForSSHPublicAddress(Mockito.any(Order.class))).thenReturn(instance);
		Mockito.doNothing().when(spiedManageController).waitForSSHConnectivity(instance);
		
		spiedManageController.createLocalInstanceWithFederationUser(localOrder);
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		final Category publicKeyCategory = new Category(OrderConstants.PUBLIC_KEY_TERM, 
				OrderConstants.CREDENTIALS_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS);
		
		Mockito.verify(computePlugin).requestInstance(Mockito.any(Token.class),
				Mockito.argThat(new ArgumentMatcher<List<Category>>() {

					@Override
					public boolean matches(Object argument) {
						List<Category> categories = (List<Category>) argument;
						return !categories.contains(publicKeyCategory);
					}
				}), Mockito.argThat(new ArgumentMatcher<Map<String, String>>() {

					@Override
					public boolean matches(Object argument) {
						Map<String, String> xOCCIAttr = (Map<String, String>) argument;
						String publicKeyValue = xOCCIAttr
								.get(OrderAttribute.DATA_PUBLIC_KEY
										.getValue());
						return publicKeyValue == null;
					}
				}), Mockito.anyString());
		
		String base64UserDataCmd = new String(Base64.decodeBase64(localManagerController
				.createUserDataUtilsCommand(localOrder)), "UTF-8");
		Assert.assertTrue(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSSHKeyReplacementLocallyWhenManagerKeyIsNotDefined() 
			throws FileNotFoundException, IOException, MessagingException {
		ManagerController managerControllerSpy = this.managerController;
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String instanceId = "instance1";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenReturn(instanceId);
		managerControllerSpy.setComputePlugin(computePlugin);
		
		String localOrderId = "id1";
		Order localOrder = new Order(localOrderId, managerTestHelper.getDefaultFederationToken(), 
				new ArrayList<Category>(), xOCCIAtt, true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		localOrder.setState(OrderState.FULFILLED);
		localOrder.setInstanceId(instanceId);
		localOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		managerControllerSpy.createLocalInstanceWithFederationUser(localOrder);
		
		Mockito.verify(managerControllerSpy, Mockito.never()).waitForSSHPublicAddress(Mockito.eq(localOrder));
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		String base64UserDataCmd = new String(Base64.decodeBase64(managerController
				.createUserDataUtilsCommand(localOrder)), "UTF-8");
		Assert.assertFalse(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	public void testPreemption() {
		Order localOrder = new Order("id1", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		localOrder.setState(OrderState.FULFILLED);
		localOrder.setInstanceId("instance1");
		localOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Order servedOrder1 = new Order("id2", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), false,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedOrder1.setState(OrderState.FULFILLED);
		servedOrder1.setInstanceId("instance2");
		servedOrder1.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Order servedOrder2 = new Order("id3", managerTestHelper.getDefaultFederationToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), false,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedOrder2.setState(OrderState.FULFILLED);
		servedOrder2.setInstanceId("instance3");
		servedOrder2.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(localOrder);
		managerDataStoreController.addOrder(servedOrder1);
		managerDataStoreController.addOrder(servedOrder2);

		// check orders
		Assert.assertEquals(1, managerDataStoreController.getAllLocalOrders().size());
		Assert.assertTrue(managerDataStoreController.getAllLocalOrders().contains(localOrder));
		Assert.assertEquals(OrderState.FULFILLED, localOrder.getState());
		Assert.assertEquals(2, managerDataStoreController.getAllServedOrders().size());
		Assert.assertTrue(managerDataStoreController.getAllServedOrders().contains(servedOrder1));
		Assert.assertTrue(managerDataStoreController.getAllServedOrders().contains(servedOrder2));
		
		// preempt servedOrder1
		managerController.preemption(servedOrder1);
		Assert.assertEquals(1, managerDataStoreController.getAllLocalOrders().size());
		Assert.assertTrue(managerDataStoreController.getAllLocalOrders().contains(localOrder));
		Assert.assertEquals(OrderState.FULFILLED, localOrder.getState());
		Assert.assertEquals(1, managerDataStoreController.getAllServedOrders().size());
		Assert.assertFalse(managerDataStoreController.getAllServedOrders().contains(servedOrder1));
		Assert.assertTrue(managerDataStoreController.getAllServedOrders().contains(servedOrder2));

		// preempt servedOrder2
		managerController.preemption(servedOrder2);
		Assert.assertEquals(1, managerDataStoreController.getAllLocalOrders().size());
		Assert.assertTrue(managerDataStoreController.getAllLocalOrders().contains(localOrder));
		Assert.assertEquals(OrderState.FULFILLED, localOrder.getState());
		Assert.assertTrue(managerDataStoreController.getAllServedOrders().isEmpty());
		Assert.assertFalse(managerDataStoreController.getAllServedOrders().contains(servedOrder1));
		Assert.assertFalse(managerDataStoreController.getAllServedOrders().contains(servedOrder2));
		
		// preempt localOrder
		managerController.preemption(localOrder);
		Assert.assertEquals(1, managerDataStoreController.getAllLocalOrders().size());
		Assert.assertTrue(managerDataStoreController.getAllLocalOrders().contains(localOrder));
		Assert.assertEquals(OrderState.CLOSED, localOrder.getState());
		Assert.assertTrue(managerDataStoreController.getAllServedOrders().isEmpty());
		Assert.assertFalse(managerDataStoreController.getAllServedOrders().contains(servedOrder1));
		Assert.assertFalse(managerDataStoreController.getAllServedOrders().contains(servedOrder2));
	}
	
	@Test
	public void createOrders() {
		IdentityPlugin federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		IdentityPlugin localIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		String federationUser = "user";
		Token federationToken = new Token("id", new Token.User(federationUser, federationUser),
				new Date(), new HashMap<String, String>());
		Mockito.when(federationIdentityPlugin.getToken(Mockito.anyString())).thenReturn(
				federationToken, federationToken);
		Mockito.when(localIdentityPlugin.getToken(Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));
		managerController.setLocalIdentityPlugin(localIdentityPlugin);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);
		managerController.createOrders(ACCESS_TOKEN_ID_2, new ArrayList<Category>(), xOCCIAtt);

		for (Order order : managerController.getOrdersFromUser(federationToken.getAccessId())) {
			if (order.getFederationToken().getAccessId().isEmpty()) {
				Assert.fail();
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitOrdersSameBatchIdFailingCreateFederationUser() throws InterruptedException {
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class),
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString()))
				.thenReturn("newinstanceid")
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""));
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourcesInfo);
		managerController.setComputePlugin(computePlugin);

		String batchId = "batchIdOne";
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.BATCH_ID.getValue(), batchId);
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		Token token = managerTestHelper.getDefaultFederationToken();
		Order order1 = new Order("id1", token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		order1.setState(OrderState.OPEN);
		Order order2 = new Order("id2", token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		order2.setState(OrderState.OPEN);
		Order order3 = new Order("id3", token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		order3.setState(OrderState.OPEN);		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);
		managerDataStoreController.addOrder(order2);
		managerDataStoreController.addOrder(order3);

		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(token.getAccessId());
		Assert.assertEquals(OrderState.FULFILLED, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(1).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(2).getState());
		Assert.assertEquals(batchId, managerController.getFailedBatches()
				.getFailedBatchIdsPerType(FailedBatchType.FEDERATION_USER).get(0));	
	}	
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitRemoteOrdersSameBatchIdFailingCreateFederationUser()
			throws InterruptedException {
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				resourcesInfo);
		managerController.setComputePlugin(computePlugin);

		String batchId = "batchIdOne";
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.BATCH_ID.getValue(), batchId);
		Token token = managerTestHelper.getDefaultFederationToken();
		Order order1 = new Order("id1", token, new ArrayList<Category>(), xOCCIAtt,
				false, "");
		order1.setState(OrderState.OPEN);
		Order order2 = new Order("id2", token, new ArrayList<Category>(), xOCCIAtt,
				false, "");
		order2.setState(OrderState.OPEN);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);
		managerDataStoreController.addOrder(order2);

		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(token.getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.OPEN, order.getState());
		}

		Assert.assertEquals(batchId, managerController.getFailedBatches()
						.getFailedBatchIdsPerType(FailedBatchType.FEDERATION_USER).get(0));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitOrdersSameBatchIdFailingCreateFederationUserTryingAgain() throws InterruptedException {
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class),
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString()))
				.thenReturn("newinstanceid")
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""))
				.thenReturn("newinstanceid")
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""));
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourcesInfo);
		managerController.setComputePlugin(computePlugin);

		String batchId = "batchIdOne";
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		xOCCIAtt.put(OrderAttribute.BATCH_ID.getValue(), batchId);
		Token token = managerTestHelper.getDefaultFederationToken();
		Order order1 = new Order("id1", token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		order1.setState(OrderState.OPEN);
		Order order2 = new Order("id2", token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		order2.setState(OrderState.OPEN);
		Order order3 = new Order("id3", token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		order3.setState(OrderState.OPEN);		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(order1);
		managerDataStoreController.addOrder(order2);
		managerDataStoreController.addOrder(order3);

		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(token.getAccessId());
		Assert.assertEquals(OrderState.FULFILLED, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(1).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(2).getState());
		Assert.assertEquals(batchId, managerController.getFailedBatches()
				.getFailedBatchIdsPerType(FailedBatchType.FEDERATION_USER).get(0));
		
		managerController.checkAndSubmitOpenOrders();

		ordersFromUser = managerController.getOrdersFromUser(token.getAccessId());
		Assert.assertEquals(OrderState.FULFILLED, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.FULFILLED, ordersFromUser.get(1).getState());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(2).getState());
		Assert.assertEquals(batchId, managerController.getFailedBatches()
				.getFailedBatchIdsPerType(FailedBatchType.FEDERATION_USER).get(0));			
	}
	
	@Test
	public void testNormalizeBatchId() {
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		String batchId = "batchId";
		xOCCIAtt.put(OrderAttribute.BATCH_ID.getValue(), batchId);
		managerController.normalizeBatchId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				xOCCIAtt);
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL + "@" + batchId,
				xOCCIAtt.get(OrderAttribute.BATCH_ID.getValue()));
	}
	
	@Test
	public void testGetResourceInfo() {
		MapperPlugin mapperPlugin = Mockito
				.mock(MapperPlugin.class);
		Map<String, Map<String, String>> fedUsersCredentials = new HashMap<String, Map<String,String>>();
		HashMap<String, String> credentialsOne = new HashMap<String, String>();
		credentialsOne.put("one", "x1");
		HashMap<String, String> credentialsTwo = new HashMap<String, String>();
		credentialsTwo.put("two", "y1");
		HashMap<String, String> credentialsTree = new HashMap<String, String>();
		credentialsTree.put("two", "y1");
		fedUsersCredentials.put("one", credentialsOne);
		fedUsersCredentials.put("two", credentialsTwo);
		fedUsersCredentials.put("three", credentialsTree);
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(fedUsersCredentials);
		managerController.setLocalCredentailsPlugin(mapperPlugin);
				
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenOne = new Token("One", new Token.User("", ""), null, null);
		Mockito.when(identityPlugin.createToken(credentialsOne)).thenReturn(tokenOne);
		Token tokenTwo = new Token("Two", new Token.User("", ""), null, null);;
		Mockito.when(identityPlugin.createToken(credentialsTwo)).thenReturn(tokenTwo);
		Mockito.when(identityPlugin.createToken(credentialsTree)).thenReturn(tokenTwo);
		managerController.setLocalIdentityPlugin(identityPlugin);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		ResourcesInfo resourcesInfoOne = new ResourcesInfo("10", "20", "10", "20", "10", "20");
		Mockito.when(computePlugin.getResourcesInfo(tokenOne)).thenReturn(resourcesInfoOne);
		ResourcesInfo resourcesInfoTwo = new ResourcesInfo("20", "20", "20", "20", "20", "20");
		Mockito.when(computePlugin.getResourcesInfo(tokenTwo)).thenReturn(resourcesInfoTwo);
		managerController.setComputePlugin(computePlugin);
		
		ResourcesInfo resourcesInfo = managerController.getResourcesInfo(null, true);
		Assert.assertEquals("40", resourcesInfo.getCpuInUse());
		Assert.assertEquals("30", resourcesInfo.getCpuIdle());
		Assert.assertEquals("40", resourcesInfo.getMemInUse());
		Assert.assertEquals("30", resourcesInfo.getMemIdle());
		Assert.assertEquals("40", resourcesInfo.getInstancesInUse());
		Assert.assertEquals("30", resourcesInfo.getInstancesIdle());		
	}
	
	@Test
	public void testGetResourceInfoWithResourceInfoByUser() {
		MapperPlugin mapperPlugin = Mockito
				.mock(MapperPlugin.class);
		Map<String, Map<String, String>> fedUsersCredentials = new HashMap<String, Map<String,String>>();
		HashMap<String, String> credentialsOne = new HashMap<String, String>();
		credentialsOne.put("one", "x1");
		HashMap<String, String> credentialsTwo = new HashMap<String, String>();
		credentialsTwo.put("two", "y1");
		HashMap<String, String> credentialsTree = new HashMap<String, String>();
		credentialsTree.put("two", "y1");
		fedUsersCredentials.put("one", credentialsOne);
		fedUsersCredentials.put("two", credentialsTwo);
		fedUsersCredentials.put("three", credentialsTree);
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(fedUsersCredentials);
		managerController.setLocalCredentailsPlugin(mapperPlugin);		
				
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenOne = new Token("One", new Token.User("user", "user"), null, null);
		Mockito.when(identityPlugin.createToken(credentialsOne)).thenReturn(tokenOne);
		Token tokenTwo = new Token("Two", new Token.User("user", "user"), null, null);
		Mockito.when(identityPlugin.createToken(credentialsTwo)).thenReturn(tokenTwo);
		Mockito.when(identityPlugin.createToken(credentialsTree)).thenReturn(tokenTwo);
		managerController.setLocalIdentityPlugin(identityPlugin);
		
		String accessId = "accessId";
		IdentityPlugin federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		Token federationToken = new Token(accessId, new Token.User("user", "user"), new Date(), 
				new HashMap<String, String>());
		Mockito.when(federationIdentityPlugin.getToken(Mockito.eq(accessId))).thenReturn(federationToken);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);			
		
		Order orderOne = new Order("idOne", federationToken, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.OPEN);
		String instanceIdOne = "instanceIdOne";
		orderOne.setInstanceId(instanceIdOne);
		Order orderTwo = new Order("idTwo", federationToken, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderTwo.setState(OrderState.OPEN);
		String instanceIdTwo = "instanceIdTwo";
		orderTwo.setInstanceId(instanceIdTwo);		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);
		managerDataStoreController.addOrder(orderTwo);

		Map<String, String> attributesOne = new HashMap<String, String>();
		double memInstanceOne = 2.0;
		attributesOne.put("occi.compute.memory", String.valueOf(memInstanceOne));
		double cpuInstanceOne = 10.0;
		attributesOne.put("occi.compute.cores", String.valueOf(cpuInstanceOne));
		
		Map<String, String> attributesTwo = new HashMap<String, String>();
		double memInstanceTwo = 3.0;
		attributesTwo.put("occi.compute.memory", String.valueOf(memInstanceTwo));
		double cpuInstanceTwo = 5.0;
		attributesTwo.put("occi.compute.cores", String.valueOf(cpuInstanceTwo));		
		Instance instanceOne = new Instance("idOne", new ArrayList<Resource>(), attributesOne, new ArrayList<Instance.Link>(), InstanceState.RUNNING);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class), Mockito.eq(instanceIdOne))).thenReturn(instanceOne);
		Instance instanceTwo = new Instance("idTwo", new ArrayList<Resource>(), attributesTwo, new ArrayList<Instance.Link>(), InstanceState.RUNNING);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class), Mockito.eq(instanceIdTwo))).thenReturn(instanceTwo);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		ResourcesInfo resourcesInfoOne = new ResourcesInfo("10", "20", "10", "20", "10", "20");
		Mockito.when(computePlugin.getResourcesInfo(tokenOne)).thenReturn(resourcesInfoOne);
		ResourcesInfo resourcesInfoTwo = new ResourcesInfo("20", "20", "20", "20", "20", "20");
		Mockito.when(computePlugin.getResourcesInfo(tokenTwo)).thenReturn(resourcesInfoTwo);
		
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(instanceIdOne))).thenReturn(instanceOne);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.eq(instanceIdTwo))).thenReturn(instanceTwo);
		managerController.setComputePlugin(computePlugin);
		
		ResourcesInfo resourcesInfo = managerController.getResourcesInfo(federationToken.getUser().getName(), true);
		Assert.assertEquals("40", resourcesInfo.getCpuInUse());
		Assert.assertEquals("30", resourcesInfo.getCpuIdle());
		Assert.assertEquals("40", resourcesInfo.getMemInUse());
		Assert.assertEquals("30", resourcesInfo.getMemIdle());
		Assert.assertEquals("40", resourcesInfo.getInstancesInUse());
		Assert.assertEquals("30", resourcesInfo.getInstancesIdle());
		// two times
		Assert.assertEquals("4", resourcesInfo.getInstancesInUseByUser());
		Assert.assertEquals(String.valueOf(cpuInstanceOne * 1024), resourcesInfo.getMemInUseByUser());
		Assert.assertEquals("30.0", resourcesInfo.getCpuInUseByUser());
	}	
	
	@Test
	public void testGetResourceInfoWorngCredentials() {
		MapperPlugin mapperPlugin = Mockito.mock(MapperPlugin.class);
		Map<String, Map<String, String>> fedUsersCredentials = new HashMap<String, Map<String,String>>();
		HashMap<String, String> credentialsOne = new HashMap<String, String>();
		credentialsOne.put("one", "x1");
		HashMap<String, String> credentialsTwo = new HashMap<String, String>();
		credentialsTwo.put("two", "y1");
		HashMap<String, String> credentialsTree = new HashMap<String, String>();
		HashMap<String, String> credentialsFour = new HashMap<String, String>();
		fedUsersCredentials.put("one", credentialsOne);
		fedUsersCredentials.put("two", credentialsTwo);
		fedUsersCredentials.put("three", credentialsTree);
		fedUsersCredentials.put("four", credentialsFour);
		Mockito.when(mapperPlugin.getAllLocalCredentials())
					.thenReturn(fedUsersCredentials);
		managerController.setLocalCredentailsPlugin(mapperPlugin);
				
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token tokenOne = new Token("One", new Token.User("", ""), null, null);
		Mockito.when(identityPlugin.createToken(credentialsOne)).thenReturn(tokenOne);
		Token tokenTwo = new Token("Two", new Token.User("", ""), null, null);;
		Mockito.when(identityPlugin.createToken(credentialsTwo)).thenReturn(tokenTwo);
		Mockito.when(identityPlugin.createToken(credentialsTree)).thenReturn(null);
		Mockito.when(identityPlugin.createToken(credentialsFour)).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));
		managerController.setLocalIdentityPlugin(identityPlugin);
		
		IdentityPlugin federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(federationIdentityPlugin.getToken(Mockito.anyString())).thenReturn(tokenOne);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		ResourcesInfo resourcesInfoOne = new ResourcesInfo("10", "20", "10", "20", "10", "20");
		Mockito.when(computePlugin.getResourcesInfo(tokenOne)).thenReturn(resourcesInfoOne);
		ResourcesInfo resourcesInfoTwo = new ResourcesInfo("20", "20", "20", "20", "20", "20");
		Mockito.when(computePlugin.getResourcesInfo(tokenTwo)).thenReturn(resourcesInfoTwo);
		managerController.setComputePlugin(computePlugin);
		
		ResourcesInfo resourcesInfo = managerController.getResourcesInfo("", true);
		Assert.assertEquals("40", resourcesInfo.getCpuInUse());
		Assert.assertEquals("30", resourcesInfo.getCpuIdle());
		Assert.assertEquals("40", resourcesInfo.getMemInUse());
		Assert.assertEquals("30", resourcesInfo.getMemIdle());
		Assert.assertEquals("40", resourcesInfo.getInstancesInUse());
		Assert.assertEquals("30", resourcesInfo.getInstancesIdle());
	}	
	
	@Test
	public void testInitializeManager() throws SQLException, JSONException {
		String userOne = "userOne";
		String accessIdOne = "One";
		Token federationTokenOne = new Token(accessIdOne, new Token.User(userOne, userOne), new Date(), null);
		String userTwo = "userTwo";
		String accessIdTwo = "Two";
		Token federationTokenTwo = new Token(accessIdTwo, new Token.User(userTwo, userTwo), new Date(), null);
		String instanceIdOne = "instOne";
		String instanceIdTwo = "instTwo";
		String instanceIdThree = "instThree";
		String instanceIdFive = "instFive";
		Order orderOneUserOne = new Order("One", federationTokenOne, instanceIdOne,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, new Date().getTime(), true,
				OrderState.FULFILLED, null, xOCCIAtt);
		Order orderTwoUserOne = new Order("Two", federationTokenOne, instanceIdTwo,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, new Date().getTime(), true,
				OrderState.FULFILLED, null, xOCCIAtt);
		Order orderThreeUserTwo = new Order("Three", federationTokenTwo, instanceIdThree,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, new Date().getTime(), true,
				OrderState.FULFILLED, null, xOCCIAtt);
		Order orderOPENFour = new Order("Four", federationTokenTwo, "444",
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, new Date().getTime(), true,
				OrderState.OPEN, null, xOCCIAtt);
		Order orderFiveDELETEDUserTwo = new Order("Five", federationTokenTwo, instanceIdFive,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, new Date().getTime(), true,
				OrderState.DELETED, null, xOCCIAtt);		
		managerController.getManagerDataStoreController().addOrder(orderOneUserOne);
		managerController.getManagerDataStoreController().addOrder(orderTwoUserOne);
		managerController.getManagerDataStoreController().addOrder(orderThreeUserTwo);
		managerController.getManagerDataStoreController().addOrder(orderOPENFour);
		managerController.getManagerDataStoreController().addOrder(orderFiveDELETEDUserTwo);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString()))
				.thenReturn(new Instance(""))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""));
		managerController.setComputePlugin(computePlugin);
		
		IdentityPlugin federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(federationIdentityPlugin.getToken(accessIdOne)).thenReturn(federationTokenOne);
		Mockito.when(federationIdentityPlugin.getToken(accessIdTwo)).thenReturn(federationTokenTwo);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);

		managerController.initializeManager();
		
		List<Order> ordersFromUser = managerController.getOrdersFromUser(federationTokenOne.getAccessId());
		Assert.assertEquals(2, ordersFromUser.size());
		Assert.assertEquals(OrderState.FULFILLED, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.CLOSED, ordersFromUser.get(1).getState());
		
		ordersFromUser = managerController.getOrdersFromUser(federationTokenTwo.getAccessId());
		Assert.assertEquals(2, ordersFromUser.size());
		Assert.assertEquals(OrderState.CLOSED, ordersFromUser.get(0).getState());
		Assert.assertEquals(null, ordersFromUser.get(0).getInstanceId());
		Assert.assertEquals(OrderState.OPEN, ordersFromUser.get(1).getState());
	}
	
	@Test
	public void testInitializeManagerWithAttachmentAndOrderClosed() throws SQLException, JSONException {
		String userOne = "userOne";
		String accessIdOne = "One";
		Token federationTokenOne = new Token(accessIdOne, new Token.User(userOne, userOne) , new Date(), null);
		String userTwo = "userTwo";
		String accessIdTwo = "Two";
		Token federationTokenTwo = new Token(accessIdTwo, new Token.User(userTwo, userTwo), new Date(), null);
		String instanceIdOne = "instOne";
		String instanceIdTwo = "instTwo";
		String instanceIdThree = "instThree";
		Order orderOneUserOne = new Order("One", federationTokenOne, instanceIdOne,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, new Date().getTime(), true,
				OrderState.FULFILLED, null, xOCCIAtt);
		Order orderTwoUserOne = new Order("Two", federationTokenOne, instanceIdTwo,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, new Date().getTime(), true,
				OrderState.FULFILLED, null, xOCCIAtt);
		Order orderThreeUserTwo = new Order("Three", federationTokenTwo, instanceIdThree,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, new Date().getTime(), true,
				OrderState.FULFILLED, null, xOCCIAtt);	
		managerController.getManagerDataStoreController().addOrder(orderOneUserOne);
		managerController.getManagerDataStoreController().addOrder(orderTwoUserOne);
		managerController.getManagerDataStoreController().addOrder(orderThreeUserTwo);
			
		StorageLink storageLinkOne = new StorageLink("idOne", instanceIdThree,
				"targetOne", "deviceIdOne", "providerMemberIdOne", federationTokenOne, true);
		StorageLink storageLinkTwo = new StorageLink("idTwo", instanceIdThree,
				"targetTwo", "deviceIdTwo", "providerMemberIdTwo", federationTokenOne, true);
		StorageLink storageLinkThree = new StorageLink("idThree", "sourceThree",
				"targetThree", "deviceIdThree", "providerMemberIdThree", federationTokenOne, true);
		managerController.getManagerDataStoreController().addStorageLink(storageLinkOne);
		managerController.getManagerDataStoreController().addStorageLink(storageLinkTwo);
		managerController.getManagerDataStoreController().addStorageLink(storageLinkThree);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class), Mockito.anyString()))
				.thenReturn(new Instance("")).thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""));
		managerController.setComputePlugin(computePlugin);
		
		IdentityPlugin federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(federationIdentityPlugin.getToken(accessIdOne)).thenReturn(federationTokenOne);
		Mockito.when(federationIdentityPlugin.getToken(accessIdTwo)).thenReturn(federationTokenTwo);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);

		managerController.initializeManager();
		
		Assert.assertEquals(1, managerController.getManagerDataStoreController().getAllStorageLinks().size());
		
		List<Order> ordersFromUser = managerController.getOrdersFromUser(federationTokenOne.getAccessId());
		Assert.assertEquals(2, ordersFromUser.size());
		Assert.assertEquals(OrderState.FULFILLED, ordersFromUser.get(0).getState());
		Assert.assertEquals(OrderState.CLOSED, ordersFromUser.get(1).getState());
		
		ordersFromUser = managerController.getOrdersFromUser(federationTokenTwo.getAccessId());
		Assert.assertEquals(1, ordersFromUser.size());
		Assert.assertEquals(OrderState.CLOSED, ordersFromUser.get(0).getState());
	}	
	
	@Test
	public void testInitializeStorageLinks() throws SQLException, JSONException {
		Token federationTokenOne = new Token("accessId", new Token.User("user", "user"), new Date(), null);
		StorageLink storageLinkOne = new StorageLink("idOne", "instanceIdThree",
				"targetOne", "deviceIdOne", "providerMemberIdOne", federationTokenOne, true);
		StorageLink storageLinkTwo = new StorageLink("idTwo", "instanceIdThree",
				"targetTwo", "deviceIdTwo", "providerMemberIdTwo", federationTokenOne, true);
		StorageLink storageLinkThree = new StorageLink("idThree", "sourceThree",
				"targetThree", "deviceIdThree", "providerMemberIdThree", federationTokenOne, true);
		managerController.getManagerDataStoreController().addStorageLink(storageLinkOne);
		managerController.getManagerDataStoreController().addStorageLink(storageLinkTwo);
		managerController.getManagerDataStoreController().addStorageLink(storageLinkThree);	
		
		managerController.initializeStorageLinks();
		
		Assert.assertEquals(3, managerController.getManagerDataStoreController().getAllStorageLinks().size());
	}
	
	@Test
	public void testGetFederationMemberQuota() {
		String federationMemberId = "anyonemember";
		String accessId = "access_id";
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String cpuIdle = "1";
		String cpuInUse = "2";
		String memIdle = "3";
		String memInUse = "4";
		String instancesIdle = "5";
		String instancesInUse = "6";
		String cpuInUseByUser = "7";
		String memInUseByUser = "8";
		String instancesInUseByUser = "9";
		ResourcesInfo resourceInfo = new ResourcesInfo("id" ,cpuIdle, cpuInUse, memIdle, memInUse, instancesIdle,
				instancesInUse, cpuInUseByUser, memInUseByUser, instancesInUseByUser);
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourceInfo);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token token = new Token(accessId, new Token.User("user", "user"), new Date(), new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(Mockito.eq(accessId))).thenReturn(token);

		managerController.setPacketSender(generateRemoteQuotaResponse(resourceInfo));
		managerController.setComputePlugin(computePlugin);
		managerController.setFederationIdentityPlugin(identityPlugin);
		FederationMember federationMemberQuota = managerController.getFederationMemberQuota(federationMemberId, accessId);
		Assert.assertEquals(cpuIdle, federationMemberQuota.getResourcesInfo().getCpuIdle());
		Assert.assertEquals(cpuInUse, federationMemberQuota.getResourcesInfo().getCpuInUse());
		Assert.assertEquals(cpuInUseByUser, federationMemberQuota.getResourcesInfo().getCpuInUseByUser());
		Assert.assertEquals(memIdle, federationMemberQuota.getResourcesInfo().getMemIdle());
		Assert.assertEquals(memInUse, federationMemberQuota.getResourcesInfo().getMemInUse());
		Assert.assertEquals(memInUseByUser, federationMemberQuota.getResourcesInfo().getMemInUseByUser());
		Assert.assertEquals(instancesIdle, federationMemberQuota.getResourcesInfo().getInstancesIdle());
		Assert.assertEquals(instancesInUse, federationMemberQuota.getResourcesInfo().getInstancesInUse());
		Assert.assertEquals(instancesInUseByUser, federationMemberQuota.getResourcesInfo().getInstancesInUseByUser());
	}
	
	@Test
	public void testGetFederationMemberQuotaOwnMember() {
		String federationMemberId = DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL;
		String accessId = "access_id";
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String cpuIdle = "1";
		String cpuInUse = "2";
		String memIdle = "3";
		String memInUse = "4";
		String instancesIdle = "5";
		String instancesInUse = "6";
		ResourcesInfo resourceInfo = new ResourcesInfo(cpuIdle, cpuInUse, memIdle, memInUse, instancesIdle, instancesInUse);
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourceInfo);
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token token = new Token(accessId, new Token.User("user", "user"), new Date(), new HashMap<String, String>());
		Mockito.when(identityPlugin.getToken(Mockito.eq(accessId))).thenReturn(token);

		managerController.setComputePlugin(computePlugin);
		managerController.setFederationIdentityPlugin(identityPlugin);
		FederationMember federationMemberQuota = managerController.getFederationMemberQuota(federationMemberId, accessId);
		Assert.assertEquals(cpuIdle, federationMemberQuota.getResourcesInfo().getCpuIdle());
		Assert.assertEquals(cpuInUse, federationMemberQuota.getResourcesInfo().getCpuInUse());
		Assert.assertEquals(memIdle, federationMemberQuota.getResourcesInfo().getMemIdle());
		Assert.assertEquals(memInUse, federationMemberQuota.getResourcesInfo().getMemInUse());
		Assert.assertEquals(instancesIdle, federationMemberQuota.getResourcesInfo().getInstancesIdle());
		Assert.assertEquals(instancesInUse, federationMemberQuota.getResourcesInfo().getInstancesInUse());
	}		
	
	@Test
	public void testGetFederationMemberQuotaException() {
		String federationMemberId = "anyonemember";
		String accessId = "access_id";
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String cpuIdle = "1";
		String cpuInUse = "2";
		String memIdle = "3";
		String memInUse = "4";
		String instancesIdle = "5";
		String instancesInUse = "6";
		ResourcesInfo resourceInfo = new ResourcesInfo(cpuIdle, cpuInUse, memIdle, memInUse, instancesIdle, instancesInUse);
		resourceInfo.setId("id");
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourceInfo);

		
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		Packet response = Mockito.mock(Packet.class);
		Mockito.when(response.getError()).thenReturn(new PacketError(Condition.bad_request , org.xmpp.packet.PacketError.Type.continue_processing));
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);
		managerController.setPacketSender(packetSender);
		managerController.setComputePlugin(computePlugin);
		FederationMember federationMemberQuota = managerController.getFederationMemberQuota(federationMemberId, accessId);
		Assert.assertNull(federationMemberQuota);
	}			
	
	private AsyncPacketSender generateRemoteQuotaResponse(ResourcesInfo resourcesInfo) {
		IQ iq = new IQ();
		iq.setTo(resourcesInfo.getId());
		iq.setType(Type.get);
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
		Element tokenEl = queryEl.addElement("token");
		tokenEl.addElement("accessId").setText("auth_token");
		tokenEl.addElement("user").setText("user");

		IQ response = IQ.createResultIQ(iq);
		queryEl = response.getElement().addElement("query",
				ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
		Element resourceEl = queryEl.addElement("resourcesInfo");

		resourceEl.addElement("id").setText("id");
		resourceEl.addElement("cpuIdle").setText(resourcesInfo.getCpuIdle());
		resourceEl.addElement("cpuInUse").setText(resourcesInfo.getCpuInUse());
		resourceEl.addElement(ManagerPacketHelper.CPU_IN_USE_BY_USER).setText(resourcesInfo.getCpuInUseByUser());
		resourceEl.addElement("instancesIdle").setText(resourcesInfo.getInstancesIdle());
		resourceEl.addElement("instancesInUse").setText(resourcesInfo.getInstancesInUse());
		resourceEl.addElement(ManagerPacketHelper.MEM_IN_USE_BY_USER).setText(resourcesInfo.getMemInUseByUser());
		resourceEl.addElement("memIdle").setText(resourcesInfo.getMemIdle());
		resourceEl.addElement("memInUse").setText(resourcesInfo.getMemInUse());			
		resourceEl.addElement(ManagerPacketHelper.INSTANCES_IN_USE_BY_USER).setText(resourcesInfo.getInstancesInUseByUser());			
		
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);
		return packetSender;		
	}
	
	@Test
	public void testGetRendezvousMembersInfo() {
		ArrayList<FederationMember> items = new ArrayList<FederationMember>();
		String prefix = "user";
		int cont = 10;
		for (int i = 0; i < cont; i++) {
			items.add(new FederationMember(prefix + i));
		}

		managerController.updateMembers(items);

		List<FederationMember> rendezvousMembersInfo = managerController.getRendezvousMembers();
		Assert.assertEquals(11, rendezvousMembersInfo.size());
		Assert.assertTrue(rendezvousMembersInfo.contains(new FederationMember(
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL)));
		for (int i = 0; i < cont; i++) {
			Assert.assertTrue(rendezvousMembersInfo.contains(new FederationMember(prefix + i)));
		}
	}

	@Test
	public void testGetRendezvousMembersInfoOnlyYourself() {
		List<FederationMember> rendezvousMembersInfo = managerController.getRendezvousMembers();
		Assert.assertEquals(1, rendezvousMembersInfo.size());
		Assert.assertTrue(rendezvousMembersInfo.contains(new FederationMember(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL)));
	}
	
	@Test(expected=OCCIException.class)
	public void testGetAccountingInvalidAdminUser() {
		managerController.getAccountingInfo("invalid_admin_user", OrderConstants.COMPUTE_TERM);
	}
	
	@Test
	public void testGetAccountingValidAdminUser() {
		// mocking accountingPlugin
		AccountingPlugin accountingPlugin = managerTestHelper.getAccountingPlugin();
		List<AccountingInfo> expectedAccounting = new ArrayList<AccountingInfo>();
		AccountingInfo accountingInfo = new AccountingInfo(DefaultDataTestHelper.FED_USER_NAME, "localMember", "remoteMember1");
		accountingInfo.addConsumption(50);
		expectedAccounting.add(accountingInfo);
		accountingInfo = new AccountingInfo("user2", "localMember", "remoteMember1");
		accountingInfo.addConsumption(30);
		expectedAccounting.add(accountingInfo);
		
		Mockito.when(accountingPlugin.getAccountingInfo()).thenReturn(expectedAccounting );
				
		List<AccountingInfo> returnedAccounting = managerController
				.getAccountingInfo(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				OrderConstants.COMPUTE_TERM);
		
		// checking accounting
		Assert.assertEquals(2, returnedAccounting.size());
		Assert.assertEquals(expectedAccounting, returnedAccounting);
	}
		
	@Test
	public void testGetAccountingMoreThanOneValidAdminUser() {
		// mocking accountingPlugin
		AccountingPlugin accountingPlugin = managerTestHelper.getAccountingPlugin();
		List<AccountingInfo> expectedAccounting = new ArrayList<AccountingInfo>();
		AccountingInfo accountingInfo = new AccountingInfo(DefaultDataTestHelper.FED_USER_NAME, "localMember", "remoteMember1");
		accountingInfo.addConsumption(50);
		expectedAccounting.add(accountingInfo);
		accountingInfo = new AccountingInfo("user2", "localMember", "remoteMember1");
		accountingInfo.addConsumption(30);
		expectedAccounting.add(accountingInfo);
		
		Mockito.when(accountingPlugin.getAccountingInfo()).thenReturn(expectedAccounting );
		
		// mocking identity to allow admin user authentication
		Token adminToken = new Token("admin_access_id", new Token.User("admin_user", "admin_user"), new Date(),
				new HashMap<String, String>());
		IdentityPlugin federationIdentityPlugin = managerTestHelper.getFederationIdentityPlugin();
		Mockito.when(federationIdentityPlugin.getToken("admin_access_id")).thenReturn(adminToken);
		IdentityPlugin identityPlugin = managerTestHelper.getIdentityPlugin();
		Mockito.when(identityPlugin.getToken("admin_access_id")).thenReturn(adminToken);
		
		// checking return of admin_user 1
		List<AccountingInfo> returnedAccounting = managerController
				.getAccountingInfo(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				OrderConstants.COMPUTE_TERM);
		Assert.assertEquals(2, returnedAccounting.size());
		Assert.assertEquals(expectedAccounting, returnedAccounting);
	
		// checking return of admin_user 2
		returnedAccounting = managerController.getAccountingInfo("admin_access_id", OrderConstants.COMPUTE_TERM);
		Assert.assertEquals(2, returnedAccounting.size());
		Assert.assertEquals(expectedAccounting, returnedAccounting);
	}
	
	@Test
	public void testGetAdminUsersFromFile() throws IOException {
		String propertiesFile = "src/test/resources/accounting/fake_properties1";
		
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(propertiesFile);
		properties.load(input);

		managerController = new ManagerController(properties, null);
		
		String user = "CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR";
		Assert.assertTrue(managerController.isAdminUser(new Token("access_id",
				new Token.User("", user), new Date(), new HashMap<String, String>())));
	}
	
	@Test
	public void testGetMoreThanOneAdminUsersFromFile() throws IOException {
		String propertiesFile = "src/test/resources/accounting/fake_properties2";

		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(propertiesFile);
		properties.load(input);

		managerController = new ManagerController(properties, null);
		
		String userOne = "CN=Giovanni Farias, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR";
		Assert.assertTrue(managerController.isAdminUser(new Token("access_id1",
				new Token.User(userOne, userOne), new Date(), new HashMap<String, String>())));
		
		String userTwo = "CN=Marcos Nobrega, OU=DSC, O=UFCG, O=UFF BrGrid CA, O=ICPEDU, C=BR";
		Assert.assertTrue(managerController.isAdminUser(new Token("access_id2",
				new Token.User("", userTwo), new Date(), new HashMap<String, String>())));
	}
	
	@Test
	public void testGetResourceInfoInUseByUser() {
		Token token = new Token("accessId", new Token.User("user", "user"), 
				new Date(), new HashMap<String, String>());		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		String instanceIdOne = "instanceOne";
		String instanceIdTwo = "instanceTwo";
		
		Order orderOne = new Order("idOne", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.OPEN);
		orderOne.setInstanceId(instanceIdOne);
		Order orderTwo = new Order("idTwo", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderTwo.setState(OrderState.OPEN);
		orderTwo.setInstanceId(instanceIdTwo);
		Order orderThreeWithoutInstanceId = new Order("idThree", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderThreeWithoutInstanceId.setState(OrderState.OPEN);		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);
		managerDataStoreController.addOrder(orderTwo);

		Map<String, String> attributesOne = new HashMap<String, String>();
		double memInstanceOne = 2.0;
		attributesOne.put("occi.compute.memory", String.valueOf(memInstanceOne));
		double cpuInstanceOne = 10.0;
		attributesOne.put("occi.compute.cores", String.valueOf(cpuInstanceOne));
		
		Map<String, String> attributesTwo = new HashMap<String, String>();
		double memInstanceTwo = 3.0;
		attributesTwo.put("occi.compute.memory", String.valueOf(memInstanceTwo));
		double cpuInstanceTwo = 5.0;
		attributesTwo.put("occi.compute.cores", String.valueOf(cpuInstanceTwo));		
		Instance instanceOne = new Instance("idOne", new ArrayList<Resource>(), attributesOne, new ArrayList<Instance.Link>(), InstanceState.RUNNING);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class), Mockito.eq(instanceIdOne))).thenReturn(instanceOne);
		Instance instanceTwo = new Instance("idTwo", new ArrayList<Resource>(), attributesTwo, new ArrayList<Instance.Link>(), InstanceState.RUNNING);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class), Mockito.eq(instanceIdTwo))).thenReturn(instanceTwo);
		
		ResourcesInfo resourceInfoExtra = managerController.getResourceInfoInUseByUserId(token, token.getUser().getName(), true);
		
		Assert.assertEquals(String.valueOf(memInstanceOne * 1024 + memInstanceTwo * 1024), resourceInfoExtra.getMemInUseByUser());
		Assert.assertEquals(String.valueOf(2), resourceInfoExtra.getInstancesInUseByUser());
		Assert.assertEquals(String.valueOf(cpuInstanceOne + cpuInstanceTwo), resourceInfoExtra.getCpuInUseByUser());
	}
	
	@Test
	public void testGetResourceInfoInUseByUserWithGetInstanceError() {
		Token token = new Token("accessId", new Token.User("user", "user"), new Date(), new HashMap<String, String>());		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		String instanceIdOne = "instanceOne";
		String instanceIdTwo = "instanceTwo";
		
		Order orderOne = new Order("idOne", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.OPEN);
		orderOne.setInstanceId(instanceIdOne);
		Order orderTwo = new Order("idTwo", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderTwo.setState(OrderState.OPEN);
		orderTwo.setInstanceId(instanceIdTwo);
		Order orderThreeWithoutInstanceId = new Order("idThree", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderThreeWithoutInstanceId.setState(OrderState.OPEN);		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);
		managerDataStoreController.addOrder(orderTwo);

		Map<String, String> attributesOne = new HashMap<String, String>();
		double memInstanceOne = 2.0;
		attributesOne.put("occi.compute.memory", String.valueOf(memInstanceOne));
		double cpuInstanceOne = 10.0;
		attributesOne.put("occi.compute.cores", String.valueOf(cpuInstanceOne));
		
		Map<String, String> attributesTwo = new HashMap<String, String>();
		double memInstanceTwo = 3.0;
		attributesTwo.put("occi.compute.memory", String.valueOf(memInstanceTwo));
		double cpuInstanceTwo = 5.0;
		attributesTwo.put("occi.compute.cores", String.valueOf(cpuInstanceTwo));		
		Instance instanceOne = new Instance("idOne", new ArrayList<Resource>(),
				attributesOne, new ArrayList<Instance.Link>(),
				InstanceState.RUNNING);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(
				Mockito.any(Token.class), Mockito.eq(instanceIdOne)))
				.thenReturn(instanceOne);
		@SuppressWarnings("unused")
		Instance instanceTwo = new Instance("idTwo", new ArrayList<Resource>(),
				attributesTwo, new ArrayList<Instance.Link>(),
				InstanceState.RUNNING);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(
				Mockito.any(Token.class), Mockito.eq(instanceIdTwo)))
				.thenThrow(new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		ResourcesInfo resourceInfoExtra = managerController.getResourceInfoInUseByUserId(token, token.getUser().getName(), true);
		
		Assert.assertEquals(String.valueOf(memInstanceOne * 1024), resourceInfoExtra.getMemInUseByUser());
		Assert.assertEquals(String.valueOf(1), resourceInfoExtra.getInstancesInUseByUser());
		Assert.assertEquals(String.valueOf(cpuInstanceOne), resourceInfoExtra.getCpuInUseByUser());
	}
	
	@Test
	public void testGetResourceInfoInUseByUserWithDoubleValueConvertError() {
		Token token = new Token("accessId", new Token.User("user", "user"), new Date(), 
				new HashMap<String, String>());		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		String instanceIdOne = "instanceOne";
		String instanceIdTwo = "instanceTwo";
		
		Order orderOne = new Order("idOne", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.OPEN);
		orderOne.setInstanceId(instanceIdOne);
		Order orderTwo = new Order("idTwo", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderTwo.setState(OrderState.OPEN);
		orderTwo.setInstanceId(instanceIdTwo);
		Order orderThreeWithoutInstanceId = new Order("idThree", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderThreeWithoutInstanceId.setState(OrderState.OPEN);		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);
		managerDataStoreController.addOrder(orderTwo);
		
		Map<String, String> attributesOne = new HashMap<String, String>();
		attributesOne.put("occi.compute.memory", "wrong");
		double cpuInstanceOne = 10.0;
		attributesOne.put("occi.compute.cores", String.valueOf(cpuInstanceOne));
		
		Map<String, String> attributesTwo = new HashMap<String, String>();
		double memInstanceTwo = 3.0;
		attributesTwo.put("occi.compute.memory", String.valueOf(memInstanceTwo));
		attributesTwo.put("occi.compute.cores", "wrong");		
		Instance instanceOne = new Instance("idOne", new ArrayList<Resource>(),
				attributesOne, new ArrayList<Instance.Link>(), InstanceState.RUNNING);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(
				Mockito.any(Token.class), Mockito.eq(instanceIdOne))).thenReturn(instanceOne);
		Instance instanceTwo = new Instance("idTwo", new ArrayList<Resource>(),
				attributesTwo, new ArrayList<Instance.Link>(), InstanceState.RUNNING);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(
				Mockito.any(Token.class), Mockito.eq(instanceIdTwo))).thenReturn(instanceTwo);
		
		ResourcesInfo resourceInfoExtra = managerController.getResourceInfoInUseByUserId(token, 
				token.getUser().getName(), true);
		
		Assert.assertEquals(String.valueOf(memInstanceTwo * 1024), resourceInfoExtra.getMemInUseByUser());
		Assert.assertEquals(String.valueOf(2), resourceInfoExtra.getInstancesInUseByUser());
		Assert.assertEquals(String.valueOf(cpuInstanceOne), resourceInfoExtra.getCpuInUseByUser());
	}	
	
	@Test
	public void testGetResourceInfoInUseByUserWithOrdersEmpty() {
		Token token = new Token("accessId", new Token.User("user", "user"), 
				new Date(), new HashMap<String, String>());		
		ResourcesInfo resourceInfoExtra = managerController.getResourceInfoInUseByUserId(
				token, token.getUser().getId(), true);
		
		Assert.assertEquals(String.valueOf(0.0), resourceInfoExtra.getMemInUseByUser());
		Assert.assertEquals(String.valueOf(0), resourceInfoExtra.getInstancesInUseByUser());
		Assert.assertEquals(String.valueOf(0.0), resourceInfoExtra.getCpuInUseByUser());
	}
	
	@Test
	public void testInstanceRemovedWithAttachment() {
		Token federationToken = new Token("accessId", new Token.User("user", "user"), new Date(), null);
		String instanceId = "instanceId";
		Order order = new Order("id", federationToken, instanceId, "providingMemberId", "requestingMemberId",
				new Date().getTime(), true, OrderState.OPEN, null, xOCCIAtt);
		
		StorageLink storageLinkOne = new StorageLink("idOne", instanceId,
				"targetOne", "deviceIdOne", "provadingMemberIdOne", federationToken, true);
		StorageLink storageLinkTwo = new StorageLink("idTwo", instanceId,
				"targetTwo", "deviceIdTwo", "provadingMemberIdTwo", federationToken, true);	
		StorageLink storageLinkThree = new StorageLink("idTree", "instanceIdThree",
				"targetThree", "deviceIdThree", "provadingMemberIdThree", federationToken, true);			
		managerController.getManagerDataStoreController().addStorageLink(storageLinkOne);
		managerController.getManagerDataStoreController().addStorageLink(storageLinkTwo);
		managerController.getManagerDataStoreController().addStorageLink(storageLinkThree);
		
		Assert.assertEquals(3, managerController.getManagerDataStoreController().getAllStorageLinks().size());
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""));
		managerController.instanceRemoved(order);
		
		Assert.assertEquals(1, managerController.getManagerDataStoreController().getAllStorageLinks().size());		
	}	
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitNetworkOrders() throws InterruptedException {
		
		String newResourceId = "networkid-01";
		Token token = managerTestHelper.getDefaultFederationToken();
		
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);
		
		Mockito.when(
				networkPlugin.requestInstance(Mockito.any(Token.class),
						Mockito.anyList(), Mockito.anyMap()))
				.thenReturn(newResourceId);
		managerController.setNetworkPlugin(networkPlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		FederationMember federationMember = new FederationMember(resourcesInfo);
		listMembers.add(federationMember);
		managerController.updateMembers(listMembers);

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		Order orderOne = new Order("idA", token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.OPEN);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);

		managerController.checkAndSubmitOpenOrders();
		
		List<Order> ordersFromUser = managerController.getOrdersFromUser(token.getAccessId());
		Assert.assertEquals(1,ordersFromUser.size());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.FULFILLED, order.getState());
			Assert.assertEquals(newResourceId, order.getInstanceId());
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitFederatedNetworkOrders() throws InterruptedException {
		
		String newResourceId = "networkid-01";
		Token token = managerTestHelper.getDefaultFederationToken();
		
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);
		
		Mockito.when(
				networkPlugin.requestInstance(Mockito.any(Token.class),
						Mockito.anyList(), Mockito.anyMap()))
				.thenReturn(newResourceId);
		managerController.setNetworkPlugin(networkPlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		FederationMember federationMember = new FederationMember(resourcesInfo);
		listMembers.add(federationMember);
		managerController.updateMembers(listMembers);

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		Order orderOne = new Order("idA", token, new ArrayList<Category>(), xOCCIAtt, false, "");
		orderOne.setState(OrderState.OPEN);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);
		managerController.checkAndSubmitOpenOrders();
		
		List<Order> ordersFromUser = managerController.getOrdersFromUser(token.getAccessId(), false);
		Assert.assertEquals(1,ordersFromUser.size());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.FULFILLED, order.getState());
			Assert.assertEquals(newResourceId, order.getInstanceId());
		}
	}
	
	@Test
	public void testRemoveNetworkOrderForRemoteMember() throws InterruptedException {
		
		String newResourceId = "networkid-01";
		Token token = managerTestHelper.getDefaultFederationToken();
		String orderId = "idA";

		Order orderOne = new Order(orderId, token, new ArrayList<Category>(), xOCCIAtt, false, "");
		orderOne.setState(OrderState.FULFILLED);
		orderOne.setInstanceId(newResourceId);
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);
		
		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);
		managerController.setNetworkPlugin(networkPlugin);
		
		managerController.removeOrderForRemoteMember(token.getAccessId(), orderId);
		
		Mockito.verify(networkPlugin, Mockito.times(1)).removeInstance(token, newResourceId);
	}
	
	@Test
	public void testGetNetworkInstance() throws InterruptedException {
		
		String newResourceId = "networkid-01";
		Token token = managerTestHelper.getDefaultFederationToken();
		String orderId = "idA";
		String providingMemberId = "manager.test.com";

		Order orderOne = new Order(orderId, token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.FULFILLED);
		orderOne.setInstanceId(newResourceId);
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		orderOne.setFederationToken(token);
		orderOne.setProvidingMemberId(providingMemberId);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);

		String completeId = orderOne.getInstanceId() + Order.SEPARATOR_GLOBAL_ID + orderOne.getProvidingMemberId();
		
		Instance instance = new Instance(completeId);
		
		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);
		Mockito.when(
				networkPlugin.getInstance(token, newResourceId)).thenReturn(instance);
		
		managerController.setNetworkPlugin(networkPlugin);
		
		Instance returned = managerController.getInstance(token.getAccessId(), completeId, OrderConstants.NETWORK_TERM);
		
		Mockito.verify(networkPlugin, Mockito.times(1)).getInstance(token, newResourceId);
		Assert.assertNotNull(returned);
		Assert.assertEquals(completeId, returned.getId());
	}
	
	@Test
	public void testRemoveNetworkInstance() {
		
		//Constants
		String newResourceId = "networkid-01";
		Token token = managerTestHelper.getDefaultFederationToken();
		String orderId = "idA";
		String providingMemberId = "manager.test.com";
		String completeId = newResourceId + Order.SEPARATOR_GLOBAL_ID + providingMemberId;
		
		//Returns
		List<StorageLink> storageLinksReturn = new ArrayList<StorageLink>();
		
		ManagerDataStoreController managerDataStoreControllerMock = Mockito.mock(ManagerDataStoreController.class);
		Mockito.when(
				managerDataStoreControllerMock.getAllStorageLinkByInstance(newResourceId, 
				OrderConstants.NETWORK_INTERFACE_TERM)).thenReturn(storageLinksReturn);
		
		Order orderOne = new Order(orderId, token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.FULFILLED);
		orderOne.setInstanceId(newResourceId);
		orderOne.setResourceKing(OrderConstants.NETWORK_TERM);
		orderOne.setFederationToken(token);
		orderOne.setProvidingMemberId(providingMemberId);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);

		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);
		
		managerController.setNetworkPlugin(networkPlugin);
		
		managerController.removeInstance(token.getAccessId(), completeId, OrderConstants.NETWORK_TERM);
		
		Mockito.verify(networkPlugin, Mockito.times(1)).removeInstance(token, newResourceId);
		
	}
	
	@Test
	public void testRemoveRemoteNetworkInstance() {
		
		//Constants
		String newResourceId = "networkid-01";
		Token token = managerTestHelper.getDefaultFederationToken();
		String orderId = "idA";
		String providingMemberId = "manager.remote.test.com";
		String completeId = newResourceId + Order.SEPARATOR_GLOBAL_ID + providingMemberId;
		
		//Returns & Mocks
		List<StorageLink> storageLinksReturn = new ArrayList<StorageLink>();
		
		ManagerDataStoreController managerDataStoreControllerMock = Mockito.mock(ManagerDataStoreController.class);
		Mockito.when(
				managerDataStoreControllerMock.getAllStorageLinkByInstance(newResourceId, 
				OrderConstants.NETWORK_INTERFACE_TERM)).thenReturn(storageLinksReturn);
		
		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);
		AsyncPacketSender asyncPacketSender = Mockito.mock(AsyncPacketSender.class);
		IQ response = new IQ();
		Mockito.when(
				asyncPacketSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);
		
		Order orderOne = new Order(orderId, token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.FULFILLED);
		orderOne.setInstanceId(newResourceId);
		orderOne.setResourceKing(OrderConstants.NETWORK_TERM);
		orderOne.setFederationToken(token);
		orderOne.setProvidingMemberId(providingMemberId);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);

		managerController.setNetworkPlugin(networkPlugin);
		managerController.setPacketSender(asyncPacketSender);
		
		managerController.removeInstance(token.getAccessId(), completeId, OrderConstants.NETWORK_TERM);
		
		Mockito.verify(asyncPacketSender, Mockito.times(1)).syncSendPacket(Mockito.any(IQ.class));
		
	}
	
	@Test
	public void testRemoveNetworkInstanceForRemoteMember() {
		
		//Constants
		String instanceId = "networkid-01";
		Token token = managerTestHelper.getDefaultFederationToken();
		String orderId = "idA";
		String providingMemberId = "manager.test.com";
		
		//Returns
		List<StorageLink> storageLinksReturn = new ArrayList<StorageLink>();
		
		ManagerDataStoreController managerDataStoreControllerMock = Mockito.mock(ManagerDataStoreController.class);
		Mockito.when(
				managerDataStoreControllerMock.getAllStorageLinkByInstance(instanceId, 
				OrderConstants.NETWORK_INTERFACE_TERM)).thenReturn(storageLinksReturn);
		
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);
		
		Order orderOne = new Order(orderId, token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.FULFILLED);
		orderOne.setInstanceId(instanceId);
		orderOne.setResourceKing(OrderConstants.NETWORK_TERM);
		orderOne.setFederationToken(token);
		orderOne.setProvidingMemberId(providingMemberId);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);

		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);
		
		managerController.setNetworkPlugin(networkPlugin);
		
		managerController.removeInstanceForRemoteMember(instanceId);
		
		Mockito.verify(networkPlugin, Mockito.times(1)).removeInstance(token, instanceId);
		
	}
	
	
	@SuppressWarnings("unchecked")
	@Test
	public void testCheckAndSubmitOpenNetworkOrdersToRemoteMember() {
		
		String INSTANCE_ID = "newinstanceid";
		
		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSender);
		
		// mocking getRemoteInstance for running benchmarking
		IQ response = new IQ(); 
		response.setType(Type.result);
		Element queryEl = response.getElement().addElement("query", 
				ManagerXmppComponent.GETINSTANCE_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(INSTANCE_ID);
		
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(response);

		final List<PacketCallback> callbacks = new LinkedList<PacketCallback>();
		
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				callbacks.add((PacketCallback) invocation.getArguments()[1]);
				return null;
			}
		}).when(packetSender).addPacketCallback(Mockito.any(Packet.class), Mockito.any(PacketCallback.class));
		
		ResourcesInfo localResourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		localResourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ResourcesInfo remoteResourcesInfoOne = new ResourcesInfo("", "", "", "", "", "");
		remoteResourcesInfoOne.setId(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		
		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);

		managerController.setNetworkPlugin(networkPlugin);

		final List<FederationMember> listMembers = new ArrayList<FederationMember>();
		listMembers.add(new FederationMember(localResourcesInfo));
		listMembers.add(new FederationMember(remoteResourcesInfoOne));
		managerController.updateMembers(listMembers);
		
		FederationMemberPickerPlugin memberPicker = Mockito.mock(FederationMemberPickerPlugin.class);
		Mockito.when(memberPicker.pick(Mockito.anyList())).thenReturn(
				new FederationMember(remoteResourcesInfoOne));
		managerController.setMemberPickerPlugin(memberPicker );

		final String orderId = "orderId";
		Order orderOne = new Order(orderId, managerTestHelper.getDefaultFederationToken(),
				new ArrayList<Category>(),
				new HashMap<String, String>(), true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		orderOne.setState(OrderState.OPEN);
		orderOne.setResourceKing(OrderConstants.NETWORK_TERM);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);
		
		// Send to first member
		managerController.checkAndSubmitOpenOrders();

		List<Order> ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.PENDING, order.getState());
		}
		Assert.assertTrue(managerController.isOrderForwardedtoRemoteMember(orderOne.getId()));			
		
		AsyncPacketSender packetSenderTwo = Mockito.mock(AsyncPacketSender.class);
		managerController.setPacketSender(packetSenderTwo);
		
		IQ iq = new IQ();
		queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.ORDER_NAMESPACE);
		instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText(INSTANCE_ID);
		callbacks.get(0).handle(iq);
		
		ordersFromUser = managerController.getOrdersFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Order order : ordersFromUser) {
			Assert.assertEquals(OrderState.FULFILLED, order.getState());
			Assert.assertFalse(managerController.isOrderForwardedtoRemoteMember(order.getId()));
		}
	}

	@Test
	public void getInstanceForRemoteMember() {

		//Constants
		String instanceId = "networkid-01";
		Token token = managerTestHelper.getDefaultFederationToken();
		String orderId = "idA";
		String providingMemberId = "manager.test.com";

		//Returns
		List<StorageLink> storageLinksReturn = new ArrayList<StorageLink>();

		ManagerDataStoreController managerDataStoreControllerMock = Mockito.mock(ManagerDataStoreController.class);
		Mockito.when(
				managerDataStoreControllerMock.getAllStorageLinkByInstance(instanceId, 
				OrderConstants.NETWORK_INTERFACE_TERM)).thenReturn(storageLinksReturn);

		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.NETWORK_TERM);

		Order orderOne = new Order(orderId, token, new ArrayList<Category>(), xOCCIAtt, true, "");
		orderOne.setState(OrderState.FULFILLED);
		orderOne.setInstanceId(instanceId);
		orderOne.setResourceKing(OrderConstants.NETWORK_TERM);
		orderOne.setFederationToken(token);
		orderOne.setProvidingMemberId(providingMemberId);
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		managerDataStoreController.addOrder(orderOne);

		NetworkPlugin networkPlugin = Mockito.mock(NetworkPlugin.class);

		managerController.setNetworkPlugin(networkPlugin);

		managerController.getInstanceForRemoteMember(instanceId);

		Mockito.verify(networkPlugin, Mockito.times(1)).getInstance(token, instanceId);
	}
	
	@Test
	public void testCheckInstancePreempted() {
		Token federationUserToken = new Token("accessId", new Token.User("user", "user"), new Date(), null);
		String instanceId = "instanceId00";
		Order orderToPreempt = new Order("id", federationUserToken, instanceId, "providingMemberId"
				, "requestingMemberId", new Date().getTime(), true, OrderState.FULFILLED, null, xOCCIAtt);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(federationUserToken, instanceId))
				.thenReturn(new Instance(instanceId), null);
		managerController.setComputePlugin(computePlugin);
		
		long now = System.currentTimeMillis();
		managerController.checkInstancePreempted(federationUserToken, orderToPreempt);
		long after = System.currentTimeMillis();
		
		long executionTime = after - now;
		Assert.assertTrue(executionTime >= ManagerController.DEFAULT_CHECK_STILL_ALIVE_WAIT_TIME 
				&& executionTime < ManagerController.DEFAULT_CHECK_STILL_ALIVE_WAIT_TIME * 2 - 1);
	}
	
	@Test
	public void testCheckInstancePreemptedQuotaExceeded() {
		Token federationUserToken = new Token("accessId", new Token.User("user", "user"), new Date(), null);
		String instanceId = "instanceId00";
		Order orderToPreempt = new Order("id", federationUserToken, instanceId, "providingMemberId"
				, "requestingMemberId", new Date().getTime(), true, OrderState.FULFILLED, null, xOCCIAtt);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getInstance(federationUserToken, instanceId))
				.thenThrow(new OCCIException(ErrorType.QUOTA_EXCEEDED, ""));
		managerController.setComputePlugin(computePlugin);
		
		long now = System.currentTimeMillis();
		managerController.checkInstancePreempted(federationUserToken, orderToPreempt);
		long after = System.currentTimeMillis();
		
		long executionTime = after - now;
		Assert.assertTrue(executionTime < ManagerController.DEFAULT_CHECK_STILL_ALIVE_WAIT_TIME);
	}	
	
	@Test
	public void testIsThereEnoughQuotaInstancesOk() {
		Token token = new Token("accessId", new User("userId", "userName"), new Date(), null);
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		String requestingMemberId = "requesting_member_id";
		String user = "one_user";
		
		ManagerDataStoreController managerDataStoreController = managerController.getManagerDataStoreController();
		Order orderServeredComputeFullfield = new Order("id_one", token, "instance_id_one", null, requestingMemberId, 
				0, false, OrderState.FULFILLED, null, xOCCIAtt);
		orderServeredComputeFullfield.setResourceKing(OrderConstants.COMPUTE_TERM);
		managerDataStoreController.addOrder(orderServeredComputeFullfield);
		Order orderServeredOtherMemberComputeFullfield = new Order("id_two", token, "instance_id_two", null, 
				"other_requesting_member", 0, false, OrderState.FULFILLED, null, xOCCIAtt);
		orderServeredOtherMemberComputeFullfield.setResourceKing(OrderConstants.COMPUTE_TERM);
		managerDataStoreController.addOrder(orderServeredOtherMemberComputeFullfield);		
		Order orderServeredStorageFullfield = new Order("id_three", token, "instance_id_three", null, requestingMemberId, 
				0, false, OrderState.FULFILLED, null, xOCCIAtt);
		orderServeredStorageFullfield.setResourceKing(OrderConstants.STORAGE_LINK_TERM);
		managerDataStoreController.addOrder(orderServeredStorageFullfield);
		Order orderServeredComputeOpen = new Order("id_four", token, "instance_id_four", null, requestingMemberId, 
				0, false, OrderState.OPEN, null, xOCCIAtt);
		orderServeredComputeOpen.setResourceKing(OrderConstants.COMPUTE_TERM);
		managerDataStoreController.addOrder(orderServeredComputeOpen);				
		
		CapacityControllerPlugin capacityControllerPlugin = Mockito.mock(CapacityControllerPlugin.class);
		// max instances
		double maxCapacity = 3.0;
		Mockito.when(capacityControllerPlugin.getMaxCapacityToSupply(
				Mockito.any(FederationMember.class))).thenReturn(maxCapacity);
		this.managerController.setCapacityControllerPlugin(capacityControllerPlugin);
		
		// One instance
		Assert.assertTrue(this.managerController.isThereEnoughQuota(requestingMemberId));
		
		Order orderServeredComputeFullfieldOther = new Order("id_five", token, "instance_id_five", null, requestingMemberId, 
				0, false, OrderState.FULFILLED, null, xOCCIAtt);
		orderServeredComputeFullfieldOther.setResourceKing(OrderConstants.COMPUTE_TERM);
		managerDataStoreController.addOrder(orderServeredComputeFullfieldOther);
		
		// Two instance
		Assert.assertFalse(this.managerController.isThereEnoughQuota(requestingMemberId));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGetMaxCapacityDefaultUser() {
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Token token = null;
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(token);
		this.managerController.setLocalIdentityPlugin(identityPlugin);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		int instancesIdle = 3;
		int instancesInUse = 7;
		ResourcesInfo resourceInfo = new ResourcesInfo("0", "0", "0", "0", 
				String.valueOf(instancesIdle), String.valueOf(instancesInUse));
		Mockito.when(computePlugin.getResourcesInfo(token)).thenReturn(resourceInfo);
		this.managerController.setComputePlugin(computePlugin);
		int maxCapacityDefaultUser = this.managerController.getMaxCapacityDefaultUser();
		
		Assert.assertEquals(instancesIdle + instancesInUse, maxCapacityDefaultUser);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGetMaxCapacityDefaultUserWithException() {
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenThrow(
				new OCCIException(ErrorType.BAD_REQUEST, ""));
		this.managerController.setLocalIdentityPlugin(identityPlugin);
		int maxCapacityDefaultUser = this.managerController.getMaxCapacityDefaultUser();
		
		Assert.assertEquals(CapacityControllerPlugin.MAXIMUM_CAPACITY_VALUE_ERROR, maxCapacityDefaultUser);
	}
	
}
