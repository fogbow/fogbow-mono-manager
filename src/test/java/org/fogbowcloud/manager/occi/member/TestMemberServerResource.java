package org.fogbowcloud.manager.occi.member;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;

public class TestMemberServerResource {

	private final String ID_RESOURCEINFO1 = "id1";
	private final String ID_RESOURCEINFO2 = "id2";

	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AccountingPlugin accoutingPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private MapperPlugin mapperPlugin;

	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, "", "", "",
						"", "", ""));
		this.mapperPlugin = Mockito.mock(MapperPlugin.class);
		Map<String, Map<String, String>> defaultFederationUsersCrendetials = new HashMap<String, Map<String, String>>();
		HashMap<String, String> localUserCredentials = new HashMap<String, String>();
		defaultFederationUsersCrendetials.put("one", localUserCredentials);
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(
				defaultFederationUsersCrendetials);
		Mockito.when(mapperPlugin.getLocalCredentials("x_federation_auth_token")).thenReturn(
				localUserCredentials);

		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.accoutingPlugin = Mockito.mock(AccountingPlugin.class);
		this.authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		this.helper = new OCCITestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testGetMember() throws Exception {
		List<FederationMember> federationMembers = new ArrayList<FederationMember>();
		ResourcesInfo resourcesInfo1 = new ResourcesInfo(ID_RESOURCEINFO1, "2", "1", "100", "35",
				"", "");
		ResourcesInfo resourcesInfo2 = new ResourcesInfo(ID_RESOURCEINFO2, "2", "1", "100", "35",
				"", "");
		federationMembers.add(new FederationMember(resourcesInfo1));
		federationMembers.add(new FederationMember(resourcesInfo2));

		List<ResourcesInfo> resourcesInfo = new ArrayList<ResourcesInfo>();
		resourcesInfo.add(resourcesInfo1);
		resourcesInfo.add(resourcesInfo2);

		AsyncPacketSender packetSender = createPacketSenderMock(resourcesInfo);

		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, federationMembers, mapperPlugin, packetSender);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, "x_federation_auth_token");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		Assert.assertTrue(responseStr.contains(ID_RESOURCEINFO1));
		Assert.assertTrue(responseStr.contains(ID_RESOURCEINFO2));
		Assert.assertTrue(responseStr.contains(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	private AsyncPacketSender createPacketSenderMock(List<ResourcesInfo> resourcesInfo) {
		// mocking packet sender
		List<IQ> responses = new ArrayList<IQ>();

		for (ResourcesInfo resourceInfo : resourcesInfo) {
			IQ iq = new IQ();
			iq.setTo(resourceInfo.getId());
			iq.setType(Type.get);
			Element queryEl = iq.getElement().addElement("query",
					ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
			Element userEl = queryEl.addElement("token");
			userEl.addElement("accessId").setText("x_federation_auth_token");

			IQ response = IQ.createResultIQ(iq);
			queryEl = response.getElement().addElement("query",
					ManagerXmppComponent.GETREMOTEUSERQUOTA_NAMESPACE);
			Element resourceEl = queryEl.addElement("resourcesInfo");

			resourceEl.addElement("id").setText(resourceInfo.getId());
			resourceEl.addElement("cpuIdle").setText(resourceInfo.getCpuIdle());
			resourceEl.addElement("cpuInUse").setText(resourceInfo.getCpuInUse());
			resourceEl.addElement(ManagerPacketHelper.CPU_IN_USE_BY_USER).setText(resourceInfo.getCpuInUseByUser());
			resourceEl.addElement("instancesIdle").setText(resourceInfo.getInstancesIdle());
			resourceEl.addElement("instancesInUse").setText(resourceInfo.getInstancesInUse());
			resourceEl.addElement(ManagerPacketHelper.INSTANCES_IN_USE_BY_USER).setText(resourceInfo.getInstancesInUseByUser());
			resourceEl.addElement("memIdle").setText(resourceInfo.getMemIdle());
			resourceEl.addElement("memInUse").setText(resourceInfo.getMemInUse());
			resourceEl.addElement(ManagerPacketHelper.MEM_IN_USE_BY_USER).setText(resourceInfo.getMemInUseByUser());
			responses.add(response);
		}

		IQ firstResponse = responses.remove(0);
		IQ[] nextResponses = new IQ[responses.size()];
		responses.toArray(nextResponses);

		AsyncPacketSender packetSender = Mockito.mock(AsyncPacketSender.class);
		Mockito.when(packetSender.syncSendPacket(Mockito.any(IQ.class))).thenReturn(firstResponse,
				nextResponses);
		return packetSender;
	}

	@Test
	public void testGetMemberOnlyMe() throws Exception {
		List<FederationMember> federationMembers = new ArrayList<FederationMember>();
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, federationMembers, mapperPlugin);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, "x_federation_auth_token");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		// Should come with a single member (the manager itself)
		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		Assert.assertTrue(responseStr.contains(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetMemberWithoutFederationToken() throws Exception {
		List<FederationMember> federationMembers = new ArrayList<FederationMember>();
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, federationMembers, mapperPlugin);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetMemberWrongContentType() throws Exception {
		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, new LinkedList<FederationMember>(), mapperPlugin);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, "x_federation_auth_token");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificMemberQuota() throws Exception {
		List<FederationMember> federationMembers = new ArrayList<FederationMember>();
		String cpuIdle = "2";
		String cpuInUse = "1";
		String memIdle = "100";
		String memInUse = "35";
		String instancesIdle = "1";
		String instancesInUse = "10";
		ResourcesInfo resourcesInfo1 = new ResourcesInfo(ID_RESOURCEINFO1, cpuIdle, cpuInUse,
				memIdle, memInUse, instancesIdle, instancesInUse);
		ResourcesInfo resourcesInfo2 = new ResourcesInfo(ID_RESOURCEINFO2, cpuIdle, cpuInUse,
				memIdle, memInUse, "", "");
		federationMembers.add(new FederationMember(resourcesInfo1));
		federationMembers.add(new FederationMember(resourcesInfo2));

		List<ResourcesInfo> resourcesInfo = new ArrayList<ResourcesInfo>();
		resourcesInfo.add(resourcesInfo1);
		resourcesInfo.add(resourcesInfo2);

		AsyncPacketSender packetSender = createPacketSenderMock(resourcesInfo);

		this.helper.initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accoutingPlugin, federationMembers, mapperPlugin, packetSender);

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_MEMBER + "/" + ID_RESOURCEINFO1 + "/quota");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, "x_federation_auth_token");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		Assert.assertEquals(cpuIdle, getValueByKey(responseStr, "cpuIdle"));
		Assert.assertEquals(cpuInUse, getValueByKey(responseStr, "cpuInUse"));
		Assert.assertEquals(memIdle, getValueByKey(responseStr, "memIdle"));
		Assert.assertEquals(memInUse, getValueByKey(responseStr, "memInUse"));
		Assert.assertEquals(instancesIdle, getValueByKey(responseStr, "instancesIdle"));
		Assert.assertEquals(instancesInUse, getValueByKey(responseStr, "instancesInUse"));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());			
	}

	private String getValueByKey(String responseStr, String key) {
		String[] tokens = responseStr.split("\n");
		for (String token : tokens) {
			String prefix = OCCIHeaders.X_OCCI_ATTRIBUTE + ": " + key;
			if (token.startsWith(prefix)) {
				return token.replace(prefix + "=", "");
			}
		}
		return null;
	}

}
