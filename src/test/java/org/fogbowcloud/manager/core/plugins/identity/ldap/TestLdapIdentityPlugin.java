package org.fogbowcloud.manager.core.plugins.identity.ldap;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.fogbowcloud.manager.core.plugins.identity.openstack.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.restlet.resource.ResourceException;

public class TestLdapIdentityPlugin {

	private static final String IDENTITY_URL_KEY = "identity_url";
	private final String KEYSTONE_URL = "http://localhost:" + PluginHelper.PORT_ENDPOINT;
	
	private final String MOCK_SIGNATURE = "mock_signature";
	
	private LdapIdentityPlugin ldapStoneIdentity;

	@Before
	public void setUp() throws Exception {
		
		Properties properties = new Properties();
		properties.put(IDENTITY_URL_KEY, KEYSTONE_URL);

		this.ldapStoneIdentity = Mockito.spy(new LdapIdentityPlugin(properties));
		doReturn(MOCK_SIGNATURE).when(ldapStoneIdentity).createSignature(Mockito.any(JSONObject.class));
		

	}

	@After
	public void tearDown() throws Exception {
		
	}

	@Test
	public void testCreateToken() throws Exception {
		
		String name = "ldapUser";
		String password = "ldapUserPass";
		String userName = "User Full Name";
		
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(LdapIdentityPlugin.CRED_USERNAME, name);
		userCredentials.put(LdapIdentityPlugin.CRED_PASSWORD, password);
		userCredentials.put(LdapIdentityPlugin.CRED_AUTH_URL, "ldapUrl");
		userCredentials.put(LdapIdentityPlugin.CRED_LDAP_BASE, "ldapBase");
		userCredentials.put(LdapIdentityPlugin.CRED_LDAP_ENCRYPT, "");
		userCredentials.put(LdapIdentityPlugin.CRED_PRIVATE_KEY, "private_key_path");
		userCredentials.put(LdapIdentityPlugin.CRED_PUBLIC_KEY, "public_key_path");
		
		doReturn(userName).when(ldapStoneIdentity).ldapAuthenticate(Mockito.eq(name), Mockito.eq(password));
		
		Token token = ldapStoneIdentity.createToken(userCredentials);
		
		String decodedAccessId = decodeAccessId(token.getAccessId());
		
		assertTrue(decodedAccessId.contains(name));
		assertTrue(decodedAccessId.contains(userName));
		assertTrue(decodedAccessId.contains(MOCK_SIGNATURE));
		
	}
	
	@Test
	public void testGetToken() throws Exception {
		
		String login = "ldapUser";
		String password = "ldapUserPass";
		String userName = "User Full Name";
		
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(LdapIdentityPlugin.CRED_USERNAME, login);
		userCredentials.put(LdapIdentityPlugin.CRED_PASSWORD, password);
		userCredentials.put(LdapIdentityPlugin.CRED_AUTH_URL, "ldapUrl");
		userCredentials.put(LdapIdentityPlugin.CRED_LDAP_BASE, "ldapBase");
		userCredentials.put(LdapIdentityPlugin.CRED_LDAP_ENCRYPT, "");
		userCredentials.put(LdapIdentityPlugin.CRED_PRIVATE_KEY, "private_key_path");
		userCredentials.put(LdapIdentityPlugin.CRED_PUBLIC_KEY, "public_key_path");
		
		doReturn(userName).when(ldapStoneIdentity).ldapAuthenticate(Mockito.eq(login), Mockito.eq(password));
		
		Token tokenA = ldapStoneIdentity.createToken(userCredentials);
		
		String decodedAccessId = decodeAccessId(tokenA.getAccessId());
		
		String split[] = decodedAccessId.split(ldapStoneIdentity.ACCESSID_SEPARATOR);
		String tokenMessage = split[0];
		String signature = split[1];
		
		doReturn(true).when(ldapStoneIdentity).verifySign(Mockito.eq(tokenMessage), Mockito.eq(signature));
		Token tokenB = ldapStoneIdentity.getToken(tokenA.getAccessId());
		
		assertEquals(tokenA.getAccessId(), tokenB.getAccessId());
		assertEquals(login, tokenA.getUser().getId());
		assertEquals(userName, tokenA.getUser().getName());
		assertEquals(login, tokenB.getUser().getId());
		assertEquals(userName, tokenB.getUser().getName());
		assertEquals(tokenA.getExpirationDate(), tokenB.getExpirationDate());
		
	}

	@Test
	public void testCreateTokenFail() throws Exception {
		
		String name = "ldapUser";
		String password = "ldapUserPass";
		String userName = "User Full Name";
		
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(LdapIdentityPlugin.CRED_USERNAME, name);
		userCredentials.put(LdapIdentityPlugin.CRED_PASSWORD, password);
		userCredentials.put(LdapIdentityPlugin.CRED_AUTH_URL, "ldapUrl");
		userCredentials.put(LdapIdentityPlugin.CRED_LDAP_BASE, "ldapBase");
		userCredentials.put(LdapIdentityPlugin.CRED_LDAP_ENCRYPT, "");
		userCredentials.put(LdapIdentityPlugin.CRED_PRIVATE_KEY, "private_key_path");
		userCredentials.put(LdapIdentityPlugin.CRED_PUBLIC_KEY, "public_key_path");
		
		doThrow(new Exception("Invalid User")).when(ldapStoneIdentity).ldapAuthenticate(Mockito.eq(name), Mockito.eq(password));
		try {
			
			ldapStoneIdentity.createToken(userCredentials);
			fail();
		} catch (Exception e) {
			assertEquals("Unauthorized", e.getMessage());
		}
		
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenInvalidAccessId() throws Exception {
		
		String name = "ldapUser";
		String password = "ldapUserPass";
		String userName = "User Full Name";
		
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(LdapIdentityPlugin.CRED_USERNAME, name);
		userCredentials.put(LdapIdentityPlugin.CRED_PASSWORD, password);
		userCredentials.put(LdapIdentityPlugin.CRED_AUTH_URL, "ldapUrl");
		userCredentials.put(LdapIdentityPlugin.CRED_LDAP_BASE, "ldapBase");
		userCredentials.put(LdapIdentityPlugin.CRED_LDAP_ENCRYPT, "");
		userCredentials.put(LdapIdentityPlugin.CRED_PRIVATE_KEY, "private_key_path");
		userCredentials.put(LdapIdentityPlugin.CRED_PUBLIC_KEY, "public_key_path");
		
		doReturn(userName).when(ldapStoneIdentity).ldapAuthenticate(Mockito.eq(name), Mockito.eq(password));
		
		Token tokenA = ldapStoneIdentity.createToken(userCredentials);
		
		String decodedAccessId = decodeAccessId(tokenA.getAccessId());
		
		String split[] = decodedAccessId.split(ldapStoneIdentity.ACCESSID_SEPARATOR);
		String tokenMessage = split[0];
		String signature = split[1];
		
		String newAccessId = "{name:\"nome\", expirationDate:\"123421\"}"+ldapStoneIdentity.ACCESSID_SEPARATOR+signature;
		
		newAccessId = new String(Base64.encodeBase64(newAccessId.getBytes(Charsets.UTF_8), false, false),
				Charsets.UTF_8);
		
		doReturn(true).when(ldapStoneIdentity).verifySign(Mockito.eq(tokenMessage), Mockito.eq(signature));
		doReturn(false).when(ldapStoneIdentity).verifySign(Mockito.eq(newAccessId), Mockito.eq(signature));
		
		ldapStoneIdentity.getToken(newAccessId);
		
		
	}
	
	public String decodeAccessId(String accessId){
		return new String(Base64.decodeBase64(accessId),
				Charsets.UTF_8);
	}
	
}
