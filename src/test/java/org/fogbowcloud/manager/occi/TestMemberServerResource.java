package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestMemberServerResource {

	private final String FLAVOUR_1 = "flavour1";
	private final String FLAVOUR_2 = "flavour1";
	private final String ID_RESOURCEINFO1 = "id1";
	private final String ID_RESOURCEINFO2 = "id2";

	private OCCITestHelper helper;

	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testGetMember() throws Exception {
		
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);

		List<FederationMember> federationMembers = new ArrayList<FederationMember>();
		List<Flavor> flavours = new ArrayList<Flavor>();
		flavours.add(new Flavor(FLAVOUR_1, "3", "135", 2));
		flavours.add(new Flavor(FLAVOUR_2, "3", "135", 2));
		ResourcesInfo resourcesInfo = new ResourcesInfo(ID_RESOURCEINFO1, "2", "1", "100", "35",
				flavours);
		ResourcesInfo resourcesInfo2 = new ResourcesInfo(ID_RESOURCEINFO2, "2", "1", "100", "35",
				null);
		federationMembers.add(new FederationMember(resourcesInfo));
		federationMembers.add(new FederationMember(resourcesInfo));
		federationMembers.add(new FederationMember(resourcesInfo2));

		this.helper.initializeComponentMember(computePlugin, identityPlugin, federationMembers);		
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		Assert.assertTrue(checkResponse(responseStr));
		Assert.assertTrue(responseStr.contains(FLAVOUR_1));
		Assert.assertTrue(responseStr.contains(FLAVOUR_2));
		Assert.assertTrue(responseStr.contains(ID_RESOURCEINFO1));
		Assert.assertTrue(responseStr.contains(ID_RESOURCEINFO2));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetMemberEmpty() throws Exception {
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);

		this.helper.initializeComponentMember(computePlugin, identityPlugin, null);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(" ", responseStr);
	}
	
	@Test
	public void testGetMemberWrongContentType() throws Exception {
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);

		this.helper.initializeComponentMember(computePlugin, identityPlugin, null);
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}	
	
	private boolean checkResponse(String response) {
		String[] tokens = response.split("\n");
		for (String token : tokens) {
			if (!token.contains("id=")) {
				return false;
			}
		}
		return true;
	}
}
