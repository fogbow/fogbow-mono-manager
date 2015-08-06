package org.fogbowcloud.manager.core.plugins.util;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;

public class HttpClientWrapper {

	private static final String GET = "get";
	private static final String POST = "post";
	private static final Logger LOGGER = Logger
			.getLogger(HttpClientWrapper.class);
	private HttpClient client;

	private HttpResponseWrapper doRequest(String url, String method,
			HttpEntity entity, SSLConnectionSocketFactory sslSocketFactory) {
		HttpRequestBase request = null;
		if (method.equals(POST)) {
			request = new HttpPost(url);
			if (entity != null) {
				((HttpPost) request).setEntity(entity);
			}
		} else if (method.equals(GET)) {
			request = new HttpGet(url);
		}
		HttpResponse response = null;
		String responseStr = null;
		try {
			response = getClient(sslSocketFactory).execute(request);
			responseStr = EntityUtils.toString(response.getEntity(),
					Charsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error("Could not perform HTTP request.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				response.getEntity().getContent().close();
			} catch (Exception e) {
				// Best effort
			}
		}
		return new HttpResponseWrapper(response.getStatusLine(), responseStr);
	}

	public HttpResponseWrapper doPost(String url) {
		return doPost(url, null);
	}

	public HttpResponseWrapper doGet(String url) {
		return doRequest(url, GET, null, null);
	}

	public HttpResponseWrapper doPost(String url, StringEntity entity) {
		return doRequest(url, POST, entity, null);
	}
	
	public HttpResponseWrapper doPostSSL(String url,
			SSLConnectionSocketFactory sslSocketFactory) {
		return doRequest(url, POST, null, sslSocketFactory);
	}

	public HttpResponseWrapper doGetSSL(String url,
			SSLConnectionSocketFactory sslSocketFactory) {
		return doRequest(url, GET, null, sslSocketFactory);
	}

	private HttpClient getClient(SSLConnectionSocketFactory sslSocketFactory) {
		if (sslSocketFactory == null) {
			client = HttpClients.createMinimal();
		} else {
			client = HttpClients.custom().setSSLSocketFactory(sslSocketFactory)
					.build();
		}
		return client;
	}
}
