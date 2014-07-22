package org.fogbowcloud.manager.occi.plugins;


import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;
import org.fogbowcloud.manager.core.plugins.voms.VomsIdentityPlugin;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TestIdentityVoms {
	
	VomsIdentityPlugin vomsIdentityPlugin;

	@Before
	public void setUp() {
		this.vomsIdentityPlugin = new VomsIdentityPlugin(null);
	}
	
	@Ignore
	@Test
	public void testValidCetificate() throws Exception {
		FileInputStream fileInputStream = new FileInputStream(
				"src/test/resources/voms/certs/test0.cert.pem");	
		
		String myString = IOUtils.toString(fileInputStream, "UTF-8");
		Assert.assertTrue(vomsIdentityPlugin.isValid(myString));
		
	}
	
	@Ignore
	@Test
	public void testExpiredCertificate() throws Exception {
		FileInputStream fileInputStream = new FileInputStream(
				"src/test/resources/voms/certs/expired.cert.pem");

		String myString = IOUtils.toString(fileInputStream, "UTF-8");
		Assert.assertFalse(vomsIdentityPlugin.isValid(myString));
	}
	
	@Ignore
	@Test
	public void testNotYetValidCertificate() throws Exception {
		FileInputStream fileInputStream = new FileInputStream(
				"src/test/resources/voms/certs/expired.cert.pem");

		String myString = IOUtils.toString(fileInputStream, "UTF-8");
		Assert.assertFalse(vomsIdentityPlugin.isValid(myString));
	}	
}
