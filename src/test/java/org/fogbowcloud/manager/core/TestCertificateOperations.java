package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import org.junit.Assert;
import org.junit.Test;

public class TestCertificateOperations {

	@Test
	public void testGetCertificate() throws IOException, CertificateException {
		Certificate cert = CertificateHandlerHelper.getCertificate();
		Certificate result = CertificateHandlerHelper
				.convertToCertificateFormat(CertificateHandlerHelper
						.convertToSendingFormat());
		Assert.assertEquals(cert, result);
	}
}
