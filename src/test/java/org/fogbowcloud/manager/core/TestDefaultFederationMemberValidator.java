package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDefaultFederationMemberValidator {
	ManagerTestHelper helper;
	DefaultFederationMemberValidator validator;
	X509Certificate mockCA;
	X509Certificate mockCertificate;
	FederationMember member;
	PublicKey publicKey;

	@Before
	public void setUp() throws NoSuchAlgorithmException {
		KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
		KeyPair keys = keyGenerator.generateKeyPair();
		publicKey = keys.getPublic();
		helper = new ManagerTestHelper();
		validator = new DefaultFederationMemberValidator();
		X509Certificate mockCA = Mockito.mock(X509Certificate.class);
		Mockito.doReturn(publicKey).when(mockCA).getPublicKey();
		List<X509Certificate> list = new LinkedList<X509Certificate>();
		list.add(mockCA);
		validator.setValidCAs(list);
		mockCertificate = Mockito.mock(X509Certificate.class);
		member = new FederationMember(new ResourcesInfo("cpuIdle", "cpuInUse",
				"memIdle", "memInUse", new LinkedList<Flavor>(),
				mockCertificate));

	}

	@Test
	public void testValidMember() throws CertificateException, IOException,
			InvalidKeyException, NoSuchAlgorithmException,
			NoSuchProviderException, SignatureException {

		Mockito.doNothing().when(mockCertificate).checkValidity();
		Mockito.doNothing().when(mockCertificate).verify(publicKey);
		Assert.assertTrue(validator.canDonateTo(member));
	}

	@Test
	public void testInvalidMember() throws CertificateException, IOException,
			InvalidKeyException, NoSuchAlgorithmException,
			NoSuchProviderException, SignatureException {

		Mockito.doThrow(new SignatureException()).when(mockCertificate)
				.verify(publicKey);
		Mockito.doNothing().when(mockCertificate).checkValidity();
		Assert.assertFalse(validator.canDonateTo(member));
	}

	@Test
	public void testExpiredMember() throws CertificateException, IOException,
			InvalidKeyException, NoSuchAlgorithmException,
			NoSuchProviderException, SignatureException {
		// this method is invalid to get the exception here-- how to do that?
		// use a real expired certificate?
		
		//gambiarra
		Mockito.doThrow(new IllegalArgumentException())
		 .when(mockCertificate).checkValidity();
		
		 Mockito.doNothing().when(mockCertificate).verify(publicKey);
		Assert.assertFalse(validator.canDonateTo(member));
	}
}
