package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.core.ssh.SSHTunnel;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.fogbowcloud.manager.xmpp.core.model.DateUtils;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestManagerController {

	private static final String INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private static final String SECOND_INSTANCE_ID = "rt22e67-5fgt-457a-3rt6-gt78124fhj9p";

	ManagerController managerController;
	ManagerTestHelper managerTestHelper;

	private static final Long SCHEDULER_PERIOD = 500L;
	public static final String USER_NAME = "user";
	public static final String USER_PASS = "password";
	public static final String ACCESS_TOKEN_ID = "HgjhgYUDFTGBgrbelihBDFGBÇuyrb";
	public static final String ACCESS_TOKEN_ID_2 = "2222CVXV23T4TG42VVCV";
	private static final Long GRACE_TIME = 30L;
	private static final String TENANT_NAME = "tenantName";
	private static final long LONG_TIME = 1 * 24 * 60 * 60 * 1000;

	@Before
	public void setUp() throws Exception {
		managerController = new ManagerController(new Properties());
		managerTestHelper = new ManagerTestHelper();
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

		long expirationTime = System.currentTimeMillis() + 250;

		String expirationDateStr = getDateISO8601Format(expirationTime);

		Map<String, String> attributesTokenReturn = new HashMap<String, String>();
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_ACCESS_ID,
				ACCESS_TOKEN_ID);
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_TENANT_ID, "987654321");
		attributesTokenReturn.put(OCCIHeaders.X_TOKEN_EXPIRATION_DATE,
				expirationDateStr);
		Token token = new Token(attributesTokenReturn);

		Map<String, String> attributesTokenReturn2 = new HashMap<String, String>();
		attributesTokenReturn2.put(OCCIHeaders.X_TOKEN_ACCESS_ID,
				ACCESS_TOKEN_ID_2);
		attributesTokenReturn2.put(OCCIHeaders.X_TOKEN_TENANT_ID, "987654321");
		attributesTokenReturn2.put(OCCIHeaders.X_TOKEN_EXPIRATION_DATE, "data");
		Token token2 = new Token(attributesTokenReturn2);

		Mockito.when(openStackidentityPlugin.getToken(attributesToken))
				.thenReturn(token, token2);
		managerController.setIdentityPlugin(openStackidentityPlugin);

		// Get new token
		Token federationUserToken = managerController.getFederationUserToken();
		String accessToken = federationUserToken
				.get(OCCIHeaders.X_TOKEN_ACCESS_ID);
		Assert.assertEquals(ACCESS_TOKEN_ID, accessToken);

		// Use member token
		accessToken = managerController.getFederationUserToken().get(
				OCCIHeaders.X_TOKEN_ACCESS_ID);
		Assert.assertEquals(ACCESS_TOKEN_ID, accessToken);

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(expirationTime + GRACE_TIME);
		token.setDateUtils(dateUtils);

		// Get new token
		accessToken = managerController.getFederationUserToken().get(
				OCCIHeaders.X_TOKEN_ACCESS_ID);
		Assert.assertEquals(ACCESS_TOKEN_ID_2, accessToken);
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
		FederationMember managerItem = new FederationMember(
				managerTestHelper.getResources());
		List<FederationMember> items = new LinkedList<FederationMember>();
		items.add(managerItem);
		managerController.updateMembers(items);

		List<FederationMember> members = managerController.getMembers();
		Assert.assertEquals(1, members.size());
		Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		Assert.assertEquals(1, managerController.getMembers().size());
	}

	@Test
	public void testGetManyItemsFromIQ() throws CertificateException,
			IOException {
		ArrayList<FederationMember> items = new ArrayList<FederationMember>();
		for (int i = 0; i < 10; i++) {
			items.add(new FederationMember(managerTestHelper.getResources()));
		}
		managerController.updateMembers(items);

		List<FederationMember> members = managerController.getMembers();
		Assert.assertEquals(10, members.size());
		for (int i = 0; i < 10; i++) {
			Assert.assertEquals("abc", members.get(0).getResourcesInfo()
					.getId());
		}
		Assert.assertEquals(10, managerController.getMembers().size());
	}

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
				computePlugin.requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn(INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(
				USER_NAME);
		Mockito.when(identityPlugin.getTokenExpiresDate(ACCESS_TOKEN_ID)).thenReturn(
				getDateISO8601Format(System.currentTimeMillis() + LONG_TIME));

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD * 2);

		List<Request> requests = managerController
				.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(USER_NAME, requests.get(0).getUser());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

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
				computePlugin.requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn(INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(
				USER_NAME);
		Mockito.when(identityPlugin.getTokenExpiresDate(ACCESS_TOKEN_ID)).thenReturn(
				getDateISO8601Format(System.currentTimeMillis() + LONG_TIME));

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerController
				.getRequestsFromUser(ACCESS_TOKEN_ID);

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
				computePlugin.requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn(INSTANCE_ID)
				.thenThrow(
						new OCCIException(ErrorType.QUOTA_EXCEEDED,
								ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(
				USER_NAME);
		Mockito.when(identityPlugin.getTokenExpiresDate(ACCESS_TOKEN_ID)).thenReturn(
				getDateISO8601Format(System.currentTimeMillis() + LONG_TIME));

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerController
				.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController.removeInstance(ACCESS_TOKEN_ID, INSTANCE_ID);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetFulfilledAndOpenAndFulfilled() throws InterruptedException {

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
				computePlugin.requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn(INSTANCE_ID, SECOND_INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(
				USER_NAME);
		Mockito.when(identityPlugin.getTokenExpiresDate(ACCESS_TOKEN_ID)).thenReturn(
				getDateISO8601Format(System.currentTimeMillis() + LONG_TIME));
		
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerController
				.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerController.removeInstance(ACCESS_TOKEN_ID, INSTANCE_ID);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		// getting second instance
		Thread.sleep(SCHEDULER_PERIOD * 2);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(SECOND_INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetOpenAndClosed() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class)))
				.thenThrow(
						new OCCIException(ErrorType.QUOTA_EXCEEDED,
								ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(
				USER_NAME);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID_2)).thenReturn(
				USER_NAME);

		long expirationTime = System.currentTimeMillis() + SCHEDULER_PERIOD;
		String expirationDateStr = getDateISO8601Format(expirationTime);
		
		Mockito.when(identityPlugin.getTokenExpiresDate(ACCESS_TOKEN_ID)).thenReturn(
				expirationDateStr);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		// creating request
		managerController.createRequests(ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID_2);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetFulfilledAndClosed() throws InterruptedException {

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
				computePlugin.requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn(INSTANCE_ID);

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(
				USER_NAME);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID_2)).thenReturn(
				USER_NAME);

		long expirationTime = System.currentTimeMillis() + SCHEDULER_PERIOD + GRACE_TIME;
		String expirationDateStr = getDateISO8601Format(expirationTime);

		Mockito.when(identityPlugin.getTokenExpiresDate(ACCESS_TOKEN_ID)).thenReturn(
				expirationDateStr);
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		managerController.createRequests(ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerController
				.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
		
		Thread.sleep(SCHEDULER_PERIOD);		
		
		// removing instance
		managerController.removeInstance(ACCESS_TOKEN_ID_2, INSTANCE_ID);

		Thread.sleep(SCHEDULER_PERIOD);
		
		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID_2);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));		
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}
	
	@Test
	public void testOneTimeRequestSetOpenAndClosed() throws InterruptedException {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerController = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				RequestConstants.DEFAULT_TYPE);

		// mocking compute
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(),
						Mockito.any(List.class), Mockito.any(Map.class)))
				.thenThrow(
						new OCCIException(ErrorType.QUOTA_EXCEEDED,
								ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		// mocking identity
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID)).thenReturn(
				USER_NAME);
		Mockito.when(identityPlugin.getUser(ACCESS_TOKEN_ID_2)).thenReturn(
				USER_NAME);

		long expirationTime = System.currentTimeMillis() + SCHEDULER_PERIOD;
		String expirationDateStr = getDateISO8601Format(expirationTime);

		Mockito.when(identityPlugin.getTokenExpiresDate(ACCESS_TOKEN_ID)).thenReturn(
				expirationDateStr);

		// mocking sshTunnel
		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerController.setIdentityPlugin(identityPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setSSHTunnel(sshTunnel);

		// creating request
		managerController.createRequests(ACCESS_TOKEN_ID,
				new ArrayList<Category>(), xOCCIAtt);

		List<Request> requests = managerController
				.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());

		Thread.sleep(SCHEDULER_PERIOD + GRACE_TIME);

		requests = managerController.getRequestsFromUser(ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.ONE_TIME.getValue(), requests.get(0)
				.getAttValue(RequestAttribute.TYPE.getValue()));
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

}
