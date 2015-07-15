package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import java.net.URISyntaxException;

import org.apache.http.ProtocolVersion;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.plugins.identity.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.model.Token;
import org.mockito.Mockito;

public class CloudStackTestHelper {
	
	public static final String CLOUDSTACK_URL = "http://localhost:8080/client/api";
	public static final String POST = "post";
	public static final String GET = "get";
	
	private static final ProtocolVersion PROTO = new ProtocolVersion("HTTP", 1, 1);
	
	public static void recordHTTPClientWrapperRequest(HttpClientWrapper httpClient,
			Token token, String requestType, String url, String response, int httpCode) {
		URIBuilder uriBuilder = null;
		try {
			uriBuilder = new URIBuilder(url);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		HttpResponseWrapper returned = new HttpResponseWrapper
				(new BasicStatusLine(PROTO, httpCode, "test reason"), response);
		HttpResponseWrapper mockedResponse = requestType.equals(GET) ? 
				httpClient.doGet(uriBuilder.toString()) : 
				httpClient.doPost(uriBuilder.toString());
		Mockito.when(mockedResponse).thenReturn(returned);
	}
	
	public static String createURL(String command, String... queryParts) {
		URIBuilder uriBuilder = null;
		try {
			uriBuilder = new URIBuilder(CLOUDSTACK_URL);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
		uriBuilder.addParameter(CloudStackComputePlugin.COMMAND, command);
		for (int i = 0; i < queryParts.length; i+=2) {
			uriBuilder.addParameter(queryParts[i], queryParts[i + 1]);
		}
		return uriBuilder.toString();
	}
	
}
