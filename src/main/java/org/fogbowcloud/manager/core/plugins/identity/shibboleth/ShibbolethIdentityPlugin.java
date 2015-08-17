package org.fogbowcloud.manager.core.plugins.identity.shibboleth;

import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallingException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

public class ShibbolethIdentityPlugin implements IdentityPlugin {

	private static final Logger LOGGER = Logger.getLogger(ShibbolethIdentityPlugin.class);
	
	private static final String IDENTIFIER_ATTRIBUTE = "eduPersonPrincipalName";
	private static final String DEFAULT_ASSERTION_URL = "http://localhost/Shibboleth.sso/GetAssertion";
	
	
	public static final String CRED_ASSERTION_KEY = "assertionKey";
	public static final String CRED_ASSERTION_ID = "assertionId";

	private static final long DEFAULT_EXPIRATION_INTERVAL = 60 * 60 * 1000; // One hour
	
	private String getAssertionURL;
	
	public ShibbolethIdentityPlugin(Properties props) {
		try {
			DefaultBootstrap.bootstrap();
		} catch (ConfigurationException e) {
			LOGGER.warn("Couldn't bootstrap OpenSAML", e);
		}
		this.getAssertionURL = props.getProperty("identity_shibboleth_get_assertion_url");
	}
	
	@Override
	public Token createToken(Map<String, String> userCredentials) {
		return getToken(userCredentials.get(CRED_ASSERTION_KEY) + 
				":" + userCredentials.get(CRED_ASSERTION_ID));
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		String[] accessIdSplit = accessId.split(":");
		String assertionKey = accessIdSplit[0];
		String assertionId = accessIdSplit[1];
		
		URIBuilder uriBuilder = null;
		try {
			uriBuilder = new URIBuilder(getAssertionURL == null ? 
					DEFAULT_ASSERTION_URL : getAssertionURL);
		} catch (URISyntaxException e) {
			LOGGER.warn("Couldn't create URIBuilder from assertion URL.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		uriBuilder.addParameter("key", assertionKey);
		uriBuilder.addParameter("ID", assertionId);
		
		HttpClientWrapper httpClientWrapper = new HttpClientWrapper();
		HttpResponseWrapper responseWrapper = null;
		try {
			responseWrapper = httpClientWrapper.doGet(uriBuilder.build().toString());
		} catch (URISyntaxException e) {
			LOGGER.warn("Couldn't build URL from assertion builder.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		Document document = null;
		try {
			document = createDocumentBuilder().parse(new InputSource(
					new StringReader(responseWrapper.getContent())));
		} catch (Exception e) {
			LOGGER.warn("Couldn't parse document from serialized SAML response.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
        Element assertionEl = document.getDocumentElement();
        
        Unmarshaller unmarshaller = Configuration.getUnmarshallerFactory().getUnmarshaller(assertionEl);
        Assertion assertion = null;
        try {
        	assertion = (Assertion) unmarshaller.unmarshall(assertionEl);
		} catch (UnmarshallingException e) {
			LOGGER.warn("Couldn't unmarshall SAML assertion from XML document.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
        Map<String, String> tokenAttrs = new HashMap<String, String>();
        
        List<AttributeStatement> attributeStatements = assertion.getAttributeStatements();
        for (AttributeStatement attributeStatement : attributeStatements) {
        	List<Attribute> attributes = attributeStatement.getAttributes();
        	for (Attribute attribute : attributes) {
				List<XMLObject> attributeValues = attribute.getAttributeValues();
				if (attributeValues.isEmpty()) {
					continue;
				}
				XMLObject attributeValue = attributeValues.iterator().next();
				String value = attributeValue.getDOM().getTextContent();
				tokenAttrs.put(attribute.getFriendlyName(), value);
			}
		}
		
		return new Token(accessId, tokenAttrs.get(IDENTIFIER_ATTRIBUTE), 
				new Date(new Date().getTime() + DEFAULT_EXPIRATION_INTERVAL), 
				tokenAttrs);
	}

	private DocumentBuilder createDocumentBuilder() {
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = null;
		try {
			docBuilder = documentBuilderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			LOGGER.warn("Couldn't create document builder.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		return docBuilder;
	}
	
	@Override
	public boolean isValid(String accessId) {
		try {
			getToken(accessId);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public Token createFederationUserToken() {
		return null;
	}

	@Override
	public Credential[] getCredentials() {
		return null;
	}

	@Override
	public String getAuthenticationURI() {
		return null;
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		return originalToken;
	}

}
