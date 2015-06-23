package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestCloudStackComputePlugin {

	private static final String COMPUTE_DEFAULT_ZONE = "root";
	private static final String CLOUDSTACK_URL = "http://localhost:8080/client/api";

	private CloudStackComputePlugin createPlugin(HttpClientWrapper httpClient) {
		Properties properties = new Properties();
		properties.put("compute_cloudstack_api_url", CLOUDSTACK_URL);
		properties.put("compute_cloudstack_default_zone", COMPUTE_DEFAULT_ZONE);
		if (httpClient == null) {
			return new CloudStackComputePlugin(properties);
		} else {
			return new CloudStackComputePlugin(properties, httpClient);
		}
	}

	@Test
	public void testGetInstance() {
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String returned = "{ \"listvirtualmachinesresponse\" : { \"count\":1 ,\"virtualmachine\" : [  {\"id\":\"50b2b99a-8215-4437-9dfe-17382242e08c\",\"name\":\"ubuntu\""
				+ ",\"displayname\":\"ubuntu\",\"account\":\"admin\",\"domainid\":\"ae389e11-1385-11e5-be87-fa163ec5cca2\",\"domain\":\"ROOT\",\"created\":\"2015-06-17"
				+ "T16:45:41+0000\",\"state\":\"Running\",\"haenable\":false,\"zoneid\":\"d05bfc3e-85e5-4be8-9ae9-cc7c2deb95f1\""
				+ ",\"zonename\":\"zone1\",\"hostid\":\"0faa15c5-f1cc-4936-92b2-cf1a0f994592\",\"hostname\":\"cloudstack\""
				+ ",\"templateid\":\"9b23a921-9c4b-4745-a99c-9b7b1f7113f0\",\"templatename\":\"ubuntu-1404\",\"templated"
				+ "isplaytext\":\"ubuntu-1404\",\"passwordenabled\":false,\"serviceofferingid\":\"62d5f174-2f1e-42f0-931e-07600a054"
				+ "70e\",\"serviceofferingname\":\"Small Instance\",\"cpunumber\":1,\"cpuspeed\":500,\"memory\":512,\"cpuused\":\"0.08%\","
				+ "\"networkkbsread\":13833,\"networkkbswrite\":437,\"diskkbsread\":206572,\"diskkbswrite\":489280,\"diskioread\":14261,"
				+ "\"diskiowrite\":6032,\"guestosid\":\"b10e3358-1385-11e5-be87-fa163ec5cca2\",\"rootdeviceid\":0,\"rootdevicetype\":\"ROOT\",\"securitygroup\":"
				+ "[],\"nic\":[{\"id\":\"d76fd928-022d-44ff-a7a3-6d49bb95e4e1\",\"networkid\":\"b2eba1d4-74af-4530-b4c0-d5c5716bb74c\","
				+ "\"networkname\":\"guestnet1\",\"netmask\":\"255.255.255.0\",\"gateway\":\"192.168.100.1\",\"ipaddress\":"
				+ "\"192.168.100.227\",\"broadcasturi\":\"vlan://untagged\",\"traffictype\":\"Guest\",\"type\":\"Shared\","
				+ "\"isdefault\":true,\"macaddress\":\"06:b1:5c:00:00:81\"}],\"hypervisor\":\"KVM\",\"instancename\":\"i-2-900-VM\","
				+ "\"tags\":[],\"keypair\":\"admin-keypair\",\"affinitygroup\":[],\"displayvm\":true,\"isdynamicallyscalable\":false,"
				+ "\"ostypeid\":164} ] } }";
		Mockito.when(httpClient.doGet(Mockito.anyString()))
				.thenReturn(returned);
		CloudStackComputePlugin cscp = createPlugin(httpClient);
		Token token = new Token("api:key", null, null, null);
		List<Instance> instances = cscp.getInstances(token);
		Assert.assertEquals(1, instances.size());
	}

}
