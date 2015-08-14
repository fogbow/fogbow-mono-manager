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
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.google.common.collect.ImmutableMap;

public class EC2IdentityPlugin implements IdentityPlugin {

	private static final Logger LOGGER = Logger.getLogger(EC2IdentityPlugin.class);
	private static final long EXPIRATION_INTERVAL = TimeUnit.DAYS.toMillis(365); // One year 
	
	public static final String CRED_ACCESS_KEY = "accessKey";
	public static final String CRED_SECRET_KEY = "secretKey";
	
	private static final String FEDERATION_USER_ACCESS_KEY = "local_proxy_account_ec2_access_key";
	private static final String FEDERATION_USER_SECRET_KEY = "local_proxy_account_ec2_secret_key";
	
	private Properties properties;
	
	public EC2IdentityPlugin(Properties properties) {
		this.properties = properties;
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
		
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
		
		GetUserResult getUserResult = null;
		try {
			AmazonIdentityManagementClient idClient = new AmazonIdentityManagementClient(awsCreds);
			getUserResult = idClient.getUser();
		} catch (Exception e) {
			LOGGER.error("Couldn't load account summary from IAM.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(CRED_ACCESS_KEY, accessKey);
		attributes.put(CRED_SECRET_KEY, secretKey);
		
		return new Token(accessId, getUserResult.getUser().getArn(), 
				new Date(new Date().getTime() + EXPIRATION_INTERVAL), 
				attributes);
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
		ImmutableMap<String, String> credentials = ImmutableMap.of(
				CRED_ACCESS_KEY, properties.getProperty(FEDERATION_USER_ACCESS_KEY), 
				CRED_SECRET_KEY, properties.getProperty(FEDERATION_USER_SECRET_KEY));
		return createToken(credentials);
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
