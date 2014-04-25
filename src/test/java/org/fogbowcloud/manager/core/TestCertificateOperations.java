package org.fogbowcloud.manager.core;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;
import org.junit.Test;

public class TestCertificateOperations {

	private static final String CERTIFICATE_KEY = "certificate";
	private final String DEFAULT_PATH_TEST_VALUE_EMPTY = "src/test/resources/manager.conf.test";

	@Test
	public void testGetCertificate() throws IOException, CertificateException {
		Certificate cert = CertificateHandlerHelper.getCertificate();
		Certificate result = CertificateHandlerHelper
				.ConvertToCertificateFormat(CertificateHandlerHelper
						.ConvertToSendingFormat());
		Assert.assertEquals(cert, result);
	}
}
