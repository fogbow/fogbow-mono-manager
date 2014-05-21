package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackComputePlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.core.ssh.SSHTunnel;
import org.fogbowcloud.manager.core.util.DefaultDataTest;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
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

	private static final String SECOND_INSTANCE_ID = "rt22e67-5fgt-457a-3rt6-gt78124fhj9p";
	public static final String ACCESS_TOKEN_ID_2 = "2222CVXV23T4TG42VVCV";

	private Token userToken;
	private ManagerController managerController;
	private ManagerTestHelper managerTestHelper;
	private Map<String, String> xOCCIAtt;
	private IdentityPlugin identityPlugin;
	private ComputePlugin computePlugin;

	@Before
	public void setUp() throws Exception {
		managerTestHelper = new ManagerTestHelper();

		// default instance count value is 1
		xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));

		HashMap<String, String> tokenAttr = new HashMap<String, String>();
		userToken = new Token(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.USER_NAME,
				DefaultDataTest.TOKEN_FUTURE_EXPIRATION, tokenAttr);

		Properties properties = new Properties();
		properties.put("federation_user_name", DefaultDataTest.USER_NAME);
		properties.put("federation_user_password", DefaultDataTest.USER_PASS);
		properties.put("federation_user_tenant_name", DefaultDataTest.TENANT_NAME);
		properties.put("scheduler_period", DefaultDataTest.SCHEDULER_PERIOD.toString());
		properties.put("instance_monitoring_period", Long.toString(DefaultDataTest.LONG_TIME));
		managerController = new ManagerController(properties);

		// mocking compute
		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		// mocking identity
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(DefaultDataTest.ACCESS_TOKEN_ID)).thenReturn(userToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);
	}

	@Test
	public void testGetFederationMember() throws InterruptedException {
		OpenStackIdentityPlugin openStackidentityPlugin = Mockito
				.mock(OpenStackIdentityPlugin.class);
		Map<String, String> tokenCredentials = new HashMap<String, String>();
		tokenCredentials.put(OCCIHeaders.X_TOKEN_USER, DefaultDataTest.USER_NAME);
		tokenCredentials.put(OCCIHeaders.X_TOKEN_PASS, DefaultDataTest.USER_PASS);
		tokenCredentials.put(OCCIHeaders.X_TOKEN_TENANT_NAME, DefaultDataTest.TENANT_NAME);

		long tokenExpirationTime = System.currentTimeMillis() + 500;

		Map<String, String> attributesTokenReturn = new HashMap<String, String>();
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_TENANT_ID, "987654321");
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_TENANT_NAME, DefaultDataTest.TENANT_NAME);
		Token token = new Token(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.USER_NAME, new Date(
				tokenExpirationTime), attributesTokenReturn);

		Token token2 = new Token(ACCESS_TOKEN_ID_2, DefaultDataTest.USER_NAME, new Date(
				tokenExpirationTime + DefaultDataTest.LONG_TIME), attributesTokenReturn);

		Mockito.when(openStackidentityPlugin.createToken(tokenCredentials)).thenReturn(token,
				token2);
		Mockito.when(openStackidentityPlugin.isValid(DefaultDataTest.ACCESS_TOKEN_ID)).thenReturn(
				true, false);
		managerController.setIdentityPlugin(openStackidentityPlugin);

		// Get new token
		Token federationUserToken = managerController.getFederationUserToken();
		String accessToken = federationUserToken.getAccessId();
		Assert.assertEquals(DefaultDataTest.ACCESS_TOKEN_ID, accessToken);

		// Use member token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(DefaultDataTest.ACCESS_TOKEN_ID, accessToken);

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				tokenExpirationTime + DefaultDataTest.GRACE_TIME);
		token.setDateUtils(dateUtils);

		// Get new token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(ACCESS_TOKEN_ID_2, accessToken);
	}

	@Test
	public void testcheckAndUpdateRequestToken() throws InterruptedException {
		final long now = System.currentTimeMillis();
		final int tokenUpdaterInterval = 100;
		long tokenExpirationTime = now + (4 * tokenUpdaterInterval);

		Token token = new Token(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.USER_NAME, new Date(
				tokenExpirationTime), new HashMap<String, String>());
		RequestRepository requestRepository = new RequestRepository();
		for (int i = 0; i < 5; i++) {
			requestRepository.addRequest(DefaultDataTest.USER_NAME, new Request("id" + i, token,
					null, null));
		}
		managerController.setRequests(requestRepository);

		// adding behaviour on identity mock
		Token secondToken = new Token(ACCESS_TOKEN_ID_2, DefaultDataTest.USER_NAME, new Date(
				tokenExpirationTime + tokenUpdaterInterval), new HashMap<String, String>());
		Mockito.when(identityPlugin.createToken(token)).thenReturn(secondToken);

		// mocking date
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);

		managerController.checkAndUpdateRequestToken(tokenUpdaterInterval);

		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().in(RequestState.OPEN)) {
				Assert.assertEquals(DefaultDataTest.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			} else if (request.getState().in(RequestState.CLOSED, RequestState.FAILED)) {
				Assert.assertEquals(DefaultDataTest.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			}
		}

		// updating date
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				tokenExpirationTime - tokenUpdaterInterval);

		managerController.checkAndUpdateRequestToken(tokenUpdaterInterval);

		requestsFromUser = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().in(RequestState.OPEN)) {
				Assert.assertEquals(ACCESS_TOKEN_ID_2, request.getToken().getAccessId());
			} else if (request.getState().in(RequestState.CLOSED, RequestState.FAILED)) {
				Assert.assertEquals(DefaultDataTest.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			}
		}
	}

	@Test
	public void testMonitorDeletedRequestWithInstance() throws InterruptedException {
		Request request1 = new Request("id1", userToken, null, null);
		request1.setInstanceId(DefaultDataTest.INSTANCE_ID);
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2", userToken, null, null);
		request2.setInstanceId(DefaultDataTest.INSTANCE_ID);
		request2.setState(RequestState.DELETED);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(userToken.getUser(), request1);
		requestRepository.addRequest(userToken.getUser(), request2);
		managerController.setRequests(requestRepository);

		// updating compute mock
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenReturn(new Instance(DefaultDataTest.INSTANCE_ID));

		List<Request> requestsFromUser = managerController.getRequestsFromUser(userToken
				.getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(1).getState());

		managerController.monitorInstances();

		requestsFromUser = managerController.getRequestsFromUser(userToken.getAccessId());
		Assert.assertEquals(2, requestsFromUser.size());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(0).getState());
		Assert.assertEquals(RequestState.DELETED, requestsFromUser.get(1).getState());
	}

	@Test
	public void testMonitorDeletedRequestWithoutInstance() throws InterruptedException {
		Request request1 = new Request("id1", userToken, null, null);
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2", userToken, null, null);
		request2.setState(RequestState.DELETED);
		Request request3 = new Request("id3", userToken, null, null);
		request3.setState(RequestState.OPEN);

		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(userToken.getUser(), request1);
		requestRepository.addRequest(userToken.getUser(), request2);
		requestRepository.addRequest(userToken.getUser(), request3);
		managerController.setRequests(requestRepository);

		//updating compute mock
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		Assert.assertEquals(3, managerController.getRequestsFromUser(userToken.getAccessId()).size());
		Assert.assertEquals(RequestState.DELETED,
				managerController.getRequest(userToken.getAccessId(), "id1").getState());
		Assert.assertEquals(RequestState.DELETED,
				managerController.getRequest(userToken.getAccessId(), "id2").getState());
		Assert.assertEquals(RequestState.OPEN,
				managerController.getRequest(userToken.getAccessId(), "id3").getState());

		managerController.monitorInstances();

		Assert.assertEquals(1, managerController.getRequestsFromUser(userToken.getAccessId()).size());
		Assert.assertEquals(RequestState.OPEN,
				managerController.getRequestsFromUser(userToken.getAccessId()).get(0).getState());
	}

	@Test
	public void testMonitorFulfilledRequestWithoutInstance() throws InterruptedException {
		Request request1 = new Request("id1", userToken, null, null);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", userToken, null, null);
		request2.setState(RequestState.FULFILLED);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(userToken.getUser(), request1);
		requestRepository.addRequest(userToken.getUser(), request2);
		managerController.setRequests(requestRepository);

		//updating compute mock
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));
			
		List<Request> requestsFromUser = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}
		
		managerController.monitorInstances();
		
		requestsFromUser = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.CLOSED));
		}
	}

	@Test
	public void testMonitorFulfilledAndPersistentRequest() throws InterruptedException {
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		Request request1 = new Request("id1", userToken, null, attributes);
		request1.setState(RequestState.FULFILLED);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(userToken.getUser(), request1);
		managerController.setRequests(requestRepository);

		mockRequestInstance();
	
		//updating compute mock
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		List<Request> requestsFromUser = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.FULFILLED, requestsFromUser.get(0).getState());

		managerController.monitorInstances();

		requestsFromUser = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.OPEN, requestsFromUser.get(0).getState());

		managerController.checkAndSubmitOpenRequests();

		requestsFromUser = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
		Assert.assertEquals(1, requestsFromUser.size());
		Assert.assertEquals(RequestState.FULFILLED, requestsFromUser.get(0).getState());
	}

	@Test
	public void testMonitorFulfilledRequestWithInstance() throws InterruptedException {
		Request request1 = new Request("id1", userToken, null, null);
		request1.setInstanceId(DefaultDataTest.INSTANCE_ID);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", userToken, null, null);
		request2.setInstanceId(SECOND_INSTANCE_ID);
		request2.setState(RequestState.FULFILLED);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(userToken.getUser(), request1);
		requestRepository.addRequest(userToken.getUser(), request2);
		managerController.setRequests(requestRepository);

		//updating compute mock
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenReturn(new Instance(DefaultDataTest.INSTANCE_ID));
		
		managerController.monitorInstances();		
		List<Request> requestsFromUser = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
		Assert.assertEquals(2, requestsFromUser.size());
		for (Request request : requestsFromUser) {
			Assert.assertEquals(RequestState.FULFILLED, request.getState());
		}

		managerController.monitorInstances();
		requestsFromUser = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);
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

		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTest.USER_NAME, requests.get(0).getToken().getUser());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testOneTimeRequestSetFulfilledAndClosed() throws InterruptedException {
		mockRequestInstance();

		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController.removeInstance(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.INSTANCE_ID);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetFulfilledAndOpen() throws InterruptedException {
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		mockRequestInstance();

		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController.removeInstance(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.INSTANCE_ID);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

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
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		//mocking compute for 2 instances
		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(DefaultDataTest.INSTANCE_ID,
				SECOND_INSTANCE_ID);
		managerController.setComputePlugin(computePlugin);
		
		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController.removeInstance(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.INSTANCE_ID);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// getting second instance
		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD * 2);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(SECOND_INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetOpenAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis() + DefaultDataTest.SCHEDULER_PERIOD;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				String.valueOf(DateUtils.getDateISO8601Format(expirationRequestTime)));

		// creating request
		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetFulfilledAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis() + DefaultDataTest.SCHEDULER_PERIOD
				+ DefaultDataTest.GRACE_TIME;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD);

		List<Request> requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD);

		// removing instance
		managerController.removeInstance(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.INSTANCE_ID);

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	private void mockRequestInstance() {
		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(DefaultDataTest.INSTANCE_ID);
		managerController.setComputePlugin(computePlugin);
	}

	@Test
	public void testOneTimeRequestSetOpenAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis() + DefaultDataTest.SCHEDULER_PERIOD;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.ONE_TIME.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		// creating request
		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

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
		long startRequestTime = now + (DefaultDataTest.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + DefaultDataTest.LONG_TIME;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.ONE_TIME.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		// request is not in valid period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		// request is in valid period
		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestWithValidFromAttInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (DefaultDataTest.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + DefaultDataTest.LONG_TIME;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		// request is not in valid period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		// request is in valid period
		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testOneTimeRequestValidityPeriodInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (DefaultDataTest.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + (DefaultDataTest.SCHEDULER_PERIOD * 3);

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.ONE_TIME.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		// request is not in valid period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		// request is in valid period
		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// remove instance
		managerController.removeInstance(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.INSTANCE_ID);

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		// request is not in valid period anymore
		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

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
		long startRequestTime = now + (DefaultDataTest.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + (DefaultDataTest.SCHEDULER_PERIOD * 3);

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(),
				DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockRequestInstance();

		// creating request
		managerController.createRequests(DefaultDataTest.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		// request is not in valid period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		// request is in valid period
		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(DefaultDataTest.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		// remove instance
		managerController.removeInstance(DefaultDataTest.ACCESS_TOKEN_ID, DefaultDataTest.INSTANCE_ID);

		Thread.sleep(DefaultDataTest.SCHEDULER_PERIOD + DefaultDataTest.GRACE_TIME);

		// request is not in valid period anymore
		requests = managerController.getRequestsFromUser(DefaultDataTest.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

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
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(token);
		managerController.setIdentityPlugin(identityPlugin);

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
