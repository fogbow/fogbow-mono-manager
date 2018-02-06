package org.fogbowcloud.manager.occi.federatednetwork;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestFederatedNetworkResource {

	private OCCITestHelper orderHelper;

	private ManagerController facade;

	@Before
	public void setUp() throws Exception {
		this.orderHelper = new OCCITestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);

		MapperPlugin mapperPlugin = Mockito.mock(MapperPlugin.class);

		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);

		BenchmarkingPlugin benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);

		facade = this.orderHelper.initializeComponentExecutorSameThread(computePlugin,
				identityPlugin, authorizationPlugin, benchmarkingPlugin, mapperPlugin);
	}

	@After
	public void tearDown() throws Exception {
		TestDataStorageHelper.clearManagerDataStore(
				this.facade.getManagerDataStoreController().getManagerDatabase());
		this.orderHelper.stopComponent();
	}

	@Test
	public void testGet() throws ClientProtocolException, IOException {
		String FNId = "fake-id";

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/" + FNId);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(responseString.contains(FNId));
		client.close();
	}

	@Test
	public void testGetWithoutFNId() throws ClientProtocolException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(responseString.contains("Networks IDs"));
		client.close();
	}

	@Test
	public void testGetWithoutAuthentication() throws ClientProtocolException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		client.close();
	}

	@Test
	public void testPost() throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		String CIDR = "10.10.10.0/24";
		post.addHeader(OCCIConstants.FEDERATED_NETWORK_CIDR, CIDR);

		String label = "virtualized-network";
		post.addHeader(OCCIConstants.FEDERATED_NETWORK_LABEL, label);

		String membersList[] = new String[] { "lsd.manager.something", "alemanha.naf.something" };
		for (String member : membersList) {
			post.addHeader(OCCIConstants.FEDERATED_NETWORK_MEMBER, member);
		}

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(responseString.contains(CIDR));
		Assert.assertTrue(responseString.contains(label));
		for (String member : membersList) {
			Assert.assertTrue(responseString.contains(member));
		}

		client.close();
	}

	@Test
	public void testPostWithoutCIDR() throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		String label = "virtualized-network";
		post.addHeader(OCCIConstants.FEDERATED_NETWORK_LABEL, label);

		String membersList[] = new String[] { "lsd.manager.something", "alemanha.naf.something" };
		for (String member : membersList) {
			post.addHeader(OCCIConstants.FEDERATED_NETWORK_MEMBER, member);
		}

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
		client.close();
	}

	@Test
	public void testPostWithoutLabel() throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		String CIDR = "10.10.10.0/24";
		post.addHeader(OCCIConstants.FEDERATED_NETWORK_CIDR, CIDR);

		String membersList[] = new String[] { "lsd.manager.something", "alemanha.naf.something" };
		for (String member : membersList) {
			post.addHeader(OCCIConstants.FEDERATED_NETWORK_MEMBER, member);
		}

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
		client.close();
	}

	@Test
	public void testPostWithoutMember() throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		String CIDR = "10.10.10.0/24";
		post.addHeader(OCCIConstants.FEDERATED_NETWORK_CIDR, CIDR);

		String label = "virtualized-network";
		post.addHeader(OCCIConstants.FEDERATED_NETWORK_LABEL, label);

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
		client.close();
	}

	@Test
	public void testPostIncomplete() throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
		client.close();
	}

	@Test
	public void testPostWithoutAuthentication() throws ClientProtocolException, IOException {
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);

		String CIDR = "10.10.10.0/24";
		post.addHeader(OCCIConstants.FEDERATED_NETWORK_CIDR, CIDR);

		String label = "virtualized-network";
		post.addHeader(OCCIConstants.FEDERATED_NETWORK_LABEL, label);

		String membersList[] = new String[] { "lsd.manager.something", "alemanha.naf.something" };
		for (String member : membersList) {
			post.addHeader(OCCIConstants.FEDERATED_NETWORK_MEMBER, member);
		}

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		client.close();
	}

	@Test
	public void testPut() throws ClientProtocolException, IOException {
		String FNId = "fake-id";

		HttpPut post = new HttpPut(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/" + FNId);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		String membersList[] = new String[] { "lsd.manager.something", "alemanha.naf.something" };
		for (String member : membersList) {
			post.addHeader(OCCIConstants.FEDERATED_NETWORK_MEMBER, member);
		}

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(responseString.contains(FNId));
		for (String member : membersList) {
			Assert.assertTrue(responseString.contains(member));
		}

		client.close();
	}
	
	@Test
	public void testPutWithoutFNId() throws ClientProtocolException, IOException {
		HttpPut post = new HttpPut(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		String membersList[] = new String[] { "lsd.manager.something", "alemanha.naf.something" };
		for (String member : membersList) {
			post.addHeader(OCCIConstants.FEDERATED_NETWORK_MEMBER, member);
		}

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
		client.close();
	}
	
	@Test
	public void testPutWithoutMember() throws ClientProtocolException, IOException {
		String FNId = "fake-id";

		HttpPut post = new HttpPut(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/" + FNId);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
		client.close();
	}
}
