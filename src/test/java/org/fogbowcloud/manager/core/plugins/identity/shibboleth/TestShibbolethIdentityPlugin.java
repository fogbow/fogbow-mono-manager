package org.fogbowcloud.manager.core.plugins.identity.shibboleth;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestShibbolethIdentityPlugin {

	private static final BasicStatusLine STATUS_LINE_OK = new BasicStatusLine(
			new ProtocolVersion("HTTP", 1, 1), 200, "OK");

	@Test
	public void testReissueToken() {
		Properties props = new Properties();
		Token t = new Token("_assertionId:_assertionKey", "foo", 
				new Date(), new HashMap<String, String>());
		Token reIssuedToken = new ShibbolethIdentityPlugin(props).reIssueToken(t);
		Assert.assertSame(t, reIssuedToken);
	}
	
	
	@Test
	public void testGetCredentials() {
		Credential[] expected = new Credential[]{
				new Credential(ShibbolethIdentityPlugin.CRED_ASSERTION_ID, true, null), 
				new Credential(ShibbolethIdentityPlugin.CRED_ASSERTION_KEY, true, null)};
		Credential[] credentials = new ShibbolethIdentityPlugin(
				new Properties()).getCredentials();
		Assert.assertArrayEquals(expected, credentials);
	}
	
	@Test
	public void testGetAuthenticationURI() {
		Assert.assertNull(new ShibbolethIdentityPlugin(
				new Properties()).getAuthenticationURI());
	}
	
	@Test
	public void testGetForwardableToken() {
		Token t = new Token("_assertionId:_assertionKey", "foo", 
				new Date(), new HashMap<String, String>());
		Assert.assertNull(new ShibbolethIdentityPlugin(
				new Properties()).getForwardableToken(t));
	}
	
	@Test
	public void testCreateToken() {
		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(ShibbolethIdentityPlugin.CRED_ASSERTION_ID, "_assertionId");
		credentials.put(ShibbolethIdentityPlugin.CRED_ASSERTION_KEY, "_assertionKey");
		
		ShibbolethIdentityPlugin sip = Mockito.spy(new ShibbolethIdentityPlugin(new Properties()));
		
		Mockito.doReturn(null).when(sip).getToken(Mockito.anyString());
		sip.createToken(credentials);
		Mockito.verify(sip).getToken(Mockito.eq("_assertionKey:_assertionId"));
	}
	
	@Test
	public void testIsValidReturningTrue() {
		ShibbolethIdentityPlugin sip = Mockito.spy(
				new ShibbolethIdentityPlugin(new Properties()));
		
		Token t = new Token("_assertionId:_assertionKey", "foo", 
				new Date(), new HashMap<String, String>());
		
		Mockito.doReturn(t).when(sip).getToken(Mockito.anyString());
		Assert.assertTrue(sip.isValid("_assertionId:_assertionKey"));
	}
	
	@Test
	public void testIsValidReturningFalse() {
		ShibbolethIdentityPlugin sip = Mockito.spy(
				new ShibbolethIdentityPlugin(new Properties()));
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, 
				ResponseConstants.UNAUTHORIZED)).when(sip).getToken(Mockito.anyString());
		Assert.assertFalse(sip.isValid("_assertionId:_assertionKey"));
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenWrongAssertionURL() {
		Properties props = new Properties();
		props.setProperty("identity_shibboleth_get_assertion_url", "weirdUrl:||what?");
		new ShibbolethIdentityPlugin(props).getToken("_assertionKey:_assertionId");
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenHTTPRequestFailing() {
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		Mockito.doThrow(new OCCIException(ErrorType.UNAUTHORIZED, 
				ResponseConstants.UNAUTHORIZED)).when(httpClient).doGet(Mockito.anyString());
		ShibbolethIdentityPlugin sip = new ShibbolethIdentityPlugin(new Properties(), httpClient);
		sip.getToken("_assertionKey:_assertionId");
	}
	
	private static final String CAFE_BAD_FORMAT_XML = "src/test/resources/shibboleth/cafe-bad-format.xml";
	
	@Test(expected=OCCIException.class)
	public void testGetTokenHTTPXMLParsingFailing() throws IOException {
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String responseContent = IOUtils.toString(new FileInputStream(CAFE_BAD_FORMAT_XML));
		Mockito.doReturn(new HttpResponseWrapper(STATUS_LINE_OK, 
				responseContent)).when(httpClient).doGet(Mockito.anyString());
		ShibbolethIdentityPlugin sip = new ShibbolethIdentityPlugin(new Properties(), httpClient);
		sip.getToken("_assertionKey:_assertionId");
	}
	
	private static final String CAFE_BAD_ASSERTION_FORMAT_XML = "src/test/resources/shibboleth/cafe-wrong-assertion.xml";
	
	@Test(expected=OCCIException.class)
	public void testGetTokenHTTPAssertionParsingFailing() throws IOException {
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String responseContent = IOUtils.toString(new FileInputStream(CAFE_BAD_ASSERTION_FORMAT_XML));
		Mockito.doReturn(new HttpResponseWrapper(STATUS_LINE_OK, 
				responseContent)).when(httpClient).doGet(Mockito.anyString());
		ShibbolethIdentityPlugin sip = new ShibbolethIdentityPlugin(new Properties(), httpClient);
		sip.getToken("_assertionKey:_assertionId");
	}
	
	private static final String CAFE_XML_OK = "src/test/resources/shibboleth/cafe.xml";
	
	public void testGetTokenOK() throws IOException {
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String responseContent = IOUtils.toString(new FileInputStream(CAFE_XML_OK));
		Mockito.doReturn(new HttpResponseWrapper(STATUS_LINE_OK, 
				responseContent)).when(httpClient).doGet(Mockito.anyString());
		ShibbolethIdentityPlugin sip = new ShibbolethIdentityPlugin(new Properties(), httpClient);
		Token token = sip.getToken("_assertionKey:_assertionId");
		
		Assert.assertEquals("80ca06abfa1a5104af9a770f485dad07@idp1.cafeexpresso.rnp.br", token.getUser());
		Assert.assertEquals("_assertionKey:_assertionId", token.getAccessId());
		Assert.assertEquals("ford", token.get("uid"));
		Assert.assertEquals("12345678900", token.get("brPersonCPF"));
		Assert.assertEquals("Prefect", token.get("sn"));
	}
}
