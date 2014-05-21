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

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackComputePlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.core.ssh.SSHTunnel;
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
import org.fogbowcloud.manager.xmpp.core.model.DateUtils;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.fogbowcloud.manager.xmpp.util.TestHelperData;
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
	
	@Before
	public void setUp() throws Exception {		
		managerTestHelper = new ManagerTestHelper();
		
		// default instance count value is 1
		xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		
		HashMap<String, String> tokenAttr = new HashMap<String, String>();
		userToken = new Token(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.USER_NAME,
				TestHelperData.TOKEN_FUTURE_EXPIRATION, tokenAttr);
		
		Properties properties = new Properties();
		properties.put("federation_user_name", TestHelperData.USER_NAME);
		properties.put("federation_user_password", TestHelperData.USER_PASS);
		properties.put("federation_user_tenant_name", TestHelperData.TENANT_NAME);
		properties.put("scheduler_period", TestHelperData.SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(userToken);

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
		tokenCredentials.put(OCCIHeaders.X_TOKEN_USER, TestHelperData.USER_NAME);
		tokenCredentials.put(OCCIHeaders.X_TOKEN_PASS, TestHelperData.USER_PASS);
		tokenCredentials.put(OCCIHeaders.X_TOKEN_TENANT_NAME, TestHelperData.TENANT_NAME);

		long tokenExpirationTime = System.currentTimeMillis() + 500;

		Map<String, String> attributesTokenReturn = new HashMap<String, String>();
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_TENANT_ID, "987654321");
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_TENANT_NAME, TestHelperData.TENANT_NAME);
		Token token = new Token(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.USER_NAME, new Date(
				tokenExpirationTime), attributesTokenReturn);

		Token token2 = new Token(ACCESS_TOKEN_ID_2, TestHelperData.USER_NAME, new Date(
				tokenExpirationTime + TestHelperData.LONG_TIME), attributesTokenReturn);

		Mockito.when(openStackidentityPlugin.createToken(tokenCredentials)).thenReturn(token,
				token2);
		Mockito.when(openStackidentityPlugin.isValid(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(
				true, false);
		managerController.setIdentityPlugin(openStackidentityPlugin);

		// Get new token
		Token federationUserToken = managerController.getFederationUserToken();
		String accessToken = federationUserToken.getAccessId();
		Assert.assertEquals(TestHelperData.ACCESS_TOKEN_ID, accessToken);

		// Use member token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(TestHelperData.ACCESS_TOKEN_ID, accessToken);

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				tokenExpirationTime + TestHelperData.GRACE_TIME);
		token.setDateUtils(dateUtils);

		// Get new token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(ACCESS_TOKEN_ID_2, accessToken);
	}

	@Test
	public void testcheckAndUpdateRequestToken() throws InterruptedException {
		final int tokenUpdaterInterval = 100;
		final long now = System.currentTimeMillis();
		long tokenExpirationTime = now + (4 * tokenUpdaterInterval);

		Properties properties = new Properties();
		properties.put("token_update_period", Integer.toString(tokenUpdaterInterval));
		managerController = new ManagerController(properties);
		DateUtils dateUtils = Mockito.mock(DateUtils.class);

		RequestRepository requestRepository = new RequestRepository();
		Token token = new Token(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.USER_NAME, new Date(
				tokenExpirationTime), new HashMap<String, String>());

		for (int i = 0; i < 5; i++) {
			requestRepository.addRequest(TestHelperData.USER_NAME, new Request("id" + i, token,
					TestHelperData.USER_NAME, null, null));
		}
		managerController.setRequests(requestRepository);

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(token);
		Token tokenUpdatedFirstTime = new Token(ACCESS_TOKEN_ID_2, TestHelperData.USER_NAME,
				new Date(tokenExpirationTime + tokenUpdaterInterval), new HashMap<String, String>());
		Mockito.when(identityPlugin.createToken(token)).thenReturn(tokenUpdatedFirstTime);
		managerController.setIdentityPlugin(identityPlugin);

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);

		managerController.checkAndUpdateRequestToken(tokenUpdaterInterval);
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().in(RequestState.OPEN)) {
				Assert.assertEquals(TestHelperData.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			} else if (request.getState().in(RequestState.CLOSED, RequestState.FAILED)) {
				Assert.assertEquals(TestHelperData.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			}
		}

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(
				tokenExpirationTime - tokenUpdaterInterval);
		managerController.setDateUtils(dateUtils);

		managerController.checkAndUpdateRequestToken(tokenUpdaterInterval);
		requestsFromUser = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().in(RequestState.OPEN)) {
				Assert.assertEquals(ACCESS_TOKEN_ID_2, request.getToken().getAccessId());
			} else if (request.getState().in(RequestState.CLOSED, RequestState.FAILED)) {
				Assert.assertEquals(TestHelperData.ACCESS_TOKEN_ID, request.getToken()
						.getAccessId());
			}
		}
	}

	@Test
	public void testMonitoringDeletedRequestAndFoundInstance() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Long.toString(TestHelperData.LONG_TIME));
		managerController = new ManagerController(properties);

		Token token = new Token(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.USER_NAME,
				new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", token, TestHelperData.USER_NAME, null, null);
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2", token, TestHelperData.USER_NAME, null, null);
		request2.setState(RequestState.DELETED);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(TestHelperData.USER_NAME, request1);
		requestRepository.addRequest(TestHelperData.USER_NAME, request2);
		managerController.setRequests(requestRepository);
		RequestRepository requestRepositoryCopy = new RequestRepository();
		requestRepositoryCopy.addRequest(TestHelperData.USER_NAME, request1);
		requestRepositoryCopy.addRequest(TestHelperData.USER_NAME, request2);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(token);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenReturn(new Instance(TestHelperData.INSTANCE_ID));

		Assert.assertEquals(2, getRequestsDeleted(requestRepositoryCopy).size());

		managerController.monitorInstances();
		Assert.assertEquals(2, getRequestsDeleted(requestRepositoryCopy).size());
	}

	private List<Request> getRequestsDeleted(RequestRepository requestRepository) {
		List<Request> requests = new ArrayList<Request>();
		for (Request request : requestRepository.get(RequestState.DELETED)) {
			try {
				requests.add(managerController.getRequest(TestHelperData.ACCESS_TOKEN_ID,
						request.getId()));
			} catch (Exception e) {
			}
		}
		return requests;
	}

	@Test
	public void testMonitoringDeletedRequestAndNotFoundInstance() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Long.toString(TestHelperData.LONG_TIME));
		managerController = new ManagerController(properties);

		Token token = new Token(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.USER_NAME,
				new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", token, TestHelperData.USER_NAME, null, null);
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2", token, TestHelperData.USER_NAME, null, null);
		request2.setState(RequestState.DELETED);
		Request request3 = new Request("id3", token, TestHelperData.USER_NAME, null, null);
		request3.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(TestHelperData.USER_NAME, request1);
		requestRepository.addRequest(TestHelperData.USER_NAME, request2);
		requestRepository.addRequest(TestHelperData.USER_NAME, request3);
		managerController.setRequests(requestRepository);
		RequestRepository requestRepositoryCopy = new RequestRepository();
		requestRepositoryCopy.addRequest(TestHelperData.USER_NAME, request1);
		requestRepositoryCopy.addRequest(TestHelperData.USER_NAME, request2);
		requestRepositoryCopy.addRequest(TestHelperData.USER_NAME, request3);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(token);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		Assert.assertEquals(2, getRequestsDeleted(requestRepositoryCopy).size());

		managerController.monitorInstances();
		Assert.assertEquals(0, getRequestsDeleted(requestRepositoryCopy).size());
	}

	@Test
	public void testMonitoringFulfilledRequestAndNotFoundInstance() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Long.toString(TestHelperData.LONG_TIME));
		managerController = new ManagerController(properties);

		Token token = new Token(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.USER_NAME,
				new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", token, TestHelperData.USER_NAME, null, null);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", token, TestHelperData.USER_NAME, null, null);
		request2.setState(RequestState.FULFILLED);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(TestHelperData.USER_NAME, request1);
		requestRepository.addRequest(TestHelperData.USER_NAME, request2);
		managerController.setRequests(requestRepository);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(token);

		managerController.monitorInstances();
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}

		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		managerController.monitorInstances();
		requestsFromUser = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.CLOSED));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMonitoringFulfilledRequestAndPesistentInstance() throws InterruptedException {
		final int timeDefault = 5 * 60 * 1000; // Big time to not interfere this
												// test
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Integer.toString(timeDefault));
		properties.put("scheduler_period", 100 * TestHelperData.SCHEDULER_PERIOD);

		managerController = new ManagerController(properties);
		Token token = new Token(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.USER_NAME,
				TestHelperData.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		Map<String, String> map = new HashMap<String, String>();
		map.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		Request request1 = new Request("id1", token, TestHelperData.USER_NAME, null, map);
		request1.setState(RequestState.FULFILLED);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(TestHelperData.USER_NAME, request1);
		managerController.setRequests(requestRepository);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(token);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.anyList(),
						Mockito.anyMap())).thenReturn(TestHelperData.INSTANCE_ID);

		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND));

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);

		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}

		managerController.monitorInstances();

		for (Request request : requestsFromUser) {
			requestsFromUser = managerController
					.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
			Assert.assertTrue(request.getState().in(RequestState.OPEN));
		}

		managerController.checkAndSubmitOpenRequests();

		requestsFromUser = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}
	}

	@Test
	public void testMonitoringFulfilledRequestAndOnetimeInstance() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Long.toString(TestHelperData.LONG_TIME));
		managerController = new ManagerController(properties);

		Token token = new Token(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.USER_NAME,
				new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", token, TestHelperData.USER_NAME, null, null);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", token, TestHelperData.USER_NAME, null, null);
		request2.setState(RequestState.FULFILLED);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(TestHelperData.USER_NAME, request1);
		requestRepository.addRequest(TestHelperData.USER_NAME, request2);
		managerController.setRequests(requestRepository);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(token);

		managerController.monitorInstances();
		List<Request> requestsFromUser = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}

		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenReturn(new Instance(TestHelperData.INSTANCE_ID));

		managerController.monitorInstances();
		requestsFromUser = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);
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

	@SuppressWarnings("unchecked")
	@Test
	public void testGetRequestsByUser() throws InterruptedException {
		mockOKRequestCompute();

		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		managerController.checkAndSubmitOpenRequests();

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(TestHelperData.USER_NAME, requests.get(0).getUser());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testOneTimeRequestSetFulfilledAndClosed() throws InterruptedException {
		mockOKRequestCompute();
		
		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

//		Thread.sleep(TestHelperData.SCHEDULER_PERIOD);
		managerController.checkAndSubmitOpenRequests();

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController
				.removeInstance(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.INSTANCE_ID);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestSetFulfilledAndOpen() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", TestHelperData.SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class)))
				.thenReturn(TestHelperData.INSTANCE_ID)
				.thenThrow(
						new OCCIException(ErrorType.QUOTA_EXCEEDED,
								ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(userToken);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

//		Thread.sleep(TestHelperData.SCHEDULER_PERIOD);
		managerController.checkAndSubmitOpenRequests();

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController
				.removeInstance(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.INSTANCE_ID);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

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
		Properties properties = new Properties();
		properties.put("scheduler_period", Long.toString(TestHelperData.SCHEDULER_PERIOD));
		managerController = new ManagerController(properties);

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(TestHelperData.INSTANCE_ID,
				SECOND_INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(TestHelperData.ACCESS_TOKEN_ID)).thenReturn(userToken);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD);
//		managerController.checkAndSubmitOpenRequests();

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController
				.removeInstance(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.INSTANCE_ID);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// getting second instance
		Thread.sleep(TestHelperData.SCHEDULER_PERIOD * 2);
//		managerController.checkAndSubmitOpenRequests();

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(SECOND_INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetOpenAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis() + TestHelperData.SCHEDULER_PERIOD;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				String.valueOf(DateUtils.getDateISO8601Format(expirationRequestTime)));

		// creating request
		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetFulfilledAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis() + TestHelperData.SCHEDULER_PERIOD
				+ TestHelperData.GRACE_TIME;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockOKRequestCompute();

		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD);

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD);

		// removing instance
		managerController
				.removeInstance(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.INSTANCE_ID);

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	private void mockOKRequestCompute() {
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(TestHelperData.INSTANCE_ID);
		managerController.setComputePlugin(computePlugin);
	}

	@Test
	public void testOneTimeRequestSetOpenAndClosed() throws InterruptedException {
		long expirationRequestTime = System.currentTimeMillis() + TestHelperData.SCHEDULER_PERIOD;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestConstants.DEFAULT_TYPE);
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		// creating request
		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

	}

	@Test
	public void testOneTimeRequestValidFromInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (TestHelperData.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + TestHelperData.LONG_TIME;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.ONE_TIME.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(), DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockOKRequestCompute();

		// creating request
		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		// request is not in validity period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		// request is in validity period
		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestValidFromInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (TestHelperData.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + TestHelperData.LONG_TIME;

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(), DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockOKRequestCompute();

		// creating request
		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		// request is not in validity period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		// request is in validity period
		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testOneTimeRequestValidityPeriodInFuture() throws InterruptedException {
		long now = System.currentTimeMillis();
		long startRequestTime = now + (TestHelperData.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + (TestHelperData.SCHEDULER_PERIOD * 3);

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.ONE_TIME.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(), DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockOKRequestCompute();
		
		// creating request
		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		// request is not in validity period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		// request is in validity period
		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// remove instance
		managerController
				.removeInstance(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.INSTANCE_ID);

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		// request is not in validity period anymore
		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

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
		long startRequestTime = now + (TestHelperData.SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + (TestHelperData.SCHEDULER_PERIOD * 3);

		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(), DateUtils.getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				DateUtils.getDateISO8601Format(expirationRequestTime));

		mockOKRequestCompute();
		
		// creating request
		managerController.createRequests(TestHelperData.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		// request is not in validity period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		// request is in validity period
		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(TestHelperData.INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		// remove instance
		managerController
				.removeInstance(TestHelperData.ACCESS_TOKEN_ID, TestHelperData.INSTANCE_ID);

		Thread.sleep(TestHelperData.SCHEDULER_PERIOD + TestHelperData.GRACE_TIME);

		// request is not in validity period anymore
		requests = managerController.getRequestsFromUser(TestHelperData.ACCESS_TOKEN_ID);

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
