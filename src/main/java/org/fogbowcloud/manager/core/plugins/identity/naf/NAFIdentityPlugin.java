package org.fogbowcloud.manager.core.plugins.identity.naf;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONObject;

public class NAFIdentityPlugin implements IdentityPlugin {

	private static final Logger LOGGER = Logger.getLogger(NAFIdentityPlugin.class);
	protected static final String NAF_PUBLIC_KEY = "naf_identity_plublic_key";
	protected static final String STRING_SEPARATOR = "-f-";
	protected static final String NAME = "name";
	
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
		if (!isValid(accessId)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
			
		JSONObject jsonObject;
		String user = null;
		try {
			AccessIdFormat accessIdFormat = new AccessIdFormat(accessId);
			jsonObject = new JSONObject(accessIdFormat.getMessage());
			user = jsonObject.getString(NAME);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		return new Token(accessId, user, new Date(), new HashMap<String, String>());
	}

	@Override
	public boolean isValid(String accessId) {		
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
