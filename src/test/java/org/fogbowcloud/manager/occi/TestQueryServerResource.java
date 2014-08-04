package org.fogbowcloud.manager.occi;

import java.util.Date;
import java.util.HashMap;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.Request;
import org.restlet.Response;

public class TestQueryServerResource {

	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;

	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(Request.class), Mockito.any(Response.class));
		
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(
						new Token("id", OCCITestHelper.USER_MOCK, new Date(),
								new HashMap<String, String>()));
		
		this.authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAutorized(Mockito.any(Token.class))).thenReturn(true);
		
		this.helper = new OCCITestHelper();
		this.helper.initializeComponent(computePlugin, identityPlugin, authorizationPlugin);
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testGetQueryDifferentContentType() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "text/plain");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetQueryWithoutAccessToken() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
	}

	@Test
	public void testGetQuery() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		for (Resource resource : ResourceRepository.getInstance().getAll()) {
			Assert.assertTrue(responseStr.contains(resource.toHeader()));
		}

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testHeadQueryWithoutAccessToken() throws Exception {

		HttpHead head = new HttpHead(OCCITestHelper.URI_FOGBOW_QUERY);
		head.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(head);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
		Assert.assertEquals("0", response.getFirstHeader("Content-length").getValue());
	}
}
