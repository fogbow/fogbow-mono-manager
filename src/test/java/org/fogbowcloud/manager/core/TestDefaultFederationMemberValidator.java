package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDefaultFederationMemberValidator {
	ManagerTestHelper helper;
	FederationMemberValidator validator;
	
	@Before
	public void setUp() {
		helper = new ManagerTestHelper();
		validator = new DefaultFederationMemberValidator();
		X500Principal ca = new X500Principal(
				"O=Internet Widgits Pty Ltd, L=Campina Gande, ST=Paraiba, C=BR");
		X500Principal ca2 = new X500Principal(
				"O=Internet Widgits Pty Ltd, ST=Some-State, C=BR");
		List<X500Principal> list = new LinkedList<X500Principal>();
		list.add(ca);
		list.add(ca2);
		validator.setValidCAs(list);
	}
	
	@Test
	public void testValidMember() throws CertificateException, IOException {
		
		ManagerTestHelper helper = new ManagerTestHelper();
		Certificate cert = CertificateHandlerHelper.getCertificate(helper
				.getProperties());
		FederationMemberValidator validator = new DefaultFederationMemberValidator();
		//see if is this cert in the helper
		FederationMember member = new FederationMember(helper.getResources());
		Assert.assertTrue(validator.validateDonatorMember(member));
	}
	
	@Test
	public void testExpiredMember() throws CertificateException, IOException {
		
		Certificate cert = CertificateHandlerHelper.getCertificate(helper
				.getProperties(/*add expired certificate*/));
		FederationMemberValidator validator = new DefaultFederationMemberValidator();
	
		FederationMember member = new FederationMember(helper.getResources());
		Assert.assertTrue(validator.validateDonatorMember(member));
	}
	
}
