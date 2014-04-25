package org.fogbowcloud.manager.occi.core;

import org.fogbowcloud.manager.occi.instance.Instance;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class TestInstance {

	@Test
	public void testParseIdOneValue() {
		String textResponse = "X-OCCI-Location: http://localhost:8787/compute/c1490";
		Instance instance = Instance.parseInstance(textResponse);

		Assert.assertEquals(textResponse, instance.toOCCIMassageFormatLocation());
	}
	
	//TODO refatoring
	@Test
	public void testParseDetails() {
		String textResponse = "Category: compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; "
				+ "title=\"Compute Resource\"; rel=\"http://schemas.ogf.org/occi/core#resource\"; "
				+ "location=\"http://localhost:8787/compute/\"; "
				+ "attributes=\"occi.compute.architecture occi.compute.state{immutable} "
				+ "occi.compute.speed occi.compute.memory occi.compute.cores occi.compute.hostname\"; "
				+ "actions=\"http://schemas.ogf.org/occi/infrastructure/compute/action#start http://sc"
				+ "hemas.ogf.org/occi/infrastructure/compute/action#stop "
				+ "http://schemas.ogf.org/occi/infrastructure/compute/action#restart "
				+ "http://schemas.ogf.org/occi/infrastructure/compute/action#suspend\"\n"
//				+ "Category: os_vms; scheme=\"http://schemas.openstack.org/compute/instance#\"; class=\"mixin\"; location=\"http://localhost:8787/os_vms/\"; attributes=\"org.openstack.compute.console.vnc{immutable} org.openstack.compute.state{immutable}\"; actions=\"http://schemas.openstack.org/instance/action#chg_pwd http://schemas.openstack.org/instance/action#create_image\"\n"
//				+ "Category: b4c4322e-1f6c-4cf4-be02-34cc385a3b29; scheme=\"http://schemas.openstack.org/template/os#\"; class=\"mixin\"; title=\"Image: cirros-0.3.1-x86_64-uec\"; rel=\"http://schemas.ogf.org/occi/infrastructure#os_tpl\"; location=\"http://localhost:8787/b4c4322e-1f6c-4cf4-be02-34cc385a3b29/\"\n"
				+ "Link: </network/admin>; rel=\"http://schemas.ogf.org/occi/infrastructure#network\"; "
				+ "self=\"/network/interface/d7fe049b-71a3-4a91-b74b-bd191a79d423\"; "
				+ "category=\"http://schemas.ogf.org/occi/infrastructure#networkinterface "
				+ "http://schemas.ogf.org/occi/infrastructure/networkinterface#ipnetworkinterface\"; "
				+ "occi.networkinterface.gateway=\"10.0.0.1\"; "
				+ "occi.networkinterface.mac=\"fa:16:3e:5b:9d:4d\"; occi.networkinterface.interface=\"et"
				+ "h0\"; occi.networkinterface.state=\"active\"; occi.networkinterface.allocation=\"stat"
				+ "ic\"; occi.networkinterface.address=\"0.0.0.0\"; occi.core.source=\"/compute/c14902"
				+ "07-6be7-4303-a41c-f8a4b1b9c25d\"; occi.core.target=\"/network/admin\"; occi.core.id="
				+ "\"/network/interface/d7fe049b-71a3-4a91-b74b-bd191a79d423\"\n"
				+ "X-OCCI-Attribute: org.openstack.compute.console.vnc=\"N/A\"\n"
				+ "X-OCCI-Attribute: occi.compute.architecture=\"x86\"\n"
				+ "X-OCCI-Attribute: occi.compute.state=\"inactive\"\n"
				+ "X-OCCI-Attribute: occi.compute.speed=\"0.0\"\n"
				+ "X-OCCI-Attribute: org.openstack.compute.state=\"errorz\"\n"
				+ "X-OCCI-Attribute: occi.compute.memory=\"0.125\"\n"
				+ "X-OCCI-Attribute: occi.compute.cores=\"1\"\n"
				+ "X-OCCI-Attribute: occi.compute.hostname=\"server-c1490207-6be7-4303-a41c-f"
				+ "8a4b1b9c25d\"\n"
				+ "X-OCCI-Attribute: occi.core.id=\"c1490207-6be7-4303-a41c-f8a4b1b9c25d\"";
		Instance instance = Instance.parseInstance("id", textResponse);
		
		Assert.assertEquals(textResponse, instance.toOCCIMassageFormatDetails());
	}
}
