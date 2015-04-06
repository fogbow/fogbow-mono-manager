package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestOCCIApplication {

	private static final String INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private static final Long SCHEDULER_PERIOD = 500L;

	private OCCIApplication occiApplication;
	private Map<String, String> xOCCIAtt;
	private ManagerController managerFacade;

	@SuppressWarnings("unchecked")
	@Before
	public void setUp() {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		properties.put(ConfigurationConstants.TUNNEL_SSH_PRIVATE_HOST_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TUNNEL_SSH_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		
		managerFacade = new ManagerController(properties);
		occiApplication = new OCCIApplication(managerFacade);

		// default instance count value is 1
		xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
						Mockito.any(Map.class), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		HashMap<String, String> tokenAttr = new HashMap<String, String>();
		Token userToken = new Token(OCCITestHelper.FED_ACCESS_TOKEN, OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, tokenAttr);

		Mockito.when(identityPlugin.getToken(Mockito.anyString())).thenReturn(userToken);

		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		managerFacade.setAuthorizationPlugin(authorizationPlugin);
		managerFacade.setLocalIdentityPlugin(identityPlugin);
		managerFacade.setFederationIdentityPlugin(identityPlugin);
		managerFacade.setComputePlugin(computePlugin);
	}

	@Test
	public void testGetRequestDetails() throws InterruptedException {
		occiApplication.createRequests(OCCITestHelper.FED_ACCESS_TOKEN, OCCITestHelper.LOCAL_ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);
		List<Request> requests = occiApplication.getRequestsFromUser(OCCITestHelper.FED_ACCESS_TOKEN);
		Assert.assertEquals(1, requests.size());
		String requestId = requests.get(0).getId();
		Request requestDetails = occiApplication.getRequest(OCCITestHelper.FED_ACCESS_TOKEN, requestId);

		Thread.sleep(100);
		Assert.assertEquals(requestId, requestDetails.getId());
		Assert.assertNull(requestDetails.getInstanceId());
		Assert.assertEquals(RequestState.OPEN, requestDetails.getState());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetRequestDetailsAfterPeriod() throws InterruptedException {
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
						Mockito.any(Map.class), Mockito.anyString())).thenReturn(INSTANCE_ID);

		managerFacade.setComputePlugin(computePlugin);
		occiApplication.createRequests(OCCITestHelper.FED_ACCESS_TOKEN,
				OCCITestHelper.LOCAL_ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);
		List<Request> requests = occiApplication.getRequestsFromUser(OCCITestHelper.FED_ACCESS_TOKEN);
		Assert.assertEquals(1, requests.size());
		String requestId = requests.get(0).getId();
		Request requestDetails = occiApplication.getRequest(OCCITestHelper.FED_ACCESS_TOKEN, requestId);

		Thread.sleep(SCHEDULER_PERIOD * 2);

		Assert.assertEquals(requestId, requestDetails.getId());
		Assert.assertEquals(INSTANCE_ID, requestDetails.getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requestDetails.getState());
	}

	@Test
	public void testResquestUser() {
		this.occiApplication.createRequests(OCCITestHelper.FED_ACCESS_TOKEN, OCCITestHelper.LOCAL_ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);
		List<Request> requestsFromUser = occiApplication
				.getRequestsFromUser(OCCITestHelper.FED_ACCESS_TOKEN);

		Assert.assertEquals(1, requestsFromUser.size());
	}

	@Test
	public void testManyResquestUser() {
		int numberOfInstances = 10;
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(), String.valueOf(numberOfInstances));
		this.occiApplication.createRequests(OCCITestHelper.FED_ACCESS_TOKEN, OCCITestHelper.LOCAL_ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);
		List<Request> requestsFromUser = occiApplication
				.getRequestsFromUser(OCCITestHelper.FED_ACCESS_TOKEN);

		Assert.assertEquals(numberOfInstances, requestsFromUser.size());
	}

	@Test
	public void testRemoveAllRequest() {
		int numberOfInstances = 10;
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(), String.valueOf(numberOfInstances));
		occiApplication.createRequests(OCCITestHelper.FED_ACCESS_TOKEN, OCCITestHelper.LOCAL_ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requestsFromUser = occiApplication
				.getRequestsFromUser(OCCITestHelper.FED_ACCESS_TOKEN);

		Assert.assertEquals(numberOfInstances, requestsFromUser.size());

		occiApplication.removeAllRequests(OCCITestHelper.FED_ACCESS_TOKEN);
		requestsFromUser = occiApplication.getRequestsFromUser(OCCITestHelper.FED_ACCESS_TOKEN);

		Assert.assertEquals(numberOfInstances, counterDeletedRequests(requestsFromUser));
	}

	@Test
	public void testRemoveSpecificRequest() {
		int numberOfInstances = 10;
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(), String.valueOf(numberOfInstances));
		occiApplication.createRequests(OCCITestHelper.FED_ACCESS_TOKEN, OCCITestHelper.LOCAL_ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);

		List<Request> requestsFromUser = occiApplication
				.getRequestsFromUser(OCCITestHelper.FED_ACCESS_TOKEN);

		Assert.assertEquals(numberOfInstances, requestsFromUser.size());

		occiApplication.removeRequest(OCCITestHelper.FED_ACCESS_TOKEN, requestsFromUser.get(1).getId());
		requestsFromUser = occiApplication.getRequestsFromUser(OCCITestHelper.FED_ACCESS_TOKEN);

		Assert.assertEquals(1, counterDeletedRequests(requestsFromUser));
	}

	private int counterDeletedRequests(List<Request> requestsFromUser) {
		int count = 0;
		for (Request request : requestsFromUser) {
			if (request.getState().equals(RequestState.DELETED)) {
				count++;
			}
		}
		return count;
	}
}
