package org.fogbowcloud.manager.core.plugins.identity.ec2;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult;

public class TestEC2IdentityPlugin {

	@Test
	public void testReIssueToken() {
		EC2IdentityPlugin ec2IdentityPlugin = new EC2IdentityPlugin(new Properties());
		Token token = new Token("AccessId:SecretKey", "AccessId", 
				new Date(), new HashMap<String, String>());
		Assert.assertSame(token, ec2IdentityPlugin.reIssueToken(token));
	}
	
	@Test
	public void testGetCredentials() {
		EC2IdentityPlugin ec2IdentityPlugin = new EC2IdentityPlugin(new Properties());
		Credential[] credentials = new Credential[]{
				new Credential(EC2IdentityPlugin.CRED_ACCESS_KEY, true, null), 
				new Credential(EC2IdentityPlugin.CRED_SECRET_KEY, true, null)};
		Assert.assertArrayEquals(credentials, ec2IdentityPlugin.getCredentials());
	}
	
	@Test
	public void testGetAuthenticationURI() {
		EC2IdentityPlugin ec2IdentityPlugin = new EC2IdentityPlugin(new Properties());
		Assert.assertNull(ec2IdentityPlugin.getAuthenticationURI());
	}
	
	@Test
	public void testGetForwardableToken() {
		EC2IdentityPlugin ec2IdentityPlugin = new EC2IdentityPlugin(new Properties());
		Token token = new Token("AccessId:SecretKey", "AccessId", 
				new Date(), new HashMap<String, String>());
		Assert.assertNull(ec2IdentityPlugin.getForwardableToken(token));
	}
	
	@Test
	public void testCreateFederationUserToken() {
		Properties properties = new Properties();
		EC2IdentityPlugin spy = Mockito.spy(new EC2IdentityPlugin(properties));
		Mockito.doReturn(null).when(spy).getToken(Mockito.anyString());
		Map<String, String> userCredentails = new HashMap<String, String>();
		userCredentails.put(EC2IdentityPlugin.CRED_ACCESS_KEY, "AccessId");
		userCredentails.put(EC2IdentityPlugin.CRED_SECRET_KEY, "SecretKey");
		spy.createToken(userCredentails);
		Mockito.verify(spy).getToken("AccessId:SecretKey");
	}
	
	@Test
	public void testCreateToken() {
		EC2IdentityPlugin spy = Mockito.spy(new EC2IdentityPlugin(new Properties()));
		Mockito.doReturn(null).when(spy).getToken(Mockito.anyString());
		
		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(EC2IdentityPlugin.CRED_ACCESS_KEY, "AccessId");
		credentials.put(EC2IdentityPlugin.CRED_SECRET_KEY, "SecretKey");
		spy.createToken(credentials);
		
		Mockito.verify(spy).getToken("AccessId:SecretKey");
	}
	
	@Test
	public void testIsValidReturningTrue() {
		EC2IdentityPlugin spy = Mockito.spy(new EC2IdentityPlugin(new Properties()));
		Mockito.doReturn(null).when(spy).getToken(Mockito.anyString());
		Assert.assertTrue(spy.isValid("AccessId:SecretKey"));
	}
	
	@Test
	public void testIsValidReturningFalse() {
		EC2IdentityPlugin spy = Mockito.spy(new EC2IdentityPlugin(new Properties()));
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, 
				ResponseConstants.UNAUTHORIZED)).when(spy).getToken(Mockito.anyString());
		Assert.assertFalse(spy.isValid("AccessId:SecretKey"));
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenFailsWhenCreatingEC2Client() {
		EC2IdentityPlugin spy = Mockito.spy(new EC2IdentityPlugin(new Properties()));
		Mockito.doThrow(new AmazonServiceException(null)).when(spy).createEC2Client(
				Mockito.anyString(), Mockito.anyString());
		spy.getToken("AccessId:SecretKey");
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenFailsWhenDescribingAccount() {
		EC2IdentityPlugin spy = Mockito.spy(new EC2IdentityPlugin(new Properties()));
		AmazonEC2Client ec2Client = Mockito.mock(AmazonEC2Client.class);
		
		Mockito.doReturn(ec2Client).when(spy).createEC2Client(
				Mockito.anyString(), Mockito.anyString());
		Mockito.doThrow(new AmazonServiceException(null)).when(
				ec2Client).describeAccountAttributes();
		
		spy.getToken("AccessId:SecretKey");
	}
	
	@Test
	public void testGetTokenWithSuccess() {
		EC2IdentityPlugin spy = Mockito.spy(new EC2IdentityPlugin(new Properties()));
		AmazonEC2Client ec2Client = Mockito.mock(AmazonEC2Client.class);
		
		Mockito.doReturn(ec2Client).when(spy).createEC2Client(
				Mockito.anyString(), Mockito.anyString());
		Mockito.doReturn(new DescribeAccountAttributesResult()).when(
				ec2Client).describeAccountAttributes();
		
		Token token = spy.getToken("AccessId:SecretKey");
		
		Assert.assertNotNull(token);
		Assert.assertEquals("AccessId:SecretKey", token.getAccessId());
		Assert.assertEquals("AccessId", token.getUser());
		Assert.assertEquals("AccessId", token.get(EC2IdentityPlugin.CRED_ACCESS_KEY));
		Assert.assertEquals("SecretKey", token.get(EC2IdentityPlugin.CRED_SECRET_KEY));
	}
	
}
