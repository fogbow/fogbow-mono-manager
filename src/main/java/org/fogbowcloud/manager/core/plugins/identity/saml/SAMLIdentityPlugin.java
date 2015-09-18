package org.fogbowcloud.manager.core.plugins.identity.saml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.joda.time.DateTime;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.saml2.core.EncryptedAssertion;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.encryption.Decrypter;
import org.opensaml.saml2.encryption.EncryptedElementTypeEncryptedKeyResolver;
import org.opensaml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml2.metadata.provider.DOMMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.security.MetadataCredentialResolver;
import org.opensaml.security.MetadataCredentialResolverFactory;
import org.opensaml.security.MetadataCriteria;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.encryption.ChainingEncryptedKeyResolver;
import org.opensaml.xml.encryption.DecryptionException;
import org.opensaml.xml.encryption.InlineEncryptedKeyResolver;
import org.opensaml.xml.encryption.SimpleRetrievalMethodEncryptedKeyResolver;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.security.CriteriaSet;
import org.opensaml.xml.security.SecurityException;
import org.opensaml.xml.security.criteria.EntityIDCriteria;
import org.opensaml.xml.security.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xml.security.keyinfo.KeyInfoProvider;
import org.opensaml.xml.security.keyinfo.LocalKeyInfoCredentialResolver;
import org.opensaml.xml.security.keyinfo.StaticKeyInfoCredentialResolver;
import org.opensaml.xml.security.keyinfo.provider.InlineX509DataProvider;
import org.opensaml.xml.security.keyinfo.provider.RSAKeyValueProvider;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.security.x509.X509Credential;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.validation.ValidationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

public class SAMLIdentityPlugin implements IdentityPlugin {

	private static final Logger LOGGER = Logger.getLogger(SAMLIdentityPlugin.class);
	private static final String DEFAULT_IDENTIFIER_ATTRIBUTE = "eduPersonPrincipalName";
	private static final String DEFAULT_METADATA_URL = "https://ds.cafe.rnp.br/metadata/cafe-metadata.xml";
	private static final long DEFAULT_EXPIRATION_INTERVAL = 60 * 60 * 1000; // One hour
	
	private String identifierAttribute;
	private String samlMetadataURL;
	private Decrypter spDecrypter;
	
	public SAMLIdentityPlugin(Properties properties) {
		try {
			DefaultBootstrap.bootstrap();
		} catch (ConfigurationException e) {
			LOGGER.warn("Couldn't bootstrap OpenSAML", e);
		}
		String identifierAttributeProp = properties.getProperty("identity_saml_identifier_attribute");
		this.identifierAttribute = identifierAttributeProp == null ? 
				DEFAULT_IDENTIFIER_ATTRIBUTE : identifierAttributeProp;
		
		String metadataURLProp = properties.getProperty("identity_saml_metadata_url");
		this.samlMetadataURL = metadataURLProp == null ? 
				DEFAULT_METADATA_URL : metadataURLProp;
		try {
			initSPDecrypter(properties);
		} catch (Throwable e) {
			LOGGER.error("Couldn't init SPDecripter", e);
		}
	}
	
	private BasicX509Credential loadSPCredential(Properties properties) {
		KeyStore ks = null;
		String spKeystorePassword = properties.getProperty("identity_saml_sp_keystore_password");
		char[] password = spKeystorePassword.toCharArray();

		try {
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			FileInputStream fis = new FileInputStream(properties.getProperty("identity_saml_sp_keystore_path"));
			ks.load(fis, password);
			fis.close();
		} catch (Exception e) {
			LOGGER.error("Error while initializing SP Keystore", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		KeyStore.PrivateKeyEntry pkEntry = null;
		try {
			pkEntry = (KeyStore.PrivateKeyEntry) ks.getEntry(
					properties.getProperty("identity_saml_sp_keystore_cert_alias"), 
					new KeyStore.PasswordProtection(password));
		} catch (Exception e) {
			LOGGER.error("Failed to get Private Entry from the SP keystore.", e);
		}
		
		PrivateKey pk = pkEntry.getPrivateKey();
		X509Certificate certificate = (X509Certificate) pkEntry
				.getCertificate();
		BasicX509Credential credential = new BasicX509Credential();
		credential.setEntityCertificate(certificate);
		credential.setPrivateKey(pk);
		return credential;
	}
	
	private void initSPDecrypter(Properties properties) {
		StaticKeyInfoCredentialResolver localCredResolver = new StaticKeyInfoCredentialResolver(
				loadSPCredential(properties));

		List<KeyInfoProvider> kiProviders = new ArrayList<KeyInfoProvider>();
		kiProviders.add(new RSAKeyValueProvider());
		kiProviders.add(new InlineX509DataProvider());

		KeyInfoCredentialResolver kekResolver = new LocalKeyInfoCredentialResolver(
				kiProviders, localCredResolver);

		ChainingEncryptedKeyResolver encryptedKeyResolver = new ChainingEncryptedKeyResolver();
		encryptedKeyResolver.getResolverChain().add(
				new InlineEncryptedKeyResolver());
		encryptedKeyResolver.getResolverChain().add(
				new EncryptedElementTypeEncryptedKeyResolver());
		encryptedKeyResolver.getResolverChain().add(
				new SimpleRetrievalMethodEncryptedKeyResolver());

		this.spDecrypter = new Decrypter(null, kekResolver,
				encryptedKeyResolver);
		         
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
        Document document = null;
		try {
			document = createDocumentBuilder().parse(new InputSource(new StringReader(accessId)));
		} catch (Exception e) {
			LOGGER.warn("Couldn't parse document from serialized SAML response.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
        Element responseEl = document.getDocumentElement();
        Unmarshaller responseUnmarshaller = Configuration.getUnmarshallerFactory().getUnmarshaller(responseEl);
        Response response = null;
        try {
        	response = (Response) responseUnmarshaller.unmarshall(responseEl);
		} catch (UnmarshallingException e) {
			LOGGER.warn("Couldn't unmarshall SAML assertion from XML document.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
        
        Element assertionEl = (Element)responseEl.getElementsByTagNameNS(
        		"urn:oasis:names:tc:SAML:2.0:assertion", "EncryptedAssertion").item(0);
        Unmarshaller unmarshaller = Configuration.getUnmarshallerFactory().getUnmarshaller(assertionEl);
        EncryptedAssertion encryptedAssertion = null;
        try {
        	encryptedAssertion = (EncryptedAssertion) unmarshaller.unmarshall(assertionEl);
		} catch (UnmarshallingException e) {
			LOGGER.warn("Couldn't unmarshall SAML assertion from XML document.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		Assertion assertion = null;
		try {
			assertion = spDecrypter.decrypt(encryptedAssertion);
		} catch (DecryptionException e) {
			LOGGER.warn("Couldn't decrypt assertion.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
        try {
        	SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
			profileValidator.validate(assertion.getSignature());
		} catch (ValidationException e) {
			LOGGER.warn("Couldn't validate SAML profile.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
        
        X509Credential idPCredential = getIdPCredential(response);
        
        try {
        	SignatureValidator sigValidator = new SignatureValidator(idPCredential);
			sigValidator.validate(assertion.getSignature());
		} catch (ValidationException e) {
			LOGGER.warn("Couldn't validate signature against issuer credential.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
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

	private X509Credential getIdPCredential(Response response) {
		Element metadataRoot = null;
        try {
        	InputStream metaDataInputStream = new URL(samlMetadataURL).openStream();
        	Document metaDataDocument = createDocumentBuilder().parse(metaDataInputStream);
        	metadataRoot = metaDataDocument.getDocumentElement();
        	metaDataInputStream.close();
		} catch (Exception e) {
			LOGGER.warn("Couldn't parse SAML medatada from " + samlMetadataURL, e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
         
        DOMMetadataProvider idpMetadataProvider = new DOMMetadataProvider(metadataRoot);
        idpMetadataProvider.setRequireValidMetadata(true);
        idpMetadataProvider.setParserPool(new BasicParserPool());
        try {
			idpMetadataProvider.initialize();
		} catch (MetadataProviderException e) {
			LOGGER.warn("Couldn't initialize DOMMetadataProdiver.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
        
        MetadataCredentialResolverFactory credentialResolverFactory = MetadataCredentialResolverFactory.getFactory();
        MetadataCredentialResolver credentialResolver = credentialResolverFactory.getInstance(idpMetadataProvider);
         
        CriteriaSet criteriaSet = new CriteriaSet();
        criteriaSet.add(new MetadataCriteria(IDPSSODescriptor.DEFAULT_ELEMENT_NAME, SAMLConstants.SAML20P_NS));
        criteriaSet.add(new EntityIDCriteria(response.getIssuer().getValue()));
         
        X509Credential idpCredential = null;
		try {
			idpCredential = (X509Credential) credentialResolver.resolveSingle(criteriaSet);
		} catch (SecurityException e) {
			LOGGER.warn("Couldn't resolve credential for " + response.getIssuer().getValue(), e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		return idpCredential;
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
