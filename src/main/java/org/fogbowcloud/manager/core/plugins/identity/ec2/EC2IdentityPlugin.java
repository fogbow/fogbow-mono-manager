package org.fogbowcloud.manager.core.plugins.identity.ec2;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.GetAccountSummaryResult;

public class EC2IdentityPlugin implements IdentityPlugin {

	private final static Logger LOGGER = Logger.getLogger(EC2IdentityPlugin.class);
	
	@Override
	public Token createToken(Map<String, String> userCredentials) {
		return null;
	}

	@Override
	public Token reIssueToken(Token token) {
		return null;
	}

	@Override
	public Token getToken(String accessId) {
		String[] accessIdSplit = accessId.split(":");
		String accessKey = accessIdSplit[0];
		String secretKey = accessIdSplit[1];
		
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
		AmazonIdentityManagementClient idClient = new AmazonIdentityManagementClient(awsCreds);
		
		GetAccountSummaryResult accountAttributes = null;
		try {
			accountAttributes = idClient.getAccountSummary();
		} catch (Exception e) {
			LOGGER.error("Couldn't load account summary from IAM.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		return new Token(accessId, null, null, new HashMap<String, String>());
	}

	@Override
	public boolean isValid(String accessId) {
		return false;
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
		return null;
	}

}
