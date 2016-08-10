package org.fogbowcloud.manager.core.plugins.capacitycontroller;

import static org.junit.Assert.*;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.capacitycontroller.satisfactiondriven.SatisfactionDrivenCapacityControllerPlugin;
import org.junit.Test;

public class TestSatisfactionDrivenCapacityControllerPlugin {

	private final double ACCEPTABLE_ERROR = 0.000001;
	
	@Test
	public void testGetMaxCapacityToSupply() {
		FederationMember remoteMember = new FederationMember(new ResourcesInfo("remoteMember", "", "", "", "", "", "", "", "", ""));
		assertEquals(Double.MAX_VALUE, new SatisfactionDrivenCapacityControllerPlugin().getMaxCapacityToSupply(remoteMember), ACCEPTABLE_ERROR);
	}


}
