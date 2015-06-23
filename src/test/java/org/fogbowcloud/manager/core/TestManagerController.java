package org.fogbowcloud.manager.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerController.FailedBatchType;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenStackOCCIComputePlugin;
import org.fogbowcloud.manager.core.plugins.identity.openstack.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
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
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.jamppa.component.PacketCallback;
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
import org.xmpp.packet.PacketError.Condition;

public class TestManagerController {

	public static final String ACCESS_TOKEN_ID_2 = "secondAccessToken";

	private ManagerController managerController;
	private ManagerTestHelper managerTestHelper;
	private Map<String, String> xOCCIAtt;

	@Before
	public void setUp() throws Exception {
		managerTestHelper = new ManagerTestHelper();
		
		/*
		 * Default manager controller: 
		 *  computePlugin.requestInstance always throws QuotaExceededException 
		 *  identityPlugin.getToken(AccessId) always returns DefaultDataTestHelper.ACCESS_TOKEN_ID
		 *  schedulerPeriod and monitoringPeriod are long enough (a day) to avoid reeschudeling
		 */

		managerController = managerTestHelper.createDefaultManagerController();
		// default instance count value is 1
		xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
	}

	@Test
	public void testAuthorizedUser() {		
		Token tokenFromFederationIdP = managerController
				.getTokenFromFederationIdP(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		
		Assert.assertEquals(managerTestHelper.getDefaultLocalToken().getAccessId(),
				tokenFromFederationIdP.getAccessId());
	}
	
	@Test(expected=OCCIException.class)
	public void testUnauthorizedUser() {
		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(false);
		managerController.setAuthorizationPlugin(authorizationPlugin);
		
		managerController.getTokenFromFederationIdP(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);		
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitLocalUserRequests() throws InterruptedException {
		final String localUserAccessId = "Local-User-Access-Id";
		final String localUser = "localUser";
		Token localToken = new Token(localUserAccessId, localUser, new Date(),
				new HashMap<String, String>());

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenReturn("newinstanceid");
		managerController.setComputePlugin(computePlugin);

		checkRequestPerUserToken(localToken);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitFederationUserRequests() throws InterruptedException {
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class),
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""))
				.thenReturn("newinstanceid")
				.thenReturn("newinstanceid");
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourcesInfo);
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		FederationMember federationMember = new FederationMember(resourcesInfo);
		listMembers.add(federationMember);
		managerController.updateMembers(listMembers);

		checkRequestPerUserToken(managerTestHelper.getDefaultFederationToken());
	}
	
	private void checkRequestPerUserToken(Token token) {
		Request request1 = new Request("id1", token, token, new ArrayList<Category>(),
				new HashMap<String, String>(), true, "");
		request1.setState(RequestState.OPEN);
		Request request2 = new Request("id2", token, token, new ArrayList<Category>(),
				new HashMap<String, String>(), true, "");
		request2.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(token.getUser(), request1);
		requestRepository.addRequest(token.getUser(), request2);
		managerController.setRequests(requestRepository);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(token.getAccessId());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.FULFILLED, request.getState());
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
		attributes.put(RequestAttribute.REQUIREMENTS.getValue(), "true");
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), 
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				attributes, true, "");
		request1.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		managerController.checkAndSubmitOpenRequests();
		        
		Mockito.verify(packetSender, VerificationModeFactory.times(2)).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
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
		attributes.put(RequestAttribute.REQUIREMENTS.getValue(), "Glue2vCPU >= 1 && Glue2RAM >= 1024");
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), 
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				attributes, true, "");
		request1.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		managerController.checkAndSubmitOpenRequests();
		        
		Mockito.verify(packetSender, VerificationModeFactory.times(2)).sendPacket(Mockito.argThat(new ArgumentMatcher<IQ>() {
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
	public void testSubmitRequestToRemoteMember() throws InterruptedException {
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

		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		request1.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(),
				request1);
		managerController.setRequests(requestRepository);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
		}
		Assert.assertTrue(managerController.isRequestForwardedtoRemoteMember(request1.getId()));

		IQ iq = new IQ();
		queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.REQUEST_NAMESPACE);
		instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		callbacks.get(0).handle(iq);
	
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.FULFILLED, request.getState());
			Assert.assertFalse(managerController.isRequestForwardedtoRemoteMember(request.getId()));
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRemoveForwardedRequestAfterTimeout() throws InterruptedException {
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

		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true, "");
		request1.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(), request1);
		managerController.setRequests(requestRepository);

		// mocking date
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(
				managerTestHelper.getDefaultFederationToken().getAccessId());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
		}
		Assert.assertTrue(managerController.isRequestForwardedtoRemoteMember(
				request1.getId()));
		
		//updating time
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + ManagerController.DEFAULT_ASYNC_REQUEST_WAITING_INTERVAL + 100);
		
		managerController.removeRequestsThatReachTimeout();
		
		Assert.assertFalse(managerController.isRequestForwardedtoRemoteMember(
				request1.getId()));
		
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
			Assert.assertFalse(managerController.isRequestForwardedtoRemoteMember(request.getId()));
		}
	}
		
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitRequestToRemoteMemberReturningNotFound() throws InterruptedException {
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

		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(),
				new ArrayList<Category>(), new HashMap<String, String>(), true, "");
		request1.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(),
				request1);
		managerController.setRequests(requestRepository);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultFederationToken().getAccessId());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
		}
		Assert.assertTrue(managerController.isRequestForwardedtoRemoteMember(
				request1.getId()));
		
		IQ iq = new IQ();
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.REQUEST_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		iq.setError(Condition.item_not_found);
		callbacks.get(0).handle(iq);
		
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
			Assert.assertFalse(managerController.isRequestForwardedtoRemoteMember(request.getId()));
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitRequestToRemoteMemberReturningException() throws InterruptedException {
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
		Mockito.when(identityPlugin.createFederationUserToken()).thenReturn(
				managerTestHelper.getDefaultFederationToken());
		managerController.setLocalIdentityPlugin(identityPlugin);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);

		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(),managerTestHelper.getDefaultLocalToken(),
				new ArrayList<Category>(), new HashMap<String, String>(), true, "");
		request1.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(), request1);
		managerController.setRequests(requestRepository);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(
				managerTestHelper.getDefaultFederationToken().getAccessId());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
		}
		Assert.assertTrue(managerController.isRequestForwardedtoRemoteMember(
				request1.getId()));
		
		IQ iq = new IQ();
		Element queryEl = iq.getElement().addElement("query",
				ManagerXmppComponent.REQUEST_NAMESPACE);
		Element instanceEl = queryEl.addElement("instance");
		instanceEl.addElement("id").setText("newinstanceid");
		iq.setError(Condition.bad_request);
		callbacks.get(0).handle(iq);
		
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
			Assert.assertFalse(managerController.isRequestForwardedtoRemoteMember(request.getId()));
		}
	}

	@Test
	public void testGetFederationMember() throws InterruptedException {
		Map<String, String> tokenCredentials = new HashMap<String, String>();
		tokenCredentials.put(KeystoneIdentityPlugin.USERNAME, DefaultDataTestHelper.USER_NAME);
		tokenCredentials.put(KeystoneIdentityPlugin.PASSWORD, DefaultDataTestHelper.USER_PASS);
		tokenCredentials.put(KeystoneIdentityPlugin.TENANT_NAME,
				DefaultDataTestHelper.TENANT_NAME);

		long tokenExpirationTime = System.currentTimeMillis() + 500;

		Map<String, String> attributesTokenReturn = new HashMap<String, String>();
		attributesTokenReturn.put(KeystoneIdentityPlugin.TENANT_ID, "987654321");
		attributesTokenReturn.put(KeystoneIdentityPlugin.TENANT_NAME,
				DefaultDataTestHelper.TENANT_NAME);

		Token firstToken = new Token(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.USER_NAME, new Date(tokenExpirationTime),
				attributesTokenReturn);
		Token secondToken = new Token(ACCESS_TOKEN_ID_2, DefaultDataTestHelper.USER_NAME, new Date(
				tokenExpirationTime + DefaultDataTestHelper.LONG_TIME), attributesTokenReturn);

		// mocking identity plugin
		KeystoneIdentityPlugin openStackidentityPlugin = Mockito
				.mock(KeystoneIdentityPlugin.class);
		Mockito.when(openStackidentityPlugin.createFederationUserToken()).thenReturn(firstToken,
				secondToken);
		Mockito.when(openStackidentityPlugin.createToken(tokenCredentials)).thenReturn(firstToken,
				secondToken);
		Mockito.when(openStackidentityPlugin.isValid(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID))
				.thenReturn(true, false);
		managerController.setLocalIdentityPlugin(openStackidentityPlugin);

		// Get new token
		Token federationUserToken = managerController.getFederationUserToken();
		String accessToken = federationUserToken.getAccessId();
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID, accessToken);

		// Use member token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID, accessToken);

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				tokenExpirationTime + DefaultDataTestHelper.GRACE_TIME);
		firstToken.setDateUtils(dateUtils);

		// Get new token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(ACCESS_TOKEN_ID_2, accessToken);
	}

	@Test
	public void testcheckAndUpdateRequestToken() throws InterruptedException {
		final long now = System.currentTimeMillis();
		final int tokenUpdaterInterval = 100;
		long tokenExpirationTime = now + (4 * tokenUpdaterInterval);

		Token firstToken = new Token(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.USER_NAME, new Date(tokenExpirationTime),
				new HashMap<String, String>());

		// setting request repository
		RequestRepository requestRepository = new RequestRepository();
		for (int i = 0; i < 5; i++) {
			requestRepository.addRequest(DefaultDataTestHelper.USER_NAME, new Request("id" + i,
					firstToken, null, null, null, true, ""));
		}
		managerController.setRequests(requestRepository);

		// adding behaviour to identity mock
		Token secondToken = new Token(ACCESS_TOKEN_ID_2, DefaultDataTestHelper.USER_NAME, new Date(
				tokenExpirationTime + tokenUpdaterInterval), new HashMap<String, String>());
		Mockito.when(managerTestHelper.getIdentityPlugin().reIssueToken(firstToken)).thenReturn(
				secondToken);

		// mocking date
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);

		managerController.checkAndUpdateRequestToken(tokenUpdaterInterval);

		// check if requests have firstToken
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().in(RequestState.OPEN)) {
				Assert.assertEquals(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID, request.getFederationToken()
						.getAccessId());
			} else if (request.getState().in(RequestState.CLOSED, RequestState.FAILED)) {
				Assert.assertEquals(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID, request.getFederationToken()
						.getAccessId());
			}
		}

		// updating date
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				tokenExpirationTime - tokenUpdaterInterval);

		managerController.checkAndUpdateRequestToken(tokenUpdaterInterval);

		// check if open requests have been updated to secondToken
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().in(RequestState.OPEN)) {
				Assert.assertEquals(ACCESS_TOKEN_ID_2, request.getFederationToken().getAccessId());
			} else if (request.getState().in(RequestState.CLOSED, RequestState.FAILED)) {
				Assert.assertEquals(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID, request.getFederationToken()
						.getAccessId());
			}
		}
	}
	
	@Test
	public void testDeleteClosedRequest() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setState(RequestState.CLOSED);
		Request request2 = new Request("id2", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request2.setState(RequestState.CLOSED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// checking closed requests
		List<Request> requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.CLOSED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.CLOSED, requestsFromUser.get(1).getState());

		managerController.removeRequest(managerTestHelper.getDefaultLocalToken().getAccessId(), "id1");

		// making sure one request was removed
		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.CLOSED, requestsFromUser.get(0).getState());
		Assert.assertEquals("id2", requestsFromUser.get(0).getId());
		
		managerController.removeRequest(managerTestHelper.getDefaultLocalToken().getAccessId(), "id2");

		// making sure the last request was removed
		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		Assert.assertEquals(0, requestsFromUser.size());
	}

	@Test
	public void testMonitorDeletedRequestWithInstance() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request1.setState(RequestState.DELETED);
		request1.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		Request request2 = new Request("id2", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request2.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request2.setState(RequestState.DELETED);
		request2.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenReturn(
				new Instance(DefaultDataTestHelper.INSTANCE_ID));

		// checking deleted requests
		List<Request> requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(1).getState());

		managerController.monitorInstancesForLocalRequests();

		// making sure the requests were not removed
		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(1).getState());
	}

	@Test
	public void testMonitorDeletedRequestWithoutInstance() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1",
				managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2",
				managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request2.setState(RequestState.DELETED);
		Request request3 = new Request("id3",
				managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request3.setState(RequestState.OPEN);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request2);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request3);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if requests still have the initial state
		Assert.assertEquals(
				3,
				managerController.getRequestsFromUser(
						managerTestHelper.getDefaultLocalToken().getAccessId()).size());
		Assert.assertEquals(
				RequestState.DELETED,
				managerController.getRequest(managerTestHelper.getDefaultLocalToken().getAccessId(),
						"id1").getState());
		Assert.assertEquals(
				RequestState.DELETED,
				managerController.getRequest(managerTestHelper.getDefaultLocalToken().getAccessId(),
						"id2").getState());
		Assert.assertEquals(
				RequestState.OPEN,
				managerController.getRequest(managerTestHelper.getDefaultLocalToken().getAccessId(),
						"id3").getState());

		managerController.monitorInstancesForLocalRequests();

		// checking if deleted requests were removed
		Assert.assertEquals(
				1,
				managerController.getRequestsFromUser(
						managerTestHelper.getDefaultLocalToken().getAccessId()).size());
		Assert.assertEquals(
				RequestState.OPEN,
				managerController
						.getRequestsFromUser(managerTestHelper.getDefaultLocalToken().getAccessId())
						.get(0).getState());
	}

	@Test
	public void testMonitorFulfilledRequestWithoutInstance() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultLocalToken(), null, null, null, true, "");
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", managerTestHelper.getDefaultLocalToken(), null, null, null, true, "");
		request2.setState(RequestState.FULFILLED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if requests were fulfilled
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}

		managerController.monitorInstancesForLocalRequests();

		// checking if requests were closed
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.CLOSED));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMonitorFulfilledAndPersistentRequest() throws InterruptedException {
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());

		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(), attributes, true, "");
		request1.setState(RequestState.FULFILLED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if request is fulfilled
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.FULFILLED, requestsFromUser.get(0).getState());

		managerController.monitorInstancesForLocalRequests();

		// checking if request has lost its instance
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(0).getState());

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

		// getting instance for request
		managerController.checkAndSubmitOpenRequests();

		// checking if request has been fulfilled again
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.FULFILLED, requestsFromUser.get(0).getState());
	}

	@Test
	public void testMonitorFulfilledRequestWithInstance() throws InterruptedException {
		final String SECOND_INSTANCE_ID = "secondInstanceId";

		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request1.setState(RequestState.FULFILLED);
		request1.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		Request request2 = new Request("id2", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request2.setInstanceId(SECOND_INSTANCE_ID);
		request2.setState(RequestState.FULFILLED);
		request2.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenReturn(
				new Instance(DefaultDataTestHelper.INSTANCE_ID));

		managerController.monitorInstancesForLocalRequests();

		// checking if requests are still fulfilled
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.FULFILLED, request.getState());
		}

		managerController.monitorInstancesForLocalRequests();

		// checking if requests' state haven't been changed
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}
	}
	
	@Test
	public void testMonitorWontRethrowException() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request1.setState(RequestState.FULFILLED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(new RuntimeException());

		managerController.monitorInstancesForLocalRequests();
	}
	
	@Test
	public void testMonitorWillRemoveLocalFailedInstance() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request1.setState(RequestState.FULFILLED);
		request1.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Instance expectedInstance = new Instance(DefaultDataTestHelper.INSTANCE_ID, new LinkedList<Resource>(), 
				new HashMap<String, String>(), new LinkedList<Link>(), InstanceState.FAILED);
		Mockito.when(managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenReturn(expectedInstance);

		managerController.monitorInstancesForLocalRequests();
		
		// checking if instance was properly removed
		Mockito.verify(managerTestHelper.getComputePlugin()).removeInstance(
				Mockito.any(Token.class), Mockito.eq(DefaultDataTestHelper.INSTANCE_ID));
		
		// checking if request is closed
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.CLOSED));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorException() throws Exception {
		new ManagerController(null);
	}

	@Test
	public void testGet0ItemsFromIQ() {
		managerController.updateMembers(new LinkedList<FederationMember>());
		// There is a single member which is the manager itself
		Assert.assertEquals(1, managerController.getMembers().size());
	}

	@Test
	public void testGet1ItemFromIQ() throws CertificateException, IOException {
		FederationMember managerItem = new FederationMember(managerTestHelper.getResources());
		List<FederationMember> items = new LinkedList<FederationMember>();
		items.add(managerItem);
		managerController.updateMembers(items);

		List<FederationMember> members = managerController.getMembers();
		Assert.assertEquals(2, members.size());
		Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
				members.get(1).getResourcesInfo().getId());
	}

	@Test
	public void testGetManyItemsFromIQ() throws CertificateException, IOException {
		ArrayList<FederationMember> items = new ArrayList<FederationMember>();
		for (int i = 0; i < 10; i++) {
			items.add(new FederationMember(managerTestHelper.getResources()));
		}
		managerController.updateMembers(items);

		List<FederationMember> members = managerController.getMembers();
		Assert.assertEquals(11, members.size());
		for (int i = 0; i < 10; i++) {
			Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		}
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
				members.get(10).getResourcesInfo().getId());
	}

	@Test
	public void testGetRequestsByUser() throws InterruptedException {
		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenRequests();		

		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);

		// checking if request was created
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.FED_USER_NAME, requests.get(0).getFederationToken().getUser());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());
	}


	@Test
	public void testOneTimeRequestSetFulfilledAndClosed() throws InterruptedException {
		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenRequests();
		
		// checking if request was properly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());

		// updating compute mock
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				managerTestHelper.getDefaultLocalToken(), DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID  + Request.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestSetFulfilledAndOpen() throws InterruptedException {
		mockRequestInstance();
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenRequests();

		// checking if request was properly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());

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
				DefaultDataTestHelper.INSTANCE_ID + Request.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		// checking request state was set to open
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestSetFulfilledAndOpenAndFulfilled() throws InterruptedException {
		final String SECOND_INSTANCE_ID = "rt22e67-5fgt-457a-3rt6-gt78124fhj9p";
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		// mocking compute
		mockRequestInstance();

		// creating requests
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
 		managerController.checkAndSubmitOpenRequests();

		// checking if request was fulfilled with instanceID
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());

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
				DefaultDataTestHelper.INSTANCE_ID  + Request.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		// checking if request state was set to open
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.any(Token.class),
						Mockito.any(List.class), Mockito.any(Map.class), Mockito.anyString())).thenReturn(
				SECOND_INSTANCE_ID);

		// getting second instance
		managerController.checkAndSubmitOpenRequests();

		// checking if request was fulfilled with secondInstance
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(SECOND_INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());
	}

	@Test
	public void testPersistentRequestSetOpenAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis()
				+ DefaultDataTestHelper.SCHEDULER_PERIOD;

		// setting request attributes
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				String.valueOf(DateUtils.getDateISO8601Format(expirationRequestTime)));

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if request is OPEN
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// waiting expiration time
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);

		// checking if request was closed
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestSetFulfilledAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis()
				+ DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME;

		// setting request attributes
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD);
		managerController.checkAndSubmitOpenRequests();

		// checking request is fulfilled
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());

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
				DefaultDataTestHelper.INSTANCE_ID  + Request.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		managerController.checkAndSubmitOpenRequests();

		// checking if request state was set to closed
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	private void mockRequestInstance() {
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
	public void testOneTimeRequestSetOpenAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis()
				+ DefaultDataTestHelper.SCHEDULER_PERIOD;

		// setting request attributes
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.ONE_TIME.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if request was properly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// waiting expiration time
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenRequests();
		
		// checking if request state was set to closed
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());
	}

	@Test
	public void testOneTimeRequestWithValidFromAttInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + DefaultDataTestHelper.LONG_TIME;

		// setting request attributes
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.ONE_TIME.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if request was properly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// sleeping for a time and request not valid yet
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// check that request is not in valid period yet
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// sleeping for the scheduler period and submitting request
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// check if request is in valid period
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());
	}

	@Test
	public void testPersistentRequestWithValidFromAttInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + DefaultDataTestHelper.LONG_TIME;

		// setting request attributes
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if request was rightly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// sleeping for a time and request not valid yet
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// check request is not in valid period yet
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// sleeping for the scheduler period and submitting request
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// check if request is in valid period
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOneTimeRequestValidityPeriodInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 3);
		long expirationRequestTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 6);

		// setting request attributes
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.ONE_TIME.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// request is not in valid period yet
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// sleeping for the scheduler period and submitting request
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 3 + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenRequests();
		
		// checking is request is fulfilled
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());

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
				DefaultDataTestHelper.INSTANCE_ID  + Request.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		// waiting for a time and request is not into valid period anymore
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);

		// checking if request is not in valid period anymore
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestValidityPeriodInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + (DefaultDataTestHelper.SCHEDULER_PERIOD * 4);

		// setting request attributes
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID, DefaultDataTestHelper.LOCAL_ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// request is not in valid period yet
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());

		// waiting for a time and request is into valid period
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 2 + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// checking is request is fulfilled
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNotNull(requests.get(0).getProvidingMemberId());

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
						DefaultDataTestHelper.INSTANCE_ID + Request.SEPARATOR_GLOBAL_ID
								+ DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID  + Request.SEPARATOR_GLOBAL_ID
	            + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);

		// waiting for the scheduler period so that request is not into valid period anymore
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 2 + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenRequests();
		
		// checking if request is not in valid period anymore
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getProvidingMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitRequestForRemoteMemberValidation() {
		ResourcesInfo resources = Mockito.mock(ResourcesInfo.class);
		Mockito.doReturn("abc").when(resources).getId();

		FederationMember member = Mockito.mock(FederationMember.class);
		Mockito.doReturn(resources).when(member).getResourcesInfo();
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
		
		Request request = new Request("id1", null,
				null, new ArrayList<Category>(), xOCCIAtt,
				false, "abc");
		
		Assert.assertTrue(managerController.createLocalInstanceWithFederationUser(request));

		Mockito.doReturn(false).when(validatorMock).canDonateTo(Mockito.eq(member), Mockito.any(Token.class));
		managerController.setValidator(validatorMock);
		
		Assert.assertFalse(managerController.createLocalInstanceWithFederationUser(request));
	}
		
	@SuppressWarnings("unchecked")
	@Test
	public void testReplyToServedRequestWithSuccess() {
		ResourcesInfo resources = Mockito.mock(ResourcesInfo.class);
		Mockito.doReturn("abc").when(resources).getId();

		FederationMember member = Mockito.mock(FederationMember.class);
		Mockito.doReturn(resources).when(member).getResourcesInfo();
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
		
		Request request = new Request("id1", null,
				null, new ArrayList<Category>(), xOCCIAtt,
				false, "abc");
		
		Assert.assertTrue(managerController.createLocalInstanceWithFederationUser(request));

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
	public void testReplyToServedRequestWithoutSuccess() {
		ResourcesInfo resources = Mockito.mock(ResourcesInfo.class);
		Mockito.doReturn("abc").when(resources).getId();

		FederationMember member = Mockito.mock(FederationMember.class);
		Mockito.doReturn(resources).when(member).getResourcesInfo();
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
		
		Request request = new Request("id1", null,
				null, new ArrayList<Category>(), xOCCIAtt,
				false, "abc");
		
		Assert.assertFalse(managerController.createLocalInstanceWithFederationUser(request));

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
	public void testRemoveAllOpenRequests() {
		
		managerTestHelper.useSameThreadExecutor();
		
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setState(RequestState.OPEN);
		Request request2 = new Request("id2", managerTestHelper.getDefaultFederationToken(),  managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request2.setState(RequestState.OPEN);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// checking open requests
		List<Request> requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(1).getState());

		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		// removing all requests
		managerController.removeAllRequests(managerTestHelper.getDefaultLocalToken().getAccessId());
		
		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		
		Assert.assertEquals(0, requestsFromUser.size());
	}
	
	@Test
	public void testRemoveOneOpenRequestAndAfterThatRemoveAllOpenRequests() {
		
		managerTestHelper.useSameThreadExecutor();
		
		// setting request repository
		String id1 = "id1";
		String id2 = "id2";
		String id3 = "id3";
		Request request1 = new Request(id1, managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setState(RequestState.OPEN);
		Request request2 = new Request(id2, managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request2.setState(RequestState.OPEN);
		Request request3 = new Request(id3, managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request3.setState(RequestState.OPEN);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request2);
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request3);
		managerController.setRequests(requestRepository);

		// checking open requests
		List<Request> requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		Assert.assertEquals(3, requestsFromUser.size());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(1).getState());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(2).getState());

		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.any(Token.class),
						Mockito.anyString())).thenThrow(new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		// removing one request 
		managerController.removeRequest(managerTestHelper.getDefaultLocalToken().getAccessId(), id1);

		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(1).getState());
	
		// removing the rest of requests
		managerController.removeRequest(managerTestHelper.getDefaultLocalToken().getAccessId(), id2);
		managerController.removeRequest(managerTestHelper.getDefaultLocalToken().getAccessId(), id3);
		
		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultLocalToken().getAccessId());
		
		Assert.assertEquals(0, requestsFromUser.size());
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
	
	public void testInstanceIsBeingUsedByFulfilledRequest(){
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request1.setProvidingMemberId("remote-manager.test.com");
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		
		Assert.assertTrue(managerController.instanceHasRequestRelatedTo(request1.getId(), DefaultDataTestHelper.INSTANCE_ID
				+ Request.SEPARATOR_GLOBAL_ID + "remote-manager.test.com"));
		Assert.assertFalse(managerController.instanceHasRequestRelatedTo(request1.getId(), "any_value"
				+ Request.SEPARATOR_GLOBAL_ID + "remote-manager.test.com"));
	}
	
	@Test
	public void testInstanceIsBeingUsedByDeletedRequest(){
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setState(RequestState.DELETED);
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request1.setProvidingMemberId("remote-manager.test.com");
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
				
		Assert.assertTrue(managerController.instanceHasRequestRelatedTo(request1.getId(), DefaultDataTestHelper.INSTANCE_ID
				+ Request.SEPARATOR_GLOBAL_ID + "remote-manager.test.com"));
		Assert.assertFalse(managerController.instanceHasRequestRelatedTo(request1.getId(), "any_value"
				+ Request.SEPARATOR_GLOBAL_ID + "remote-manager.test.com"));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testInstanceIsNotBeingUsedButRequestWasForwarded(){
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

		Request request1 = new Request("id1", managerTestHelper.getDefaultFederationToken(), managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true, "");
		request1.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(), request1);
		managerController.setRequests(requestRepository);

		// mocking date
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);

		// submiting requests
		managerController.checkAndSubmitOpenRequests();

		// checking if request was forwarded to remote member
		List<Request> requestsFromUser = managerController.getRequestsFromUser(
				managerTestHelper.getDefaultFederationToken().getAccessId());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
		}
		Assert.assertTrue(managerController.isRequestForwardedtoRemoteMember(
				request1.getId()));
		
		// checking if forwarded request is being considered while checking if
		// instance is being used
		Assert.assertTrue(managerController.instanceHasRequestRelatedTo(request1.getId(),
				DefaultDataTestHelper.INSTANCE_ID + Request.SEPARATOR_GLOBAL_ID
						+ DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL));
		Assert.assertTrue(managerController.instanceHasRequestRelatedTo(request1.getId(),
				"any_value" + Request.SEPARATOR_GLOBAL_ID
						+ DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL));
	}
	
	@Test
	public void testInstanceIsNotBeingUsed(){
		// setting request repository
		Request request1 = new Request("id1",
				managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setState(RequestState.OPEN);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		
		Assert.assertFalse(managerController.instanceHasRequestRelatedTo(request1.getId(), "instanceId"));
	}
	
	@Test
	public void testMonitorServedRequestRemovingRequest() throws InterruptedException{
		// checking there is not served request
		Assert.assertEquals(0, managerController.getServedRequests().size());
		
		mockRequestInstance();
		
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
		managerController.setPacketSender(packetSender);
				
		managerController.queueServedRequest("manager1-test.com", new ArrayList<Category>(),
				xOCCIAtt, "id1", managerTestHelper.getDefaultFederationToken());
		managerController.checkAndSubmitOpenRequests();
		
		// checking there is one served request
		Assert.assertEquals(1, managerController.getServedRequests().size());
		Assert.assertEquals("manager1-test.com",
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
		Assert.assertEquals("id1", getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getId());

		// monitoring served requests
		managerController.monitorServedRequests();
	
		// checking there is not served request		
		Assert.assertEquals(0, managerController.getServedRequests().size());		
	}
	
	@Test
	public void testMonitorServedRequestKeepingRequest() throws InterruptedException{
		// checking there is not served request
		Assert.assertEquals(0, managerController.getServedRequests().size());
		
		mockRequestInstance();
		
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
		managerController.setPacketSender(packetSender);
		
		managerController.queueServedRequest("manager1-test.com", new ArrayList<Category>(),
				xOCCIAtt, "id1", managerTestHelper.getDefaultFederationToken());		
		managerController.checkAndSubmitOpenRequests();
				
		// checking there is one served request
		Assert.assertEquals(1, managerController.getServedRequests().size());
		Assert.assertEquals("manager1-test.com",
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
		Assert.assertEquals("id1", getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getId());

		// monitoring served requests
		managerController.monitorServedRequests();
	
		// checking there is not served request		
		Assert.assertEquals(1, managerController.getServedRequests().size());
		Assert.assertEquals("manager1-test.com",
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
	}
	
	private Request getRequestByInstanceId(List<Request> remoteRequests, String instanceId) {
		for (Request request : remoteRequests) {
			if (instanceId.equals(request.getInstanceId())){
				return request;
			}
		}
		return null;
	}

	@Test
	public void testGarbageCollector(){
		// setting request repository
		Token federationToken = new Token(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.FED_USER_NAME, new Date(), new HashMap<String, String>());
		
		Request request1 = new Request("id1", federationToken, managerTestHelper.getDefaultLocalToken(),  null, null, true, "");
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(federationToken.getUser(), request1);
		managerController.setRequests(requestRepository);

		// mocking getInstances
		Mockito.reset(managerTestHelper.getComputePlugin());
		List<Instance> federationInstances = new ArrayList<Instance>();
		federationInstances.add(new Instance(DefaultDataTestHelper.INSTANCE_ID));
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstances(
						managerTestHelper.getDefaultFederationToken())).thenReturn(
				federationInstances);
		
		// checking if there is one instance for federation token 
		Assert.assertEquals(1, managerController.getInstances(federationToken.getAccessId()).size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID + Request.SEPARATOR_GLOBAL_ID
				+ DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, managerController
				.getInstances(federationToken.getAccessId()).get(0).getId());

		managerController.garbageCollector();
		
		// checking if garbage collector does not remove the instance 
		Assert.assertEquals(1, managerController.getInstances(federationToken.getAccessId()).size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID + Request.SEPARATOR_GLOBAL_ID
				+ DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, managerController
				.getInstances(federationToken.getAccessId()).get(0).getId());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGarbageCollectorRemovingInstance(){
		// setting request repository
		Token federationToken = new Token(DefaultDataTestHelper.FED_ACCESS_TOKEN_ID,
				DefaultDataTestHelper.FED_USER_NAME, new Date(), new HashMap<String, String>());
		
		Request request1 = new Request("id1", federationToken, managerTestHelper.getDefaultLocalToken(), null, null, true, "");
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(federationToken.getUser(), request1);
		managerController.setRequests(requestRepository);

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
	public void testGarbageCollectorWithServedRequest() {
		// checking there is not served request
		Assert.assertEquals(0, managerController.getServedRequests().size());
		
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
		managerController.queueServedRequest("manager1-test.com", new ArrayList<Category>(),
				xOCCIAtt, "id1", managerTestHelper.getDefaultFederationToken());		
		managerController.checkAndSubmitOpenRequests();
				
		// checking there is one served request
		Assert.assertEquals(1, managerController.getServedRequests().size());
		Assert.assertEquals("manager1-test.com",
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
		Assert.assertEquals("id1", getRequestByInstanceId(managerController.getServedRequests(),
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

		managerController.garbageCollector();
		
		// checking if garbage collector does not remove the instance
		resultInstances = managerTestHelper.getComputePlugin().getInstances(managerTestHelper.getDefaultFederationToken());
		Assert.assertEquals(1, resultInstances.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, resultInstances.get(0).getId());
		// checking there is one served request
		Assert.assertEquals(1, managerController.getServedRequests().size());
		Assert.assertEquals("manager1-test.com",
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getRequestingMemberId());
		Assert.assertEquals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				getRequestByInstanceId(managerController.getServedRequests(),
						DefaultDataTestHelper.INSTANCE_ID).getProvidingMemberId());
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void testSSHKeyReplacementWhenOriginalRequestHasPublicKey() 
			throws FileNotFoundException, IOException, MessagingException {
		Map<String, String> extraProperties = new HashMap<String, String>();
		extraProperties.put(ConfigurationConstants.SSH_PUBLIC_KEY_PATH, 
				DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH);
		ManagerController localManagerController = 
				managerTestHelper.createDefaultManagerController(extraProperties);
		ManagerController spiedManageController = Mockito.spy(localManagerController);
		String remoteRequestId = "id1";
		
		Map<String,String> newXOCCIAttr = new HashMap<String,String>(this.xOCCIAtt);
		ArrayList<Category> categories = new ArrayList<Category>();
		final Category publicKeyCategory = new Category(RequestConstants.PUBLIC_KEY_TERM, 
				RequestConstants.CREDENTIALS_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS);
		categories.add(publicKeyCategory);
		newXOCCIAttr.put(RequestAttribute.DATA_PUBLIC_KEY.getValue(), "public-key");
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String newInstanceId = "newinstanceid";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), 
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString())).thenReturn(newInstanceId);
		spiedManageController.setComputePlugin(computePlugin);
		
		Request servedRequest = new Request(remoteRequestId, managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), categories,
				newXOCCIAttr, true,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setInstanceId(newInstanceId);
		servedRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Instance instance = new Instance(newInstanceId);
		instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, "127.0.0.1:5555");
		instance.addAttribute(Instance.SSH_USERNAME_ATT, "fogbow");
		
		Mockito.when(spiedManageController.waitForSSHPublicAddress(Mockito.any(Request.class))).thenReturn(instance);
		Mockito.doNothing().when(spiedManageController).waitForSSHConnectivity(instance);
		
		spiedManageController.createLocalInstanceWithFederationUser(servedRequest);
		
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
								.get(RequestAttribute.DATA_PUBLIC_KEY
										.getValue());
						return publicKeyValue == null;
					}
				}), Mockito.anyString());
		
		String base64UserDataCmd = new String(Base64.decodeBase64(localManagerController
				.createUserDataUtilsCommand(servedRequest)), "UTF-8");
		Assert.assertTrue(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void testSSHKeyReplacementWhenOriginalRequestHasNoPublicKey() 
			throws FileNotFoundException, IOException, MessagingException {
		Map<String, String> extraProperties = new HashMap<String, String>();
		extraProperties.put(ConfigurationConstants.SSH_PUBLIC_KEY_PATH, 
				DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH);
		ManagerController localManagerController = 
				managerTestHelper.createDefaultManagerController(extraProperties);
		String remoteRequestId = "id1";
		ManagerController spiedManageController = Mockito.spy(localManagerController);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String newInstanceId = "newinstanceid";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), 
				Mockito.anyList(), Mockito.anyMap(), Mockito.anyString())).thenReturn(newInstanceId);
		spiedManageController.setComputePlugin(computePlugin);
		
		Request servedRequest = new Request(remoteRequestId, managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				xOCCIAtt, true,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setInstanceId(newInstanceId);
		servedRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Instance instance = new Instance(newInstanceId);
		instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, "127.0.0.1:5555");
		instance.addAttribute(Instance.SSH_USERNAME_ATT, "fogbow");
		
		Mockito.when(spiedManageController.waitForSSHPublicAddress(Mockito.any(Request.class))).thenReturn(instance);
		Mockito.doNothing().when(spiedManageController).waitForSSHConnectivity(instance);
		
		spiedManageController.createLocalInstanceWithFederationUser(servedRequest);
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		final Category publicKeyCategory = new Category(RequestConstants.PUBLIC_KEY_TERM, 
				RequestConstants.CREDENTIALS_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS);
		
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
								.get(RequestAttribute.DATA_PUBLIC_KEY
										.getValue());
						return publicKeyValue == null;
					}
				}), Mockito.anyString());
		
		String base64UserDataCmd = new String(Base64.decodeBase64(localManagerController
				.createUserDataUtilsCommand(servedRequest)), "UTF-8");
		Assert.assertTrue(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSSHKeyReplacementWhenManagerKeyIsNotDefined() 
			throws FileNotFoundException, IOException, MessagingException {
		ManagerController managerControllerSpy = Mockito.spy(managerController);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String instanceId = "instance1";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenReturn(instanceId);
		managerControllerSpy.setComputePlugin(computePlugin);
		
		String servedRequestId = "id1";
		Request servedRequest = new Request(servedRequestId, managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				xOCCIAtt, true,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setInstanceId(instanceId);
		servedRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		managerControllerSpy.createLocalInstanceWithFederationUser(servedRequest);
		
		Mockito.verify(managerControllerSpy, Mockito.never()).waitForSSHPublicAddress(Mockito.eq(servedRequest));
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		String base64UserDataCmd = new String(Base64.decodeBase64(managerController
				.createUserDataUtilsCommand(servedRequest)), "UTF-8");
		Assert.assertFalse(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void testSSHKeyReplacementLocallyWhenOriginalRequestHasPublicKey() throws FileNotFoundException, IOException, MessagingException {
		Map<String, String> extraProperties = new HashMap<String, String>();
		extraProperties.put(ConfigurationConstants.SSH_PUBLIC_KEY_PATH, 
				DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH);
		ManagerController localManagerController = 
				managerTestHelper.createDefaultManagerController(extraProperties);
		ManagerController spiedManageController = Mockito.spy(localManagerController);
		
		String localRequestId = "id1";
		
		Map<String,String> newXOCCIAttr = new HashMap<String,String>(this.xOCCIAtt);
		ArrayList<Category> categories = new ArrayList<Category>();
		final Category publicKeyCategory = new Category(RequestConstants.PUBLIC_KEY_TERM, 
				RequestConstants.CREDENTIALS_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS);
		categories.add(publicKeyCategory);
		newXOCCIAttr.put(RequestAttribute.DATA_PUBLIC_KEY.getValue(), "public-key");
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String newInstanceId = "newinstanceid";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), 
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString())).thenReturn(newInstanceId);
		spiedManageController.setComputePlugin(computePlugin);
		
		Request localRequest = new Request(localRequestId, managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), categories,
				newXOCCIAttr, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		localRequest.setState(RequestState.FULFILLED);
		localRequest.setInstanceId(newInstanceId);
		localRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Instance instance = new Instance(newInstanceId);
		instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, "127.0.0.1:5555");
		instance.addAttribute(Instance.SSH_USERNAME_ATT, "fogbow");
		
		Mockito.when(spiedManageController.waitForSSHPublicAddress(Mockito.any(Request.class))).thenReturn(instance);
		Mockito.doNothing().when(spiedManageController).waitForSSHConnectivity(instance);
		
		spiedManageController.createLocalInstanceWithFederationUser(localRequest);
		
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
								.get(RequestAttribute.DATA_PUBLIC_KEY
										.getValue());
						return publicKeyValue == null;
					}
				}), Mockito.anyString());
		
		String base64UserDataCmd = new String(Base64.decodeBase64(localManagerController
				.createUserDataUtilsCommand(localRequest)), "UTF-8");
		Assert.assertTrue(base64UserDataCmd.contains(localManagerPublicKeyData));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSSHKeyReplacementLocallyWhenOriginalRequestHasNoPublicKey() 
			throws FileNotFoundException, IOException, MessagingException {
		Map<String, String> extraProperties = new HashMap<String, String>();
		extraProperties.put(ConfigurationConstants.SSH_PUBLIC_KEY_PATH, 
				DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH);
		ManagerController localManagerController = 
				managerTestHelper.createDefaultManagerController(extraProperties);
		String localRequestId = "id1";
		ManagerController spiedManageController = Mockito.spy(localManagerController);	
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String newInstanceId = "newinstanceid";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), 
				Mockito.anyList(), Mockito.anyMap(), Mockito.anyString())).thenReturn(newInstanceId);
		spiedManageController.setComputePlugin(computePlugin);
		
		Request localRequest = new Request(localRequestId, managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				xOCCIAtt, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		localRequest.setState(RequestState.FULFILLED);
		localRequest.setInstanceId(newInstanceId);
		localRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Instance instance = new Instance(newInstanceId);
		instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, "127.0.0.1:5555");
		instance.addAttribute(Instance.SSH_USERNAME_ATT, "fogbow");
		
		Mockito.when(spiedManageController.waitForSSHPublicAddress(Mockito.any(Request.class))).thenReturn(instance);
		Mockito.doNothing().when(spiedManageController).waitForSSHConnectivity(instance);
		
		spiedManageController.createLocalInstanceWithFederationUser(localRequest);
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		final Category publicKeyCategory = new Category(RequestConstants.PUBLIC_KEY_TERM, 
				RequestConstants.CREDENTIALS_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS);
		
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
								.get(RequestAttribute.DATA_PUBLIC_KEY
										.getValue());
						return publicKeyValue == null;
					}
				}), Mockito.anyString());
		
		String base64UserDataCmd = new String(Base64.decodeBase64(localManagerController
				.createUserDataUtilsCommand(localRequest)), "UTF-8");
		Assert.assertTrue(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSSHKeyReplacementLocallyWhenManagerKeyIsNotDefined() 
			throws FileNotFoundException, IOException, MessagingException {
		ManagerController managerControllerSpy = Mockito.spy(managerController);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		String instanceId = "instance1";
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.anyList(),
						Mockito.anyMap(), Mockito.anyString())).thenReturn(instanceId);
		managerControllerSpy.setComputePlugin(computePlugin);
		
		String localRequestId = "id1";
		Request localRequest = new Request(localRequestId, managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				xOCCIAtt, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		localRequest.setState(RequestState.FULFILLED);
		localRequest.setInstanceId(instanceId);
		localRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		managerControllerSpy.createLocalInstanceWithFederationUser(localRequest);
		
		Mockito.verify(managerControllerSpy, Mockito.never()).waitForSSHPublicAddress(Mockito.eq(localRequest));
		
		final String localManagerPublicKeyData = IOUtils.toString(new FileInputStream(
				new File(DefaultDataTestHelper.LOCAL_MANAGER_SSH_PUBLIC_KEY_PATH)));
		
		String base64UserDataCmd = new String(Base64.decodeBase64(managerController
				.createUserDataUtilsCommand(localRequest)), "UTF-8");
		Assert.assertFalse(base64UserDataCmd.contains(localManagerPublicKeyData));
	}
	
	public void testPreemption() {
		Request localRequest = new Request("id1", managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		localRequest.setState(RequestState.FULFILLED);
		localRequest.setInstanceId("instance1");
		localRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Request servedRequest1 = new Request("id2", managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), false,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedRequest1.setState(RequestState.FULFILLED);
		servedRequest1.setInstanceId("instance2");
		servedRequest1.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Request servedRequest2 = new Request("id3", managerTestHelper.getDefaultFederationToken(),
				managerTestHelper.getDefaultLocalToken(), new ArrayList<Category>(),
				new HashMap<String, String>(), false,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		servedRequest2.setState(RequestState.FULFILLED);
		servedRequest2.setInstanceId("instance3");
		servedRequest2.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(), localRequest);
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(), servedRequest1);
		requestRepository.addRequest(managerTestHelper.getDefaultFederationToken().getUser(), servedRequest2);

		// check requests
		managerController.setRequests(requestRepository);
		Assert.assertEquals(1, requestRepository.getAllLocalRequests().size());
		Assert.assertTrue(requestRepository.getAllLocalRequests().contains(localRequest));
		Assert.assertEquals(RequestState.FULFILLED, localRequest.getState());
		Assert.assertEquals(2, requestRepository.getAllServedRequests().size());
		Assert.assertTrue(requestRepository.getAllServedRequests().contains(servedRequest1));
		Assert.assertTrue(requestRepository.getAllServedRequests().contains(servedRequest2));
		
		// preempt servedRequest1
		managerController.preemption(servedRequest1);
		Assert.assertEquals(1, requestRepository.getAllLocalRequests().size());
		Assert.assertTrue(requestRepository.getAllLocalRequests().contains(localRequest));
		Assert.assertEquals(RequestState.FULFILLED, localRequest.getState());
		Assert.assertEquals(1, requestRepository.getAllServedRequests().size());
		Assert.assertFalse(requestRepository.getAllServedRequests().contains(servedRequest1));
		Assert.assertTrue(requestRepository.getAllServedRequests().contains(servedRequest2));

		// preempt servedRequest2
		managerController.preemption(servedRequest2);
		Assert.assertEquals(1, requestRepository.getAllLocalRequests().size());
		Assert.assertTrue(requestRepository.getAllLocalRequests().contains(localRequest));
		Assert.assertEquals(RequestState.FULFILLED, localRequest.getState());
		Assert.assertTrue(requestRepository.getAllServedRequests().isEmpty());
		Assert.assertFalse(requestRepository.getAllServedRequests().contains(servedRequest1));
		Assert.assertFalse(requestRepository.getAllServedRequests().contains(servedRequest2));
		
		// preempt localRequest
		managerController.preemption(localRequest);
		Assert.assertEquals(1, requestRepository.getAllLocalRequests().size());
		Assert.assertTrue(requestRepository.getAllLocalRequests().contains(localRequest));
		Assert.assertEquals(RequestState.CLOSED, localRequest.getState());
		Assert.assertTrue(requestRepository.getAllServedRequests().isEmpty());
		Assert.assertFalse(requestRepository.getAllServedRequests().contains(servedRequest1));
		Assert.assertFalse(requestRepository.getAllServedRequests().contains(servedRequest2));
	}
	
	@Test
	public void createRequests() {
		IdentityPlugin federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		IdentityPlugin localIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		String federationUser = "user";
		Token federationToken = new Token("id", federationUser, new Date(),
				new HashMap<String, String>());
		Mockito.when(federationIdentityPlugin.getToken(Mockito.anyString())).thenReturn(
				federationToken, federationToken);
		Mockito.when(localIdentityPlugin.getToken(Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));
		managerController.setLocalIdentityPlugin(localIdentityPlugin);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);
		managerController.createRequests(ACCESS_TOKEN_ID_2, null, new ArrayList<Category>(),
				xOCCIAtt);

		for (Request request : managerController.getRequestsFromUser(federationToken.getAccessId())) {
			if (!request.getLocalToken().getAccessId().isEmpty()) {
				Assert.fail();
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitRequestsWithoutLocalToken() throws InterruptedException {
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class),
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString()))
				.thenReturn("newinstanceid")
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""))
				.thenReturn("newinstanceid");
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourcesInfo);
		managerController.setComputePlugin(computePlugin);

		String batchId = "batchIdOne";
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.BATCH_ID.getValue(), batchId);
		Token federationToken = managerTestHelper.getDefaultFederationToken();
		Token localToken = new Token("", "user", new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", federationToken, localToken, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		request1.setState(RequestState.OPEN);
		Request request2 = new Request("id2", federationToken, localToken, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		request2.setState(RequestState.OPEN);
		Request request3 = new Request("id3", federationToken, localToken, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		request3.setState(RequestState.OPEN);			
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(federationToken.getUser(), request1);
		requestRepository.addRequest(federationToken.getUser(), request2);
		requestRepository.addRequest(federationToken.getUser(), request3);		
		managerController.setRequests(requestRepository);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(federationToken.getAccessId());
		Assert.assertEquals(RequestState.FULFILLED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(1).getState());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(2).getState());
		
		Assert.assertEquals(0, managerController.getFailedBatches()
				.getFailedBatchIdsPerType(FailedBatchType.LOCAL_USER).size());
		Assert.assertEquals(batchId, managerController.getFailedBatches()
				.getFailedBatchIdsPerType(FailedBatchType.FEDERATION_USER).get(0));		
	}	
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitRequestsSameBatchIdFailingCreateLocalUser() throws InterruptedException {
		ResourcesInfo resourcesInfo = new ResourcesInfo("", "", "", "", "", "");
		resourcesInfo.setId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class),
						Mockito.anyList(), Mockito.anyMap(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""))
				.thenReturn("newinstanceid")
				.thenReturn("newinstanceid");
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class)))
				.thenReturn(resourcesInfo);
		managerController.setComputePlugin(computePlugin);

		String batchId = "batchIdOne";
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.BATCH_ID.getValue(), batchId);
		Token token = managerTestHelper.getDefaultFederationToken();
		Request request1 = new Request("id1", token, token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		request1.setState(RequestState.OPEN);
		Request request2 = new Request("id2", token, token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		request2.setState(RequestState.OPEN);	
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(token.getUser(), request1);

		managerController.setRequests(requestRepository);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(token.getAccessId());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.FULFILLED, request.getState());
		}
		
		Assert.assertEquals(batchId, managerController.getFailedBatches()
						.getFailedBatchIdsPerType(FailedBatchType.LOCAL_USER).get(0));
		Assert.assertEquals(0, managerController.getFailedBatches()
				.getFailedBatchIdsPerType(FailedBatchType.FEDERATION_USER).size());		
	}		
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitRequestsSameBatchIdFailingCreateFederationUser() throws InterruptedException {
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
		xOCCIAtt.put(RequestAttribute.BATCH_ID.getValue(), batchId);
		Token token = managerTestHelper.getDefaultFederationToken();
		Request request1 = new Request("id1", token, token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		request1.setState(RequestState.OPEN);
		Request request2 = new Request("id2", token, token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		request2.setState(RequestState.OPEN);
		Request request3 = new Request("id3", token, token, new ArrayList<Category>(),
				xOCCIAtt, true, "");
		request3.setState(RequestState.OPEN);		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(token.getUser(), request1);
		requestRepository.addRequest(token.getUser(), request2);
		requestRepository.addRequest(token.getUser(), request3);
		managerController.setRequests(requestRepository);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(token.getAccessId());
		Assert.assertEquals(RequestState.FULFILLED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(1).getState());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(2).getState());
		Assert.assertEquals(0, managerController.getFailedBatches()
				.getFailedBatchIdsPerType(FailedBatchType.LOCAL_USER).size());
		Assert.assertEquals(batchId, managerController.getFailedBatches()
				.getFailedBatchIdsPerType(FailedBatchType.FEDERATION_USER).get(0));	
	}	
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitRemoteRequestsSameBatchIdFailingCreateFederationUser()
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
		xOCCIAtt.put(RequestAttribute.BATCH_ID.getValue(), batchId);
		Token token = managerTestHelper.getDefaultFederationToken();
		Request request1 = new Request("id1", token, token, new ArrayList<Category>(), xOCCIAtt,
				false, "");
		request1.setState(RequestState.OPEN);
		Request request2 = new Request("id2", token, token, new ArrayList<Category>(), xOCCIAtt,
				false, "");
		request2.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(token.getUser(), request1);
		requestRepository.addRequest(token.getUser(), request2);
		managerController.setRequests(requestRepository);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requestsFromUser = managerController.getRequestsFromUser(token.getAccessId());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.OPEN, request.getState());
		}

		Assert.assertEquals(0, managerController.getFailedBatches()
						.getFailedBatchIdsPerType(FailedBatchType.LOCAL_USER).size());
		Assert.assertEquals(batchId, managerController.getFailedBatches()
						.getFailedBatchIdsPerType(FailedBatchType.FEDERATION_USER).get(0));
	}
	
	@Test
	public void testSeparateRequestsPerBatchId() {
		String batchIdOne = "111";
		String batchIdTwo = "222";
		String batchIdThree = "333";
		RequestRepository requests = new RequestRepository();
		Map<String, String> xOCCIAttOne = new HashMap<String, String>();
		xOCCIAttOne.put(RequestAttribute.BATCH_ID.getValue(), batchIdOne);
		Map<String, String> xOCCIAttTwo = new HashMap<String, String>();
		xOCCIAttTwo.put(RequestAttribute.BATCH_ID.getValue(), batchIdTwo);
		Map<String, String> xOCCIAttThree = new HashMap<String, String>();
		xOCCIAttThree.put(RequestAttribute.BATCH_ID.getValue(), batchIdThree);		
		for (int i = 0; i < 100; i++) {
			requests.addRequest("user",new Request("One" + i, managerTestHelper.getDefaultFederationToken(),
							managerTestHelper.getDefaultFederationToken(),
							new ArrayList<Category>(), xOCCIAttOne, true, ""));
		}
		for (int i = 0; i < 55; i++) {
			requests.addRequest("user",new Request("Two" + i, managerTestHelper.getDefaultFederationToken(),
							managerTestHelper.getDefaultFederationToken(),
							new ArrayList<Category>(), xOCCIAttTwo, true, ""));
		}
		for (int i = 0; i < 1; i++) {
			requests.addRequest("user",new Request("Three" + i, managerTestHelper.getDefaultFederationToken(),
							managerTestHelper.getDefaultFederationToken(),
							new ArrayList<Category>(), xOCCIAttThree, true, ""));
		}		
		managerController.setRequests(requests);
		Map<String, List<Request>> requestsPerBatchId = managerController.getRequestsPerBatchId();
		Assert.assertEquals(3, requestsPerBatchId.size());
	}
	
	@Test
	public void testGetRequestSubList() {
		String batchIdOne = "batchIdOne";
		String batchIdTwo = "batchIdTwo";
		RequestRepository requests = new RequestRepository();
		Map<String, String> xOCCIAttOne = new HashMap<String, String>();
		xOCCIAttOne.put(RequestAttribute.BATCH_ID.getValue(), batchIdOne);
		Map<String, String> xOCCIAttTwo = new HashMap<String, String>();
		xOCCIAttTwo.put(RequestAttribute.BATCH_ID.getValue(), batchIdTwo);		
		for (int i = 0; i < 100; i++) {
			requests.addRequest("user",new Request("One" + i, managerTestHelper.getDefaultFederationToken(),
							managerTestHelper.getDefaultFederationToken(),
							new ArrayList<Category>(), xOCCIAttOne, true, ""));
		}
		for (int i = 0; i < 52; i++) {
			requests.addRequest("user",new Request("Two" + i, managerTestHelper.getDefaultFederationToken(),
							managerTestHelper.getDefaultFederationToken(),
							new ArrayList<Category>(), xOCCIAttTwo, true, ""));
		}		
		managerController.setRequests(requests);
		Map<String, List<Request>> requestsPerBatchId = managerController.getRequestsPerBatchId();
		Assert.assertEquals(2, requestsPerBatchId.size());
		
		List<List<Request>> requestSubList = managerController.getRequestSubList();
		Assert.assertEquals(7, requestSubList.size());
	}
}
