package org.fogbowcloud.manager.core.plugins.identity.saml;

import java.io.File;
import java.io.StringReader;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.joda.time.DateTime;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.signature.X509Data;
import org.opensaml.xml.signature.X509SubjectName;
import org.opensaml.xml.validation.ValidationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

public class SAMLIdentityPlugin implements IdentityPlugin {

	private static final Logger LOGGER = Logger.getLogger(SAMLIdentityPlugin.class);
	private static final String DEFAULT_IDENTIFIER_ATTRIBUTE = "eduPersonPrincipalName";
	private static final long DEFAULT_EXPIRATION_INTERVAL = 60 * 60 * 1000; // One hour
	
	private List<java.security.cert.X509Certificate> caCertificates = 
			new LinkedList<java.security.cert.X509Certificate>();
	private String identifierAttribute;
	
	public SAMLIdentityPlugin(Properties properties) {
		try {
			DefaultBootstrap.bootstrap();
		} catch (ConfigurationException e) {
			LOGGER.warn("Couldn't bootstrap OpenSAML", e);
		}
		String certDirectory = properties.getProperty("identity_saml_certificate_directory");
		loadCertificates(certDirectory);
		
		String identifierAttributeProp = properties.getProperty("identity_saml_identifier_attribute");
		this.identifierAttribute = identifierAttributeProp == null ? 
				DEFAULT_IDENTIFIER_ATTRIBUTE : identifierAttributeProp;
	}
	
	private void loadCertificates(String certDirectory) {
		File[] files = new File(certDirectory).listFiles();
		for (File file : files) {
			try {
				Collection<X509Certificate> certificateChain = 
						CertificateUtils.getCertificateChainFromFile(file.getAbsolutePath());
				if (certificateChain.isEmpty()) {
					LOGGER.warn("CA certificate chain in " + file.getAbsolutePath() + " is empty.");
					continue;
				}
				caCertificates.add(certificateChain.iterator().next());
			} catch (Exception e) {
				LOGGER.warn("Couldn't load CA certificate from " + file.getAbsolutePath(), e);
			}
		}
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		return null;
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = null;
		try {
			docBuilder = documentBuilderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			LOGGER.warn("Couldn't create document builder.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

        Document document = null;
		try {
			document = docBuilder.parse(new InputSource(new StringReader(accessId)));
		} catch (Exception e) {
			LOGGER.warn("Couldn't parse document from serialized SAML response.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
        Element element = document.getDocumentElement();
        element = (Element)element.getElementsByTagNameNS("saml", "Assertion").item(0);
        
        UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
        Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
        Assertion assertion = null;
        try {
			assertion = (Assertion) unmarshaller.unmarshall(element);
		} catch (UnmarshallingException e) {
			LOGGER.warn("Couldn't unmarshall SAML assertion from XML document.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
        
        Signature signature = assertion.getSignature();
        List<X509Data> x509Datas = signature.getKeyInfo().getX509Datas();
        if (!x509Datas.isEmpty()) {
        	X509Data x509Data = x509Datas.iterator().next();
			X509SubjectName x509SubjectName = x509Data.getX509SubjectNames().iterator().next();
			verify(signature, x509SubjectName);
        }
        
        String user = null;
        
        List<AttributeStatement> attributeStatements = assertion.getAttributeStatements();
        for (AttributeStatement attributeStatement : attributeStatements) {
        	List<Attribute> attributes = attributeStatement.getAttributes();
        	for (Attribute attribute : attributes) {
				if (attribute.getName().equals(identifierAttribute)) {
					List<XMLObject> attributeValues = attribute.getAttributeValues();
					if (attributeValues.isEmpty()) {
						continue;
					}
					XMLObject attributeValue = attributeValues.iterator().next();
					user = attributeValue.getDOM().getTextContent();
				}
			}
		}
        
        Date expirationTime = new Date(new Date().getTime() + DEFAULT_EXPIRATION_INTERVAL);
        
        List<AuthnStatement> authnStatements = assertion.getAuthnStatements();
        if (!authnStatements.isEmpty()) {
        	AuthnStatement authnStatement = authnStatements.iterator().next();
        	DateTime sessionNotOnOrAfter = authnStatement.getSessionNotOnOrAfter();
        	expirationTime = sessionNotOnOrAfter.toDate();
        }
        
        Token token = new Token(accessId, user, expirationTime, 
        		new HashMap<String, String>());
        
		return token;
	}

	private void verify(Signature signature, X509SubjectName x509SubjectName) {
		
		java.security.cert.X509Certificate pickedCACertificate = null;
		
		for (java.security.cert.X509Certificate caCertificate : caCertificates) {
			if (caCertificate.getSubjectDN().toString().equals(x509SubjectName.getValue())) {
				pickedCACertificate = caCertificate;
			}
		}
		
		if (pickedCACertificate == null) {
			LOGGER.warn("There is no CA certificate for this assertion.");
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		BasicX509Credential validatingCredential = new BasicX509Credential();
		validatingCredential.setEntityCertificate(pickedCACertificate);
		SignatureValidator signatureValidator = new SignatureValidator(validatingCredential);
		try {
			signatureValidator.validate(signature);
		} catch (ValidationException e) {
			LOGGER.warn("Couldn't validate SAML signature.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
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
