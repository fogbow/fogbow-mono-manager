package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackComputePlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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
				.getTokenFromFederationIdP(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		
		Assert.assertEquals(managerTestHelper.getDefaultToken().getAccessId(),
				tokenFromFederationIdP.getAccessId());
	}
	
	@Test(expected=OCCIException.class)
	public void testUnauthorizedUser() {
		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(false);
		managerController.setAuthorizationPlugin(authorizationPlugin);
		
		managerController.getTokenFromFederationIdP(DefaultDataTestHelper.ACCESS_TOKEN_ID);		
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
				computePlugin.requestInstance(Mockito.eq(localUserAccessId), Mockito.anyList(),
						Mockito.anyMap())).thenReturn("newinstanceid");
		managerController.setComputePlugin(computePlugin);

		checkRequestPerUserToken(localToken);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSubmitFederationUserRequests() throws InterruptedException {
		final String federationUserAccessId = "Federation-User-Access-Id";
		final String federationUser = "federationUser";
		Token federationToken = new Token(federationUserAccessId, federationUser, new Date(),
				new HashMap<String, String>());

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.eq(federationUserAccessId),
						Mockito.anyList(), Mockito.anyMap()))
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""))
				.thenReturn("newinstanceid")
				.thenThrow(new OCCIException(ErrorType.UNAUTHORIZED, ""))
				.thenReturn("newinstanceid");
		managerController.setComputePlugin(computePlugin);

		List<FederationMember> listMembers = new ArrayList<FederationMember>();
		ResourcesInfo resourseInfo = new ResourcesInfo("", "", "", "", null, null);
		resourseInfo.setId(DefaultDataTestHelper.MANAGER_COMPONENT_URL);
		FederationMember federationMember = new FederationMember(resourseInfo);
		listMembers.add(federationMember);
		managerController.setMembers(listMembers);

		checkRequestPerUserToken(federationToken);
	}

	private void checkRequestPerUserToken(Token token) {
		IdentityPlugin identityPlugin = managerTestHelper.getIdentityPlugin();
		IdentityPlugin federationIdentityPlugin = managerTestHelper.getFederationIdentityPlugin();
		Mockito.when(federationIdentityPlugin.getToken(token.getAccessId())).thenReturn(token);
		Mockito.when(identityPlugin.createFederationUserToken()).thenReturn(token);
		managerController.setLocalIdentityPlugin(identityPlugin);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);

		Request request1 = new Request("id1", token, new ArrayList<Category>(),
				new HashMap<String, String>());
		request1.setState(RequestState.OPEN);
		Request request2 = new Request("id2", token, new ArrayList<Category>(),
				new HashMap<String, String>());
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
	
	@Test
	public void testGetFederationMember() throws InterruptedException {
		Map<String, String> tokenCredentials = new HashMap<String, String>();
		tokenCredentials.put(OpenStackIdentityPlugin.USERNAME, DefaultDataTestHelper.USER_NAME);
		tokenCredentials.put(OpenStackIdentityPlugin.PASSWORD, DefaultDataTestHelper.USER_PASS);
		tokenCredentials.put(OpenStackIdentityPlugin.TENANT_NAME,
				DefaultDataTestHelper.TENANT_NAME);

		long tokenExpirationTime = System.currentTimeMillis() + 500;

		Map<String, String> attributesTokenReturn = new HashMap<String, String>();
		attributesTokenReturn.put(OpenStackIdentityPlugin.TENANT_ID, "987654321");
		attributesTokenReturn.put(OpenStackIdentityPlugin.TENANT_NAME,
				DefaultDataTestHelper.TENANT_NAME);

		Token firstToken = new Token(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				DefaultDataTestHelper.USER_NAME, new Date(tokenExpirationTime),
				attributesTokenReturn);
		Token secondToken = new Token(ACCESS_TOKEN_ID_2, DefaultDataTestHelper.USER_NAME, new Date(
				tokenExpirationTime + DefaultDataTestHelper.LONG_TIME), attributesTokenReturn);

		// mocking identity plugin
		OpenStackIdentityPlugin openStackidentityPlugin = Mockito
				.mock(OpenStackIdentityPlugin.class);
		Mockito.when(openStackidentityPlugin.createFederationUserToken()).thenReturn(firstToken,
				secondToken);
		Mockito.when(openStackidentityPlugin.createToken(tokenCredentials)).thenReturn(firstToken,
				secondToken);
		Mockito.when(openStackidentityPlugin.isValid(DefaultDataTestHelper.ACCESS_TOKEN_ID))
				.thenReturn(true, false);
		managerController.setLocalIdentityPlugin(openStackidentityPlugin);

		// Get new token
		Token federationUserToken = managerController.getFederationUserToken();
		String accessToken = federationUserToken.getAccessId();
		Assert.assertEquals(DefaultDataTestHelper.ACCESS_TOKEN_ID, accessToken);

		// Use member token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(DefaultDataTestHelper.ACCESS_TOKEN_ID, accessToken);

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

		Token firstToken = new Token(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				DefaultDataTestHelper.USER_NAME, new Date(tokenExpirationTime),
				new HashMap<String, String>());

		// setting request repository
		RequestRepository requestRepository = new RequestRepository();
		for (int i = 0; i < 5; i++) {
			requestRepository.addRequest(DefaultDataTestHelper.USER_NAME, new Request("id" + i,
					firstToken, null, null));
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
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().in(RequestState.OPEN)) {
				Assert.assertEquals(DefaultDataTestHelper.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			} else if (request.getState().in(RequestState.CLOSED, RequestState.FAILED)) {
				Assert.assertEquals(DefaultDataTestHelper.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			}
		}

		// updating date
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				tokenExpirationTime - tokenUpdaterInterval);

		managerController.checkAndUpdateRequestToken(tokenUpdaterInterval);

		// check if open requests have been updated to secondToken
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().in(RequestState.OPEN)) {
				Assert.assertEquals(ACCESS_TOKEN_ID_2, request.getToken().getAccessId());
			} else if (request.getState().in(RequestState.CLOSED, RequestState.FAILED)) {
				Assert.assertEquals(DefaultDataTestHelper.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			}
		}
	}
	
	@Test
	public void testDeleteClosedRequest() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultToken(), null, null);
		request1.setState(RequestState.CLOSED);
		Request request2 = new Request("id2", managerTestHelper.getDefaultToken(), null, null);
		request2.setState(RequestState.CLOSED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// checking closed requests
		List<Request> requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultToken().getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.CLOSED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.CLOSED, requestsFromUser.get(1).getState());

		managerController.removeRequest(managerTestHelper.getDefaultToken().getAccessId(), "id1");

		// making sure one request was removed
		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultToken().getAccessId());
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.CLOSED, requestsFromUser.get(0).getState());
		Assert.assertEquals("id2", requestsFromUser.get(0).getId());
		
		managerController.removeRequest(managerTestHelper.getDefaultToken().getAccessId(), "id2");

		// making sure the last request was removed
		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultToken().getAccessId());
		Assert.assertEquals(0, requestsFromUser.size());
	}

	@Test
	public void testMonitorDeletedRequestWithInstance() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultToken(), null, null);
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2", managerTestHelper.getDefaultToken(), null, null);
		request2.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request2.setState(RequestState.DELETED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.anyString(),
						Mockito.anyString())).thenReturn(
				new Instance(DefaultDataTestHelper.INSTANCE_ID));

		// checking deleted requests
		List<Request> requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultToken().getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(1).getState());

		managerController.monitorInstances();

		// making sure the requests were not removed
		requestsFromUser = managerController.getRequestsFromUser(managerTestHelper
				.getDefaultToken().getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(1).getState());
	}

	@Test
	public void testMonitorDeletedRequestWithoutInstance() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultToken(), null, null);
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2", managerTestHelper.getDefaultToken(), null, null);
		request2.setState(RequestState.DELETED);
		Request request3 = new Request("id3", managerTestHelper.getDefaultToken(), null, null);
		request3.setState(RequestState.OPEN);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request2);
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request3);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.anyString(),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if requests still have the initial state
		Assert.assertEquals(
				3,
				managerController.getRequestsFromUser(
						managerTestHelper.getDefaultToken().getAccessId()).size());
		Assert.assertEquals(
				RequestState.DELETED,
				managerController.getRequest(managerTestHelper.getDefaultToken().getAccessId(),
						"id1").getState());
		Assert.assertEquals(
				RequestState.DELETED,
				managerController.getRequest(managerTestHelper.getDefaultToken().getAccessId(),
						"id2").getState());
		Assert.assertEquals(
				RequestState.OPEN,
				managerController.getRequest(managerTestHelper.getDefaultToken().getAccessId(),
						"id3").getState());

		managerController.monitorInstances();

		// checking if deleted requests were removed
		Assert.assertEquals(
				1,
				managerController.getRequestsFromUser(
						managerTestHelper.getDefaultToken().getAccessId()).size());
		Assert.assertEquals(
				RequestState.OPEN,
				managerController
						.getRequestsFromUser(managerTestHelper.getDefaultToken().getAccessId())
						.get(0).getState());
	}

	@Test
	public void testMonitorFulfilledRequestWithoutInstance() throws InterruptedException {
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultToken(), null, null);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", managerTestHelper.getDefaultToken(), null, null);
		request2.setState(RequestState.FULFILLED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.anyString(),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if requests were fulfilled
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}

		managerController.monitorInstances();

		// checking if requests were closed
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
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
		Request request1 = new Request("id1", managerTestHelper.getDefaultToken(), new ArrayList<Category>(), attributes);
		request1.setState(RequestState.FULFILLED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request1);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.anyString(),
						Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		// checking if request is fulfilled
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.FULFILLED, requestsFromUser.get(0).getState());

		managerController.monitorInstances();

		// checking if request has lost its instance
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(0).getState());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class))).thenReturn(
				DefaultDataTestHelper.INSTANCE_ID);
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.anyString(),
						Mockito.eq(DefaultDataTestHelper.INSTANCE_ID))).thenReturn(
				new Instance(DefaultDataTestHelper.INSTANCE_ID));

		// getting instance for request
		managerController.checkAndSubmitOpenRequests();

		// checking if request has been fulfilled again
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.FULFILLED, requestsFromUser.get(0).getState());
	}

	@Test
	public void testMonitorFulfilledRequestWithInstance() throws InterruptedException {
		final String SECOND_INSTANCE_ID = "secondInstanceId";

		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultToken(), null, null);
		request1.setInstanceId(DefaultDataTestHelper.INSTANCE_ID);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", managerTestHelper.getDefaultToken(), null, null);
		request2.setInstanceId(SECOND_INSTANCE_ID);
		request2.setState(RequestState.FULFILLED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request1);
		requestRepository.addRequest(managerTestHelper.getDefaultToken().getUser(), request2);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().getInstance(Mockito.anyString(),
						Mockito.anyString())).thenReturn(
				new Instance(DefaultDataTestHelper.INSTANCE_ID));

		managerController.monitorInstances();

		// checking if requests are still fulfilled
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.FULFILLED, request.getState());
		}

		managerController.monitorInstances();

		// checking if requests' state haven't been changed
		requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorException() throws Exception {
		new ManagerController(null);
	}

	@Test
	public void testGet0ItemsFromIQ() {
		managerController.updateMembers(new LinkedList<FederationMember>());
		Assert.assertEquals(0, managerController.getMembers().size());
	}

	@Test
	public void testGet1ItemFromIQ() throws CertificateException, IOException {
		FederationMember managerItem = new FederationMember(managerTestHelper.getResources());
		List<FederationMember> items = new LinkedList<FederationMember>();
		items.add(managerItem);
		managerController.updateMembers(items);

		List<FederationMember> members = managerController.getMembers();
		Assert.assertEquals(1, members.size());
		Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		Assert.assertEquals(1, managerController.getMembers().size());
	}

	@Test
	public void testGetManyItemsFromIQ() throws CertificateException, IOException {
		ArrayList<FederationMember> items = new ArrayList<FederationMember>();
		for (int i = 0; i < 10; i++) {
			items.add(new FederationMember(managerTestHelper.getResources()));
		}
		managerController.updateMembers(items);

		List<FederationMember> members = managerController.getMembers();
		Assert.assertEquals(10, members.size());
		for (int i = 0; i < 10; i++) {
			Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		}
		Assert.assertEquals(10, managerController.getMembers().size());
	}

	@Test
	public void testGetRequestsByUser() throws InterruptedException {
		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenRequests();

		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);

		// checking if request was created
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.USER_NAME, requests.get(0).getToken().getUser());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testOneTimeRequestSetFulfilledAndClosed() throws InterruptedException {
		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenRequests();

		// checking if request was properly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// updating compute mock
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				DefaultDataTestHelper.ACCESS_TOKEN_ID, DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID);

		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetFulfilledAndOpen() throws InterruptedException {
		mockRequestInstance();
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		// creating request
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenRequests();

		// checking if request was properly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				DefaultDataTestHelper.ACCESS_TOKEN_ID, DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID);

		// checking request state was set to open
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
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
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);
		managerController.checkAndSubmitOpenRequests();

		// checking if request was fulfilled with instanceID
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				DefaultDataTestHelper.ACCESS_TOKEN_ID, DefaultDataTestHelper.INSTANCE_ID);
		
		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID);

		// checking if request state was set to open
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class))).thenReturn(
				SECOND_INSTANCE_ID);

		// getting second instance
		managerController.checkAndSubmitOpenRequests();

		// checking if request was fulfilled with secondInstance
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(SECOND_INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
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
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if request is OPEN
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// waiting expiration time
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);

		// checking if request was closed
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

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
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD);
		managerController.checkAndSubmitOpenRequests();

		// checking request is fulfilled
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// waiting expiration time
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD);

		// updating compute mock
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				DefaultDataTestHelper.ACCESS_TOKEN_ID, DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID);
		managerController.checkAndSubmitOpenRequests();

		// checking if request state was set to closed
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	private void mockRequestInstance() {
		Mockito.reset(managerTestHelper.getComputePlugin());
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class))).thenReturn(
				DefaultDataTestHelper.INSTANCE_ID);
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
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if request was properly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// waiting expiration time
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenRequests();
		
		// checking if request state was set to closed
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
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
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if request was properly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// sleeping for a time and request not valid yet
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// check that request is not in valid period yet
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// sleeping for the scheduler period and submitting request
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// check if request is in valid period
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
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
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// checking if request was rightly created
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// sleeping for a time and request not valid yet
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// check request is not in valid period yet
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// sleeping for the scheduler period and submitting request
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// check if request is in valid period
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

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
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// request is not in valid period yet
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// sleeping for the scheduler period and submitting request
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 3 + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenRequests();
		
		// checking is request is fulfilled
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				DefaultDataTestHelper.ACCESS_TOKEN_ID, DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID);

		// waiting for a time and request is not into valid period anymore
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD + DefaultDataTestHelper.GRACE_TIME);

		// checking if request is not in valid period anymore
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

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
		managerController.createRequests(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		// request is not in valid period yet
		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// waiting for a time and request is into valid period
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 2 + DefaultDataTestHelper.GRACE_TIME);
		managerController.checkAndSubmitOpenRequests();

		// checking is request is fulfilled
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTestHelper.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// updating compute mock
		Mockito.when(
				managerTestHelper.getComputePlugin().requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.doNothing().when(managerTestHelper.getComputePlugin()).removeInstance(
				DefaultDataTestHelper.ACCESS_TOKEN_ID, DefaultDataTestHelper.INSTANCE_ID);

		// removing instance
		managerController.removeInstance(DefaultDataTestHelper.ACCESS_TOKEN_ID,
				DefaultDataTestHelper.INSTANCE_ID);

		// waiting for the scheduler period so that request is not into valid period anymore
		Thread.sleep(DefaultDataTestHelper.SCHEDULER_PERIOD * 2 + DefaultDataTestHelper.GRACE_TIME);

		managerController.checkAndSubmitOpenRequests();
		
		// checking if request is not in valid period anymore
		requests = managerController.getRequestsFromUser(DefaultDataTestHelper.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
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
		managerController.setMembers(list);

		RestrictCAsMemberValidator validatorMock = Mockito.mock(RestrictCAsMemberValidator.class);
		Mockito.doReturn(true).when(validatorMock).canDonateTo(member);
		managerController.setValidator(validatorMock);

		Token token = Mockito.mock(Token.class);
		Mockito.doReturn(null).when(token).getAccessId();

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.createFederationUserToken()).thenReturn(token);
		managerController.setLocalIdentityPlugin(identityPlugin);

		ComputePlugin plugin = Mockito.mock(OpenStackComputePlugin.class);
		Mockito.doReturn("answer").when(plugin).requestInstance(null, null, null);

		managerController.setComputePlugin(plugin);
		Assert.assertEquals("answer",
				managerController.createInstanceForRemoteMember("abc", null, null));

		Mockito.doReturn(false).when(validatorMock).canDonateTo(member);
		managerController.setValidator(validatorMock);
		Assert.assertEquals(null,
				managerController.createInstanceForRemoteMember("abc", null, null));
	}
}
