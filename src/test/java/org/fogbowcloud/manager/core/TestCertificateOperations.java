package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Test;

public class TestCertificateOperations {

	@Test
	public void testGetCertificate() throws IOException, CertificateException {
		ManagerTestHelper helper = new ManagerTestHelper();
		Certificate cert = CertificateHandlerHelper.getCertificate(helper
				.getProperties());
		Certificate result = CertificateHandlerHelper
				.parseCertificate(CertificateHandlerHelper
						.getBase64Certificate(helper.getProperties()));
		Assert.assertEquals(cert, result);
	}

	@Test
	public void testEmptyCertificatePath() throws CertificateException, IOException {
		String configPath = "src/test/resources/manager.conf.invalidTest";
		ManagerTestHelper helper = new ManagerTestHelper();
		Certificate cert = CertificateHandlerHelper.getCertificate(helper
				.getProperties(configPath));
		Assert.assertEquals(null, cert);
		Certificate result = CertificateHandlerHelper
				.parseCertificate(CertificateHandlerHelper
						.getBase64Certificate(helper.getProperties(configPath)));
		Assert.assertEquals(null, result);
	}
	
	@Test
	public void testWrongCertificatepath() throws CertificateException, IOException {
		String configPath = "src/test/resources/manager.conf.invalidConf2";
		ManagerTestHelper helper = new ManagerTestHelper();
		Certificate cert = CertificateHandlerHelper.getCertificate(helper
				.getProperties(configPath));
		Assert.assertEquals(null, cert);
		Certificate result = CertificateHandlerHelper
				.parseCertificate(CertificateHandlerHelper
						.getBase64Certificate(helper.getProperties(configPath)));
		Assert.assertEquals(null, result);
	}
	
	@Test
	public void testaNotACertificateFile() throws CertificateException, IOException {
		String configPath = "src/test/resources/manager.conf.invalidConf3";
		ManagerTestHelper helper = new ManagerTestHelper();
		Certificate cert = CertificateHandlerHelper.getCertificate(helper
				.getProperties(configPath));
		Assert.assertEquals(null, cert);
		Certificate result = CertificateHandlerHelper
				.parseCertificate(CertificateHandlerHelper
						.getBase64Certificate(helper.getProperties(configPath)));
		Assert.assertEquals(null, result);
	}

}
