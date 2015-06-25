package org.fogbowcloud.manager.core.plugins.util;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;

public class HttpClientWrapper {

	private static final Logger LOGGER = Logger.getLogger(HttpClientWrapper.class);
	private HttpClient client;

	private HttpResponseWrapper doRequest(String url, String method, HttpEntity entity) {
		HttpRequestBase request = null;
		if (method.equals("post")) {
			request = new HttpPost(url);
			if (entity != null) {
				((HttpPost)request).setEntity(entity);
			}
		} else if (method.equals("get")) {
			request = new HttpGet(url);
		}
		HttpResponse response = null;
		String responseStr = null;
		try {
			response = getClient().execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), Charsets.UTF_8);
		} catch (HttpHostConnectException e) {
			LOGGER.error("could not connect to the host.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.NOT_FOUND);
		} 	
		catch (Exception e) {
			LOGGER.error("Could not do post request.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		return new HttpResponseWrapper(response.getStatusLine(), responseStr);
	}
	
	public HttpResponseWrapper doPost(String url)  {
         return doPost(url, null);
	}
	
	public HttpResponseWrapper doGet(String url)  {
		return doRequest(url, "get", null);
	}
	
	public HttpResponseWrapper doPost(String url, 
			StringEntity entity) {
		return doRequest(url, "post", entity);
	}
	
	private HttpClient getClient() {
		if (client == null) {
			client = HttpClients.createMinimal();
		}
		return client;
	}
	
}
