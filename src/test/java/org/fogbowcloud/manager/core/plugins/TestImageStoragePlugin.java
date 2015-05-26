package org.fogbowcloud.manager.core.plugins;

import org.junit.Assert;
import org.junit.Test;

public class TestImageStoragePlugin {
		
	@SuppressWarnings("rawtypes")
	@Test
	public void testEnumExtensions() {		
		for (Enum enumExpresion : ImageStoragePlugin.Extensions.values()) {
			Assert.assertTrue(ImageStoragePlugin.Extensions.in(enumExpresion.toString()));			
		}
	}

	@Test(expected=Throwable.class)
	public void testEnumExtensionsWrong() {
		Assert.assertTrue(ImageStoragePlugin.Extensions.in("Wrong"));
	}	
}
