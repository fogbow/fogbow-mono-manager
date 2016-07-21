package org.fogbowcloud.manager.core.plugins.identity.naf;

import java.nio.charset.Charset;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONException;
import org.json.JSONObject;

public class NAFIdentityPlugin implements IdentityPlugin {

	protected static final String SAML_ATTRIBUTES_JSONOBJECT = "saml_attributes";
	protected static final String TOKEN_CTIME_JSONOBJECT = "token_ctime";
	protected static final String NAME = "name";
	
	private static final Logger LOGGER = Logger.getLogger(NAFIdentityPlugin.class);
	protected static final String NAF_PUBLIC_KEY = "naf_identity_plublic_key";
	protected static final String STRING_SEPARATOR = "!#!";
	
	private Properties properties;
	
	public NAFIdentityPlugin(Properties properties) {
		this.properties = properties;
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
		if (accessId == null || accessId.isEmpty()) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		if (!isValid(accessId)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}		
			
		JSONObject jsonObject;
		String user = null;
		Date date = null;
		Map<String, String> attributes = null;
		try {
			String accessIdDecoded = new String(Base64.decode(accessId.getBytes()), Charset.forName("UTF-8")); 
			AccessIdFormat accessIdFormat = new AccessIdFormat(accessIdDecoded);
			jsonObject = new JSONObject(accessIdFormat.getMessage());
			user = jsonObject.getString(NAME);
			date = new Date(Long.parseLong(jsonObject.getString(TOKEN_CTIME_JSONOBJECT)));
			attributes = toMap(jsonObject.getJSONObject(SAML_ATTRIBUTES_JSONOBJECT));
		} catch (Exception e) {
			LOGGER.error("Could not create token by accessId.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		return new Token(accessId, user, date, attributes);
	}

	@Override
	public boolean isValid(String accessId) {	
		if (accessId == null || accessId.isEmpty()) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		accessId = new String(Base64.decode(accessId.getBytes()), Charset.forName("UTF-8")); 		
		RSAPublicKey publicKey = null;
		try {
			publicKey = RSAUtils.getPublicKey(properties.getProperty(NAF_PUBLIC_KEY));
		} catch (Exception e) {
			LOGGER.warn("Could not create RSA public key.", e);
			return false;
		}
		
		try {
			AccessIdFormat accessIdFormat = new AccessIdFormat(accessId);
			RSAUtils.verify(publicKey, accessIdFormat.getMessage(), accessIdFormat.getSignature());
		} catch (Exception e) {			
			LOGGER.warn("Could not verify access id.", e);
			return false;
		}
		return true;
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential(NAME, true, null) };
	}

	@Override
	public String getAuthenticationURI() {
		return null;
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		return originalToken;
	}	
	
	@SuppressWarnings("unchecked")
	protected static Map<String, String> toMap(JSONObject jsonObject) throws JSONException {
		if (jsonObject == null) {
			return null;
		}
	    Map<String, String> map = new HashMap<String, String>();

		Iterator<String> keysItr = jsonObject.keys();
	    while(keysItr.hasNext()) {
	        String key = keysItr.next();
	        String value = jsonObject.getString(key);
	        map.put(key, value);
	    }
	    return map;
	}	
	
	private class AccessIdFormat {
		
		private String signature;
		private String message;
		
		public AccessIdFormat(String accessIdStr) throws Exception {
			int signatureIndex = 1;
			int messageIndex = 0;
			String[] partiesAccessId = accessIdStr.split(STRING_SEPARATOR);
			if (partiesAccessId.length != 2) {
				throw new Exception("Invalid format.");
			}
			this.signature = partiesAccessId[signatureIndex];
			this.message = partiesAccessId[messageIndex];
		}
		
		public String getSignature() {
			return signature;
		}
		
		public String getMessage() {
			return message;
		}
		
	}
}
