package org.fogbowcloud.manager.core.plugins.identity.naf;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicStatusLine;
import org.apache.xml.security.utils.Base64;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public class TestNAFIdentityPlugin {
	
	private static final String DEFAULT_FILE_PUBLIC_KEY_PATH = "src/test/resources/public-key";
	private NAFIdentityPlugin nafIdentityPlugin;
	private KeyPair keyPair;
	
	private final String DEFAULT_USER = "fulano";
	private Map<String, String> defaultSamlAttributes;
	private static final String ATTRIBUTE_ONE_SAML_ATTRIBUTE = "attributeOne";
	private long defaultCTime;
	private Properties properties;
	
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
		this.properties = new Properties();
		this.properties.put(NAFIdentityPlugin.NAF_PUBLIC_KEY, DEFAULT_FILE_PUBLIC_KEY_PATH);
		this.nafIdentityPlugin = Mockito.spy(new NAFIdentityPlugin(this.properties));
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
	public void testIsValidWithTokenGenerator() throws Exception {
		this.properties.put(NAFIdentityPlugin.ENDPOINT_TOKEN_GENERATOR, "https://localhost:0000");
		this.properties.put(NAFIdentityPlugin.NAME_USER_TOKEN_GENERATOR, "name");
		this.properties.put(NAFIdentityPlugin.PASSWORD_USER_TOKEN_GENERATOR, "password");
		
		HttpClient client = Mockito.mock(HttpClient.class);
		HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		String content = NAFIdentityPlugin.VALID_RESPONSE_TOKEN_GENERATOR;
		InputStream contentInputStream = new ByteArrayInputStream(content.getBytes(
				StandardCharsets.UTF_8));;
		Mockito.when(httpEntity.getContent()).thenReturn(contentInputStream);
		Mockito.when(httpResponse.getEntity()).thenReturn(httpEntity);
		BasicStatusLine basicStatus = new BasicStatusLine(new ProtocolVersion("", 0, 0),
				HttpStatus.SC_OK, "");
		Mockito.when(httpResponse.getStatusLine()).thenReturn(basicStatus);
		Mockito.when(client.execute((Mockito.any(HttpUriRequest.class)))).thenReturn(
				httpResponse);
		
		this.nafIdentityPlugin.setClient(client);
		Assert.assertTrue(this.nafIdentityPlugin.isValid(createAccessIdTokenGenerator()));
	}
	
	@Test
	public void testIsInvalidWithTokenGenerator() throws Exception {
		String url = "https://localhost:0000";
		this.properties.put(NAFIdentityPlugin.ENDPOINT_TOKEN_GENERATOR, url);
		String name = "name";
		this.properties.put(NAFIdentityPlugin.NAME_USER_TOKEN_GENERATOR, name);
		String password = "password";
		this.properties.put(NAFIdentityPlugin.PASSWORD_USER_TOKEN_GENERATOR, password);
		
		HttpClient client = Mockito.mock(HttpClient.class);
		HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		String content = "Invalid";
		InputStream contentInputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));;
		Mockito.when(httpEntity.getContent()).thenReturn(contentInputStream);
		Mockito.when(httpResponse.getEntity()).thenReturn(httpEntity);
		BasicStatusLine basicStatus = new BasicStatusLine(new ProtocolVersion("", 0, 0), HttpStatus.SC_OK, "");
		Mockito.when(httpResponse.getStatusLine()).thenReturn(basicStatus);
		Mockito.when(client.execute((Mockito.any(HttpUriRequest.class)))).thenReturn(httpResponse);
				
		this.nafIdentityPlugin.setClient(client);
				
		String accessIdTokenGenerator = createAccessIdTokenGenerator();
		Assert.assertFalse(this.nafIdentityPlugin.isValid(accessIdTokenGenerator));	
	}	
	
	@Test
	public void testCheckUrlTokenGenerator() throws Exception {
		String url = "https://localhost:0000";
		this.properties.put(NAFIdentityPlugin.ENDPOINT_TOKEN_GENERATOR, url);
		String name = "name";
		this.properties.put(NAFIdentityPlugin.NAME_USER_TOKEN_GENERATOR, name);
		String password = "password";
		this.properties.put(NAFIdentityPlugin.PASSWORD_USER_TOKEN_GENERATOR, password);
		
		String accessIdTokenGenerator = createAccessIdTokenGenerator();
		HttpUriRequest request = new HttpGet(url + NAFIdentityPlugin.TOKEN_URL_OPERATION 
				+ URLEncoder.encode(accessIdTokenGenerator, "UTF-8") + NAFIdentityPlugin.METHOD_GET_VALIDITY_CHECK);
		request.addHeader(NAFIdentityPlugin.NAME, name);
		request.addHeader(NAFIdentityPlugin.PASSWORD, password);
		HttpUriRequestMatcher expectedRequest = new HttpUriRequestMatcher(request);
		
		this.nafIdentityPlugin.setClient(Mockito.spy(HttpClients.createMinimal()));
		Assert.assertFalse(this.nafIdentityPlugin.isValid(accessIdTokenGenerator));
		Mockito.verify(this.nafIdentityPlugin.getClient(), Mockito.times(1)).
				execute(Mockito.argThat(expectedRequest));
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
	
	@Test
	public void testCheckStatusResponseUnauthorized() {
		HttpResponse response = Mockito.mock(HttpResponse.class);
		int httpStatusCode = HttpStatus.SC_UNAUTHORIZED;
		StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("", 0, 0), 
				httpStatusCode, "");
		Mockito.when(response.getStatusLine()).thenReturn(statusLine);
		try {
			this.nafIdentityPlugin.checkStatusResponse(response, "");			
		} catch (OCCIException e) {
			Assert.assertEquals(httpStatusCode, e.getStatus().getCode());
			return;
		}
		Assert.fail();
	}
	
	@Test
	public void testCheckStatusResponseBadRequest() {
		HttpResponse response = Mockito.mock(HttpResponse.class);
		int httpStatusCode = HttpStatus.SC_BAD_REQUEST;
		StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("", 0, 0), 
				httpStatusCode, "");
		Mockito.when(response.getStatusLine()).thenReturn(statusLine);
		try {
			this.nafIdentityPlugin.checkStatusResponse(response, "");			
		} catch (OCCIException e) {
			Assert.assertEquals(httpStatusCode, e.getStatus().getCode());
			return;
		}
		Assert.fail();
	}	
	
	private String createAccessId() throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(NAFIdentityPlugin.NAME, DEFAULT_USER);
		jsonObject.put(NAFIdentityPlugin.TOKEN_ETIME_JSONOBJECT, this.defaultCTime);		
		jsonObject.put(NAFIdentityPlugin.SAML_ATTRIBUTES_JSONOBJECT, this.defaultSamlAttributes);
		String message = jsonObject.toString();
		
		return createFinalToken(message);
	}
	
	private String createAccessIdTokenGenerator() throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(NAFIdentityPlugin.NAME, DEFAULT_USER);
		jsonObject.put(NAFIdentityPlugin.TOKEN_ETIME_JSONOBJECT, this.defaultCTime);		
		jsonObject.put(NAFIdentityPlugin.TYPE, NAFIdentityPlugin.DEFAULT_TYPE_TOKEN_GENERATOR);
		String message = jsonObject.toString();
		
		return createFinalToken(message);
	}

	private String createFinalToken(String message) throws NoSuchAlgorithmException,
			InvalidKeyException, SignatureException,
			UnsupportedEncodingException {
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
	
	private class HttpUriRequestMatcher extends ArgumentMatcher<HttpUriRequest> {

		private HttpUriRequest request;

		public HttpUriRequestMatcher(HttpUriRequest request) {
			this.request = request;
		}

		public boolean matches(Object object) {

			HttpUriRequest comparedRequest = (HttpUriRequest) object;
			if (!this.request.getURI().equals(comparedRequest.getURI())) {
				return false;
			}
			if (!checkHeaders(comparedRequest.getAllHeaders())) {
				return false;
			}
			if (!this.request.getMethod().equals(comparedRequest.getMethod())) {
				return false;
			}
			return true;
		}

		public boolean checkHeaders(Header[] comparedHeaders) {
			for (Header comparedHeader : comparedHeaders) {
				boolean headerEquals = false;
				for (Header header : this.request.getAllHeaders()) {
					if (header.getName().equals(OCCIHeaders.X_AUTH_TOKEN)) {
						if (header.getName().equals(comparedHeader.getName())) {
							headerEquals = true;
							break;
						}
					} else 
					if (header.getName().equals(comparedHeader.getName())
							&& header.getValue().equals(comparedHeader.getValue())) {
						headerEquals = true;
						continue;
					}
				}
				if (!headerEquals) {
					return false;
				}
			}
			return true;
		}
	}	
	
}
