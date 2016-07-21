package org.fogbowcloud.manager.core.plugins.identity.naf;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.xml.security.utils.Base64;
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
	private KeyPair keyPair;
	
	private final String DEFAULT_USER = "fulano";
	private Map<String, String> defaultSamlAttributes;
	private static final String ATTRIBUTE_ONE_SAML_ATTRIBUTE = "attributeOne";
	private long defaultCTime;
	
	@Before
	public void setUp() throws Exception {
		try {
			this.keyPair = RSAUtils.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			Assert.fail();
		}
				
		this.defaultCTime = System.currentTimeMillis();
		this.defaultSamlAttributes = new HashMap<String, String>();
		this.defaultSamlAttributes.put(ATTRIBUTE_ONE_SAML_ATTRIBUTE, "valueAttributeOne");
		
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
		Assert.assertFalse(this.nafIdentityPlugin.isValid("wrong_token"));
	}	
	
	@Test
	public void testIsInvalidWithoutublicKey() throws Exception {
		this.nafIdentityPlugin = new NAFIdentityPlugin(new Properties());
		Assert.assertFalse(this.nafIdentityPlugin.isValid("wrong_token"));
	}	
	
	@Test
	public void testGetToken() throws Exception {
		String accessId = createAccessId();
		Token token = this.nafIdentityPlugin.getToken(accessId);
		Assert.assertNotNull(token);
		Assert.assertEquals(DEFAULT_USER, token.getUser());
		Assert.assertEquals(new Date(this.defaultCTime), token.getExpirationDate());
		Assert.assertEquals(this.defaultSamlAttributes.get(ATTRIBUTE_ONE_SAML_ATTRIBUTE), 
				token.getAttributes().get(ATTRIBUTE_ONE_SAML_ATTRIBUTE));
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
		jsonObject.put(NAFIdentityPlugin.TOKEN_CTIME_JSONOBJECT, this.defaultCTime);		
		jsonObject.put(NAFIdentityPlugin.SAML_ATTRIBUTES_JSONOBJECT, this.defaultSamlAttributes);
		String message = jsonObject.toString();
		
		String signature = RSAUtils.sign(keyPair.getPrivate(), message);
		String accessIdStr = message + NAFIdentityPlugin.STRING_SEPARATOR + signature;
		return Base64.encode(accessIdStr.getBytes());
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
