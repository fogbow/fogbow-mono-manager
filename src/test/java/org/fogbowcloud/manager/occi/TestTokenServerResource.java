package org.fogbowcloud.manager.occi;

import java.util.HashMap;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestTokenServerResource {

	private final String ACCESS_TOKEN_ID = "e723tgdjscbh1gyFV4GFC3OQ";

	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;

	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.helper = new OCCITestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testGetTokenWrongContentType() throws Exception {
		this.helper.initializeComponent(computePlugin, identityPlugin);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_TOKEN);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetToken() throws Exception {
		Token token = new Token(ACCESS_TOKEN_ID, "user", DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());

		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(token);

		this.helper.initializeComponent(computePlugin, identityPlugin);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_TOKEN);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		Assert.assertEquals(ACCESS_TOKEN_ID, responseStr);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetTokenUnauthorized() throws Exception {
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ""));

		this.helper.initializeComponent(computePlugin, identityPlugin);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_TOKEN);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}
}
