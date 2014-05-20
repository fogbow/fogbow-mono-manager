package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

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
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.xmpp.core.model.DateUtils;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestManagerController {

	private static final String INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private static final String SECOND_INSTANCE_ID = "rt22e67-5fgt-457a-3rt6-gt78124fhj9p";

	private static final Long SCHEDULER_PERIOD = 500L;
	public static final String USER_NAME = "user";
	public static final String USER_PASS = "password";
	public static final String ACCESS_TOKEN_ID = "HgjhgYUDFTGBgrbelihBDFGBÇuyrb";
	public static final String ACCESS_TOKEN_ID_2 = "2222CVXV23T4TG42VVCV";
	private static final Long GRACE_TIME = 30L;
	private static final String TENANT_NAME = "tenantName";
	private static final long LONG_TIME = 1 * 24 * 60 * 60 * 1000;

	private Token userToken;
	private ManagerController managerController;
	private ManagerTestHelper managerTestHelper;

	@Before
	public void setUp() throws Exception {
		managerController = new ManagerController(new Properties());
		managerTestHelper = new ManagerTestHelper();
		HashMap<String, String> tokenAttr = new HashMap<String, String>();
//		tokenAttr.put(OCCIHeaders.X_TOKEN_USER, USER_NAME);
		userToken = new Token(ACCESS_TOKEN_ID, USER_NAME, OCCITestHelper.TOKEN_FUTURE_EXPIRATION, tokenAttr);
	}

	@Test
	public void testGetFederationMember() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("federation_user_name", USER_NAME);
		properties.put("federation_user_password", USER_PASS);
		properties.put("federation_user_tenant_name", TENANT_NAME);
		managerController = new ManagerController(properties);
		OpenStackIdentityPlugin openStackidentityPlugin = Mockito
				.mock(OpenStackIdentityPlugin.class);
		Map<String, String> attributesToken = new HashMap<String, String>();
		attributesToken.put(OCCIHeaders.X_TOKEN_USER, USER_NAME);
		attributesToken.put(OCCIHeaders.X_TOKEN_PASS, USER_PASS);
		attributesToken.put(OCCIHeaders.X_TOKEN_TENANT_NAME, TENANT_NAME);

		long expirationTime = System.currentTimeMillis() + 500;

		Map<String, String> attributesTokenReturn = new HashMap<String, String>();
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_TENANT_ID, "987654321");
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_TENANT_NAME, TENANT_NAME);
//		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_USER, USER_NAME);
		Token token = new Token(ACCESS_TOKEN_ID, USER_NAME, new Date(expirationTime), attributesTokenReturn);

		Map<String, String> attributesTokenReturn2 = new HashMap<String, String>();
		attributesTokenReturn2.put(OCCIHeaders.X_TOKEN_TENANT_ID, "987654321");
		attributesTokenReturn2.put(OCCIHeaders.X_TOKEN_TENANT_NAME, TENANT_NAME);
//		attributesTokenReturn2.put(OCCIHeaders.X_TOKEN_USER, USER_NAME);

		Token token2 = new Token(ACCESS_TOKEN_ID_2, USER_NAME, new Date(expirationTime + LONG_TIME),
				attributesTokenReturn2);

		Mockito.when(openStackidentityPlugin.createToken(attributesToken)).thenReturn(token, token2);
		managerController.setIdentityPlugin(openStackidentityPlugin);

		// Get new token
		Token federationUserToken = managerController.getFederationUserToken();
		String accessToken = federationUserToken.getAccessId();
		Assert.assertEquals(ACCESS_TOKEN_ID, accessToken);

		// Use member token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(ACCESS_TOKEN_ID, accessToken);

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(expirationTime + GRACE_TIME);
		token.setDateUtils(dateUtils);

		// Get new token
		accessToken = managerController.getFederationUserToken().getAccessId();
		Assert.assertEquals(ACCESS_TOKEN_ID_2, accessToken);
	}

	@Test
	public void testcheckAndUpdateRequestToken() throws InterruptedException {
		final String firstTokenId = "firstTokenId";
		final String secontTokenId = "secondTokenId";
		final int timeDefault = 100;
		final long now = System.currentTimeMillis();

		Properties properties = new Properties();
		properties.put("token_update_period", Integer.toString(timeDefault));
		managerController = new ManagerController(properties);
		DateUtils dateUtils = Mockito.mock(DateUtils.class);

		RequestRepository requestRepository = new RequestRepository();
		Token token = new Token(firstTokenId, USER_NAME, new Date(now + (4 * timeDefault)),
				new HashMap<String, String>());
		Token token2 = new Token(firstTokenId, USER_NAME, new Date(now + (4 * timeDefault)),
				new HashMap<String, String>());
		Token token3 = new Token(firstTokenId, USER_NAME, new Date(now + (4 * timeDefault)),
				new HashMap<String, String>());
		Token token4 = new Token(firstTokenId, USER_NAME, new Date(now + (4 * timeDefault)),
				new HashMap<String, String>());
		Token token5 = new Token(firstTokenId, USER_NAME, new Date(now + (4 * timeDefault)),
				new HashMap<String, String>());
		Request request1 = new Request("id1", token, USER_NAME, null, null);
		request1.setState(RequestState.OPEN);
		Request request2 = new Request("id2", token2, USER_NAME, null, null);
		request2.setState(RequestState.OPEN);
		Request request3 = new Request("id3", token3, USER_NAME, null, null);
		request3.setState(RequestState.OPEN);
		Request request4 = new Request("id4", token4, USER_NAME, null, null);
		request4.setState(RequestState.CLOSED);
		Request request5 = new Request("id5", token5, USER_NAME, null, null);
		request5.setState(RequestState.FAILED);
		requestRepository.addRequest(USER_NAME, request1);
		requestRepository.addRequest(USER_NAME, request2);
		requestRepository.addRequest(USER_NAME, request3);
		requestRepository.addRequest(USER_NAME, request4);
		requestRepository.addRequest(USER_NAME, request5);
		managerController.setRequests(requestRepository);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Token tokenUpdatedFirstTime = new Token(secontTokenId, USER_NAME, new Date(System.currentTimeMillis()
				+ timeDefault), new HashMap<String, String>());
		Mockito.when(identityPlugin.createToken(Mockito.any(Token.class))).thenReturn(
				tokenUpdatedFirstTime);
		managerController.setIdentityPlugin(identityPlugin);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		managerController.setDateUtils(dateUtils);

		managerController.checkAndUpdateRequestToken(timeDefault);
		List<Request> requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().equals(RequestState.OPEN)) {
				Assert.assertEquals(firstTokenId, request.getToken().getAccessId());
			} else if (request.getState().equals(RequestState.CLOSED)
					|| request.getState().equals(RequestState.FAILED)) {
				Assert.assertEquals(firstTokenId, request.getToken().getAccessId());
			}
		}

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + (3 * timeDefault));
		managerController.setDateUtils(dateUtils);

		managerController.checkAndUpdateRequestToken(timeDefault);
		requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			if (request.getState().equals(RequestState.OPEN)) {
				Assert.assertEquals(secontTokenId, request.getToken().getAccessId());
			} else if (request.getState().equals(RequestState.CLOSED)
					|| request.getState().equals(RequestState.FAILED)) {
				Assert.assertEquals(firstTokenId, request.getToken().getAccessId());
			}
		}
	}

	@Test
	public void testMonitoringDeletedRequestAndFoundInstance() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Long.toString(LONG_TIME));
		managerController = new ManagerController(properties);

		Token token = new Token("id", USER_NAME, new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", token, USER_NAME, null, null);
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2", token, USER_NAME, null, null);
		request2.setState(RequestState.DELETED);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(USER_NAME, request1);
		requestRepository.addRequest(USER_NAME, request2);
		managerController.setRequests(requestRepository);
		RequestRepository requestRepositoryCopy = new RequestRepository();
		requestRepositoryCopy.addRequest(USER_NAME, request1);
		requestRepositoryCopy.addRequest(USER_NAME, request2);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenReturn(new Instance("id"));

		Assert.assertEquals(2, getRequestsDeleted(requestRepositoryCopy).size());

		managerController.monitorInstances();
		Assert.assertEquals(2, getRequestsDeleted(requestRepositoryCopy).size());
	}

	private List<Request> getRequestsDeleted(RequestRepository requestRepository) {
		List<Request> requests = new ArrayList<Request>();
		for (Request request : requestRepository.get(RequestState.DELETED)) {
			try {
				requests.add(managerController.getRequest(ACCESS_TOKEN_ID, request.getId()));
			} catch (Exception e) {
			}
		}
		return requests;
	}

	@Test
	public void testMonitoringDeletedRequestAndNotFoundInstance() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Long.toString(LONG_TIME));
		managerController = new ManagerController(properties);

		Token token = new Token("id", USER_NAME, new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", token, USER_NAME, null, null);
		request1.setState(RequestState.DELETED);
		Request request2 = new Request("id2", token, USER_NAME, null, null);
		request2.setState(RequestState.DELETED);
		Request request3 = new Request("id3", token, USER_NAME, null, null);
		request3.setState(RequestState.OPEN);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(USER_NAME, request1);
		requestRepository.addRequest(USER_NAME, request2);
		requestRepository.addRequest(USER_NAME, request3);
		managerController.setRequests(requestRepository);
		RequestRepository requestRepositoryCopy = new RequestRepository();
		requestRepositoryCopy.addRequest(USER_NAME, request1);
		requestRepositoryCopy.addRequest(USER_NAME, request2);
		requestRepositoryCopy.addRequest(USER_NAME, request3);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""));

		Assert.assertEquals(2, getRequestsDeleted(requestRepositoryCopy).size());

		managerController.monitorInstances();
		Assert.assertEquals(0, getRequestsDeleted(requestRepositoryCopy).size());
	}

	@Test
	public void testMonitoringFulfilledRequestAndNotFoundInstance() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Long.toString(LONG_TIME));
		managerController = new ManagerController(properties);

		Token token = new Token("id", USER_NAME, new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", token, USER_NAME, null, null);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", token, USER_NAME, null, null);
		request2.setState(RequestState.FULFILLED);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(USER_NAME, request1);
		requestRepository.addRequest(USER_NAME, request2);
		managerController.setRequests(requestRepository);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);

		managerController.monitorInstances();
		List<Request> requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}

		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""));

		managerController.monitorInstances();
		requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.CLOSED));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMonitoringFulfilledRequestAndPesistentInstance() throws InterruptedException {
		final int timeDefault = 100;
		final int timeDefaultSchedulePeriod = 10;
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Integer.toString(timeDefault));
		properties.put("scheduler_period", Integer.toString(timeDefaultSchedulePeriod));

		managerController = new ManagerController(properties);
		Token token = new Token("id", USER_NAME, new Date(), new HashMap<String, String>());
		Map<String, String> map = new HashMap<String, String>();
		map.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		Request request1 = new Request("id1", token, USER_NAME, null, map);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", token, USER_NAME, null, map);
		request2.setState(RequestState.FULFILLED);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(USER_NAME, request1);
		requestRepository.addRequest(USER_NAME, request2);
		managerController.setRequests(requestRepository);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.anyList(),
						Mockito.anyMap())).thenReturn("ok");

		List<Request> requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}

		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenThrow(new OCCIException(ErrorType.NOT_FOUND, ""));

		managerController.monitorInstances();
		boolean wasOpen = false;
		for (int i = 0; i < 50; i++) {
			Thread.sleep(1);
			for (Request request : requestsFromUser) {
				requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
				if (request.getState().equals(RequestState.OPEN)) {
					wasOpen = true;
				}
			}
		}

		Assert.assertTrue(wasOpen);

		Thread.sleep(timeDefaultSchedulePeriod * 100);

		requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}
	}

	@Test
	public void testMonitoringFulfilledRequestAndOnetimeInstance() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("instance_monitoring_period", Long.toString(LONG_TIME));
		managerController = new ManagerController(properties);

		Token token = new Token("id", USER_NAME, new Date(), new HashMap<String, String>());
		Request request1 = new Request("id1", token, USER_NAME, null, null);
		request1.setState(RequestState.FULFILLED);
		Request request2 = new Request("id2", token, USER_NAME, null, null);
		request2.setState(RequestState.FULFILLED);
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(USER_NAME, request1);
		requestRepository.addRequest(USER_NAME, request2);
		managerController.setRequests(requestRepository);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		managerController.setIdentityPlugin(identityPlugin);
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		managerController.setComputePlugin(computePlugin);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);

		managerController.monitorInstances();
		List<Request> requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
		for (Request request : requestsFromUser) {
			Assert.assertTrue(request.getState().equals(RequestState.FULFILLED));
		}

		Mockito.when(computePlugin.getInstance(Mockito.anyString(), Mockito.anyString()))
				.thenReturn(new Instance("id"));

		managerController.monitorInstances();
		requestsFromUser = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);
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

		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD * 2);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(USER_NAME, requests.get(0).getUser());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOneTimeRequestSetFulfilledAndClosed() throws InterruptedException {

		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController.removeInstance(ACCESS_TOKEN_ID, INSTANCE_ID);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestSetFulfilledAndOpen() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class)))
				.thenReturn(INSTANCE_ID)
				.thenThrow(
						new OCCIException(ErrorType.QUOTA_EXCEEDED,
								ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController.removeInstance(ACCESS_TOKEN_ID, INSTANCE_ID);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

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
		properties.put("scheduler_period", Long.toString(SCHEDULER_PERIOD));
		managerController = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID, SECOND_INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController.removeInstance(ACCESS_TOKEN_ID, INSTANCE_ID);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// getting second instance
		Thread.sleep(SCHEDULER_PERIOD * 2);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(SECOND_INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestSetOpenAndClosed() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		long expirationRequestTime = System.currentTimeMillis() + SCHEDULER_PERIOD;

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				String.valueOf(getDateISO8601Format(expirationRequestTime)));

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		// creating request
		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestSetFulfilledAndClosed() throws InterruptedException {

		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		long expirationRequestTime = System.currentTimeMillis() + SCHEDULER_PERIOD + GRACE_TIME;

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				getDateISO8601Format(expirationRequestTime));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD);

		// removing instance
		managerController.removeInstance(ACCESS_TOKEN_ID, INSTANCE_ID);

		Thread.sleep(SCHEDULER_PERIOD);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOneTimeRequestSetOpenAndClosed() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		long expirationRequestTime = System.currentTimeMillis() + SCHEDULER_PERIOD;

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestConstants.DEFAULT_TYPE);
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				getDateISO8601Format(expirationRequestTime));

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		// creating request
		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOneTimeRequestValidFromInFuture() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		long now = System.currentTimeMillis();
		long startRequestTime = now + (SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + LONG_TIME;

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.ONE_TIME.getValue()));
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(), getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				getDateISO8601Format(expirationRequestTime));

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		// creating request
		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		// request is not in validity period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		// request is in validity period
		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestValidFromInFuture() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		long now = System.currentTimeMillis();
		long startRequestTime = now + (SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + LONG_TIME;

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(), getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				getDateISO8601Format(expirationRequestTime));

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		// creating request
		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		// request is not in validity period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		// request is in validity period
		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOneTimeRequestValidityPeriodInFuture() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		long now = System.currentTimeMillis();
		long startRequestTime = now + (SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + (SCHEDULER_PERIOD * 3);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.ONE_TIME.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(), getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				getDateISO8601Format(expirationRequestTime));

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		// creating request
		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		// request is not in validity period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		// request is in validity period
		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// remove instance
		managerController.removeInstance(ACCESS_TOKEN_ID, INSTANCE_ID);

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		// request is not in validity period anymore
		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPersistentRequestValidityPeriodInFuture() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		long now = System.currentTimeMillis();
		long startRequestTime = now + (SCHEDULER_PERIOD * 2);
		long expirationRequestTime = now + (SCHEDULER_PERIOD * 3);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(), RequestType.PERSISTENT.getValue());
		xOCCIAtt.put(RequestAttribute.VALID_FROM.getValue(), getDateISO8601Format(startRequestTime));
		xOCCIAtt.put(RequestAttribute.VALID_UNTIL.getValue(),
				getDateISO8601Format(expirationRequestTime));

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(USER_NAME);
		Mockito.when(identityPlugin.getToken(ACCESS_TOKEN_ID)).thenReturn(userToken);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		// creating request
		managerController.createRequests(ACCESS_TOKEN_ID, new ArrayList<Category>(), xOCCIAtt);

		List<Request> requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		// request is not in validity period yet
		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		// request is in validity period
		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		// remove instance
		managerController.removeInstance(ACCESS_TOKEN_ID, INSTANCE_ID);

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		// request is not in validity period anymore
		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	public static String getDateISO8601Format(long dateMili) {
		SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
				FederationMember.ISO_8601_DATE_FORMAT, Locale.ROOT);
		dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));
		String expirationDate = dateFormatISO8601.format(new Date(dateMili));
		return expirationDate;
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

		RestrictCAsMemberValidator validatorMock = Mockito
				.mock(RestrictCAsMemberValidator.class);
		Mockito.doReturn(true).when(validatorMock).canDonateTo(member);
		managerController.setValidator(validatorMock);
		
		Token token = Mockito.mock(Token.class);
		Mockito.doReturn(null).when(token).getAccessId();
		
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(
				token);
		managerController.setIdentityPlugin(identityPlugin);
		
		ComputePlugin plugin = Mockito.mock(OpenStackComputePlugin.class);
		Mockito.doReturn("answer")
				.when(plugin)
				.requestInstance(null, null, null);
		
		managerController.setComputePlugin(plugin);
		Assert.assertEquals("answer", managerController
				.createInstanceForRemoteMember("abc", null, null));

		Mockito.doReturn(false).when(validatorMock).canDonateTo(member);
		managerController.setValidator(validatorMock);
		Assert.assertEquals(null, managerController
				.createInstanceForRemoteMember("abc", null, null));
	}
}
