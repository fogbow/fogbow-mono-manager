package org.fogbowcloud.manager.core.plugins.util;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.util.HttpRequestUtil;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;

public class HttpClientWrapper {

	private static final String GET = "get";
	private static final String POST = "post";
	private static final String DELETE = "delete";
	private static final Logger LOGGER = Logger
			.getLogger(HttpClientWrapper.class);
	private HttpClient client;

	private HttpResponseWrapper doRequest(String url, String method,
			HttpEntity entity, SSLConnectionSocketFactory sslSocketFactory,
			Map<String, String> headers) {
		HttpRequestBase request = null;
		if (method.equals(POST)) {
			request = new HttpPost(url);
			if (entity != null) {
				((HttpPost) request).setEntity(entity);
			}
		} else if (method.equals(GET)) {
			request = new HttpGet(url);
		} else if (method.equals(DELETE)) {
			request = new HttpDelete(url);
		}
		if (headers != null) {
			for (Entry<String, String> header : headers.entrySet()) {
				request.setHeader(header.getKey(), header.getValue());
			}
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

	public HttpResponseWrapper doGet(String url, Map<String, String> headers) {
		return doRequest(url, GET, null, null, headers);
	}

	public HttpResponseWrapper doGet(String url) {
		return doGet(url, null);
	}

	public HttpResponseWrapper doPost(String url, StringEntity entity) {
		return doPost(url, entity, null);
	}

	public HttpResponseWrapper doPost(String url, StringEntity entity,
			Map<String, String> headers) {
		return doRequest(url, POST, entity, null, headers);
	}

	public HttpResponseWrapper doPostSSL(String url, StringEntity entity,
			SSLConnectionSocketFactory sslSocketFactory,
			Map<String, String> headers) {
		return doRequest(url, POST, entity, sslSocketFactory, headers);
	}

	public HttpResponseWrapper doPostSSL(String url,
			SSLConnectionSocketFactory sslSocketFactory) {
		return doPostSSL(url, null, sslSocketFactory, null);
	}

	public HttpResponseWrapper doGetSSL(String url,
			SSLConnectionSocketFactory sslSocketFactory) {
		return doGetSSL(url, sslSocketFactory, null);
	}

	public HttpResponseWrapper doGetSSL(String url,
			SSLConnectionSocketFactory sslSocketFactory,
			Map<String, String> headers) {
		return doRequest(url, GET, null, sslSocketFactory, headers);
	}
	
	public HttpResponseWrapper doDeleteSSL(String url, SSLConnectionSocketFactory sslSocketFactory, 
			Map<String, String> headers) {
		return doRequest(url, DELETE, null, sslSocketFactory, headers);
	}

	private HttpClient getClient(SSLConnectionSocketFactory sslSocketFactory) {
		if (this.client == null) {
			if (sslSocketFactory == null) {
				this.client = HttpRequestUtil.createHttpClient();
			} else {
				this.client = HttpRequestUtil.createHttpClient(sslSocketFactory);
			}
		}
		return client;
	}
}
