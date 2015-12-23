package org.fogbowcloud.manager.core.model;

import org.junit.Assert;
import org.junit.Test;

public class TestResourceInfo {

	@Test
	public void testCalculateStringValuesIntValue() {
		String valueOne = "5";
		String ValueTwo = "5";
		Assert.assertEquals("10", ResourcesInfo.calculateStringValues(valueOne, ValueTwo));
	}
	
	@Test
	public void testCalculateStringValuesDoubleValue() {
		String valueOne = "5.5";
		String ValueTwo = "5.5";
		Assert.assertEquals("11.0", ResourcesInfo.calculateStringValues(valueOne, ValueTwo));		
	}	
	
}
