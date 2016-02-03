package org.fogbowcloud.manager.occi.order;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.Response;

public class TestDeleteOrder {

	OCCITestHelper orderHelper;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		this.orderHelper = new OCCITestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
						Mockito.any(Map.class), Mockito.anyString())).thenReturn("instanceid");
		Mockito.doThrow(
				new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX))
				.when(computePlugin)
				.bypass(Mockito.any(org.restlet.Request.class), Mockito.any(Response.class));

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(
						new Token("id", OCCITestHelper.USER_MOCK, new Date(),
								new HashMap<String, String>()));
		Mockito.when(identityPlugin.getToken(OCCITestHelper.INVALID_TOKEN)).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));
		
		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		this.orderHelper.initializeComponent(computePlugin, identityPlugin, authorizationPlugin);
	}

	@Test
	public void testOrder() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_ORDER);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteEmptyOrder() throws URISyntaxException, HttpException, IOException {
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Delete
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_ORDER);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(delete);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Get
		client = HttpClients.createMinimal();
		response = client.execute(get);
		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificOrder() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		List<String> orderLocations = OCCITestHelper.getOrderIdsPerLocationHeader(response);

		Assert.assertEquals(1, orderLocations.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		// Delete
		HttpDelete delete = new HttpDelete(orderLocations.get(0));
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(delete);
		final int deletedOrderAmount = 1;

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);

		Assert.assertEquals(deletedOrderAmount, deletedInstancesCounter(response));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteManyOrdersIndividually() throws URISyntaxException, HttpException,
			IOException, InterruptedException {
		final int defaultAmount = 5;

		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = " + defaultAmount);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		List<String> orderLocations = OCCITestHelper.getOrderIdsPerLocationHeader(response);

		Assert.assertEquals(defaultAmount, orderLocations.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

		// Delete all orders individually
		HttpDelete delete;
		for (String orderLocation : orderLocations) {
			delete = new HttpDelete(orderLocation);
			delete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
			delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
			client = HttpClients.createMinimal();
			response = client.execute(delete);
			Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		}
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(get);
		
		Assert.assertEquals(defaultAmount, deletedInstancesCounter(response));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteAllOrders() throws URISyntaxException, HttpException, IOException {
		final int createdMount = 5;

		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.INSTANCE_COUNT.getValue()
				+ " = " + createdMount);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		List<String> orderIDs = OCCITestHelper.getOrderIdsPerLocationHeader(response);

		Assert.assertEquals(createdMount, orderIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

		// Delete
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_ORDER);
		delete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = HttpClients.createMinimal();
		response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_ORDER);
		client = HttpClients.createMinimal();
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(createdMount, deletedInstancesCounter(response));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteOrderNotFound() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_ORDER + "not_found_id");
		delete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteWithInvalidToken() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_ORDER);
		delete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.INVALID_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	private int deletedInstancesCounter(HttpResponse response) throws ParseException, IOException,
			URISyntaxException, HttpException {
		HttpClient client = HttpClients.createMinimal();
		List<String> orderLocations2 = OCCITestHelper.getLocationIds(response);
		int countDeletedInscantes = 0;
		for (String orderLocation : orderLocations2) {
			HttpGet getSpecific = new HttpGet(orderLocation);
			getSpecific.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
			getSpecific.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
			response = client.execute(getSpecific);
			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));
			if (responseStr.contains(OrderState.DELETED.getValue())) {
				countDeletedInscantes++;
			}
		}
		return countDeletedInscantes;
	}

	@After
	public void tearDown() throws Exception {
		this.orderHelper.stopComponent();
	}
}
