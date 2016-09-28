package org.fogbowcloud.manager.core.plugins.identity.ec2;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;

public class EC2IdentityPlugin implements IdentityPlugin {

	private static final Logger LOGGER = Logger.getLogger(EC2IdentityPlugin.class);
	private static final long EXPIRATION_INTERVAL = TimeUnit.DAYS.toMillis(365); // One year 
	
	public static final String CRED_ACCESS_KEY = "accessKey";
	public static final String CRED_SECRET_KEY = "secretKey";
	
	public EC2IdentityPlugin(Properties properties) {
	}
	
	@Override
	public Token createToken(Map<String, String> userCredentials) {
		return getToken(userCredentials.get(CRED_ACCESS_KEY) + 
				":" + userCredentials.get(CRED_SECRET_KEY));
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		String[] accessIdSplit = accessId.split(":");
		String accessKey = accessIdSplit[0];
		String secretKey = accessIdSplit[1];
		
		try {
			createEC2Client(accessKey, secretKey).describeAccountAttributes();
		} catch (Exception e) {
			LOGGER.error("Couldn't load account summary from IAM.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(CRED_ACCESS_KEY, accessKey);
		attributes.put(CRED_SECRET_KEY, secretKey);
		
		return new Token(accessId, new Token.User(accessKey, accessKey), 
				new Date(new Date().getTime() + EXPIRATION_INTERVAL), 
				attributes);
	}

	protected AmazonEC2Client createEC2Client(String accessKey, String secretKey) {
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
		AmazonEC2Client ec2Client = new AmazonEC2Client(awsCreds);
		return ec2Client;
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
	public Credential[] getCredentials() {
		return new Credential[]{
				new Credential(CRED_ACCESS_KEY, true, null), 
				new Credential(CRED_SECRET_KEY, true, null)};
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
