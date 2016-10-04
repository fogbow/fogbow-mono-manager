package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.CapacityControllerPlugin;
import org.junit.Before;
import org.junit.Test;

public class TestFairnessDrivenCapacityController {

	FairnessDrivenCapacityController fairnessDrivenCapacityController;
	
	@Before
	public void setUp() {
		Properties properties = new Properties();
		properties.put(FairnessDrivenCapacityController.CONTROLLER_DELTA, String.valueOf(0));
		properties.put(FairnessDrivenCapacityController.CONTROLLER_MINIMUM_THRESHOLD, String.valueOf(0));
		properties.put(FairnessDrivenCapacityController.CONTROLLER_MAXIMUM_THRESHOLD, String.valueOf(0));
		AccountingPlugin accountingPlugin = null;
		this.fairnessDrivenCapacityController = new PairwiseFairnessDrivenController(
				properties, accountingPlugin);
	}
	
	@Test
	public void testNormalizeMaxCapacity() {
			double value = 10.0;
			double maximumCapacity = this.fairnessDrivenCapacityController.normalizeMaximumCapacity(value);
			
			assertEquals(String.valueOf(value), String.valueOf(maximumCapacity));
			
			// get last maximum capacity 
			maximumCapacity = this.fairnessDrivenCapacityController.normalizeMaximumCapacity(
					CapacityControllerPlugin.MAXIMUM_CAPACITY_VALUE_ERROR);
			
			assertEquals(String.valueOf(value), String.valueOf(maximumCapacity));
	}
	
	@Test
	public void testNormalizeMaxCapacityWithMaxCapacityErrorAndLastCapacityDefault() {	
		double maximumCapacity = this.fairnessDrivenCapacityController.normalizeMaximumCapacity(
				CapacityControllerPlugin.MAXIMUM_CAPACITY_VALUE_ERROR);
		
		assertEquals(String.valueOf(Double.MAX_VALUE), String.valueOf(maximumCapacity));
	}	
	
}
