package org.fogbowcloud.manager.core.plugins.identity.naf;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestNAFIdentityPlugin {

	private static final String DEFAULT_FILE_PUBLIC_KEY_PATH = "src/test/resources/public-key";
	private NAFIdentityPlugin nafIdentityPlugin;
	private String DEFAULT_USER = "fulano";
	private KeyPair keyPair;
	
	@Before
	public void setUp() throws Exception {
		try {
			this.keyPair = RSAUtils.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			Assert.fail();
		}
				
		writePublicKeyToFile(RSAUtils.savePublicKey(this.keyPair.getPublic()));		
		Properties properties = new Properties();
		properties.put(NAFIdentityPlugin.NAF_PUBLIC_KEY, DEFAULT_FILE_PUBLIC_KEY_PATH);
		this.nafIdentityPlugin = new NAFIdentityPlugin(properties);
	}
	
	@After
	public void tearDown() throws IOException{
		File dbFile = new File(DEFAULT_FILE_PUBLIC_KEY_PATH);
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}	
	
	@Test
	public void testIsValid() throws Exception {
		Assert.assertTrue(this.nafIdentityPlugin.isValid(createAccessId()));
	}
	
	@Test
	public void testIsInvalid() throws Exception {
		Assert.assertFalse(this.nafIdentityPlugin.isValid(""));
	}	
	
	@Test
	public void testIsInvalidWithoutublicKey() throws Exception {
		this.nafIdentityPlugin = new NAFIdentityPlugin(new Properties());
		Assert.assertFalse(this.nafIdentityPlugin.isValid(""));
	}	
	
	@Test
	public void testGetToken() throws Exception {
		String accessId = createAccessId();
		Token token = this.nafIdentityPlugin.getToken(accessId);
		Assert.assertNotNull(token);
		Assert.assertEquals(DEFAULT_USER, token.getUser());
		Assert.assertEquals(accessId, token.getAccessId());
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenInvalidToken() throws Exception {
		this.nafIdentityPlugin.getToken("");
	}	
	
	@Test
	public void testGetCredentials() {
		Credential[] credentials = this.nafIdentityPlugin.getCredentials();
		Assert.assertEquals(1, credentials.length);
	}
	
	@Test
	public void testGetForwardableToken() {
		Token originalToken = new Token("accessId", "user", new Date(), new HashMap<String, String>());		
		Assert.assertEquals(originalToken, this.nafIdentityPlugin.getForwardableToken(originalToken));
	}
	
	@Test
	public void testCreateToken() {
		Assert.assertNull(this.nafIdentityPlugin.createToken(new HashMap<String, String>()));
	}
	
	@Test
	public void testGetAuthenticationURI() {
		Assert.assertNull(this.nafIdentityPlugin.getAuthenticationURI());
	}
	
	@Test
	public void testReIssueToken() {
		Token originalToken = new Token("accessId", "user", new Date(), new HashMap<String, String>());		
		Assert.assertEquals(originalToken, this.nafIdentityPlugin.reIssueToken(originalToken));		
	}
	
	private String createAccessId() throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(NAFIdentityPlugin.NAME, DEFAULT_USER);
		String message = jsonObject.toString();
		
		String signature = RSAUtils.sign(keyPair.getPrivate(), message);
		return message + NAFIdentityPlugin.STRING_SEPARATOR + signature;
	}
	
	public void writePublicKeyToFile(String content) throws IOException {
		File file = new File(DEFAULT_FILE_PUBLIC_KEY_PATH);

		if (!file.exists()) {
			file.createNewFile();
		}

		FileWriter fw = new FileWriter(file.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(content);
		bw.close();
	}	
	
}
