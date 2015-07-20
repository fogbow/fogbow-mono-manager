package org.fogbowcloud.manager.core.plugins.identity.cloudstack;

import java.security.Key;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.Charsets;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;

public class CloudStackHelper {
	
	private static final Logger LOGGER = Logger.getLogger(CloudStackIdentityPlugin.class);
	
	private static final String JSON = "json";
	private static final String RESPONSE_FORMAT = "response";

	public static void sign(URIBuilder requestEndpoint, String accessId) {
		
		String[] accessIdSplit = accessId.split(":");
		String apiKey = accessIdSplit[0];
		String secretKey = accessIdSplit[1];
		
		requestEndpoint.addParameter(CloudStackIdentityPlugin.API_KEY, apiKey);
		requestEndpoint.addParameter(RESPONSE_FORMAT, JSON);
		
		String query = null;
		try {
			query = requestEndpoint.toString().substring(
					requestEndpoint.toString().indexOf("?") + 1);
		} catch (IndexOutOfBoundsException e) {
			LOGGER.warn("Couldn't generate signature.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		String[] querySplit = query.split("&");
		TreeMap<String, String> queryParts = new TreeMap<String, String>();
		for (String queryPart : querySplit) {
			String[] queryPartSplit = queryPart.split("=");
			queryParts.put(queryPartSplit[0].toLowerCase(), 
					queryPartSplit[1].toLowerCase());
		}
		StringBuilder orderedQuery = new StringBuilder();
		for (Entry<String, String> queryPartEntry : queryParts.entrySet()) {
			if (orderedQuery.length() > 0) {
				orderedQuery.append("&");
			}
			orderedQuery.append(queryPartEntry.getKey()).append("=")
					.append(queryPartEntry.getValue());
		}
		
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			byte[] secretKeyBytes = secretKey.getBytes(Charsets.UTF_8);
			Key key = new SecretKeySpec(secretKeyBytes, 0, secretKeyBytes.length, "HmacSHA1");
			mac.init(key);
			String signature = Base64.encodeBase64String(
					mac.doFinal(orderedQuery.toString().getBytes(Charsets.UTF_8)));
			
			requestEndpoint.addParameter(CloudStackIdentityPlugin.SIGNATURE, signature);
			
		} catch (Exception e) {
			LOGGER.warn("Couldn't generate signature.", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}
	
}
