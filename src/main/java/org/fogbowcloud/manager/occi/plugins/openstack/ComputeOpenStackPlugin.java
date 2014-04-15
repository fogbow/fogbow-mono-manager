package org.fogbowcloud.manager.occi.plugins.openstack;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.restlet.engine.header.HeaderConstants;

public class ComputeOpenStackPlugin implements ComputePlugin {

	public  static final String OS_SCHEME = "http://schemas.openstack.org/template/os#";
	private String computeEndPoint;
	private static final Logger LOGGER = Logger.getLogger(ComputeOpenStackPlugin.class);

	private final String TERM_COMPUTE = "compute";
	private final String SCHEM_COMPUTE = "http://schemas.ogf.org/occi/infrastructure#";
	private final String CLASS_COMPUTE = "kind";

	private Map<String, Category> termToOSCategory;

	public ComputeOpenStackPlugin(String computeEndPoint) {
		this.computeEndPoint = computeEndPoint;
		termToOSCategory = new HashMap<String, Category>();

		termToOSCategory.put(RequestConstants.SMALL_TERM, new Category("m1-small",
				"http://schemas.openstack.org/template/resource#", "mixin"));
		termToOSCategory.put(RequestConstants.MEDIUM_TERM, new Category("m1-medium",
				"http://schemas.openstack.org/template/resource#", "mixin"));
		termToOSCategory.put(RequestConstants.LARGE_TERM, new Category("m1-large",
				"http://schemas.openstack.org/template/resource#", "mixin"));
		termToOSCategory.put(RequestConstants.UBUNTU64_TERM, new Category(
				"cadf2e29-7216-4a5e-9364-cf6513d5f1fd",
				OS_SCHEME, "mixin"));
	}

	@Override
	public String requestInstance(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		List<Category> openStackCategories = new ArrayList<Category>();

		Category categoryCompute = new Category(TERM_COMPUTE, SCHEM_COMPUTE, CLASS_COMPUTE);
		openStackCategories.add(categoryCompute);

		for (Category category : categories) {
			if (termToOSCategory.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			}
			openStackCategories.add(termToOSCategory.get(category.getTerm()));
		}

		HttpClient httpCLient = new DefaultHttpClient();
		HttpPost httpPost;
		try {
			httpPost = new HttpPost(computeEndPoint);
			httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
			httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			for (Category category : openStackCategories) {
				httpPost.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
			}
			for (String attName : xOCCIAtt.keySet()) {
				httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
						attName + "=" + "\"" + xOCCIAtt.get(attName) + "\"");
			}
			HttpResponse response = httpCLient.execute(httpPost);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, EntityUtils.toString(
						response.getEntity(), String.valueOf(Charsets.UTF_8)));
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
				throw new OCCIException(ErrorType.NOT_FOUND, EntityUtils.toString(
						response.getEntity(), String.valueOf(Charsets.UTF_8)));
			}
			return EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (URISyntaxException e) {
			LOGGER.error(e);
		} catch (HttpException e) {
			LOGGER.error(e);
		} catch (IOException e) {
			LOGGER.error(e);
		}
		return null;
	}

	public String getInstanceDetails(String authToken, String instanceId) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpGet httpGet;
		try {
			httpGet = new HttpGet(computeEndPoint + "/" + instanceId);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			return EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (URISyntaxException e) {
			LOGGER.error(e);
		} catch (HttpException e) {
			LOGGER.error(e);
		} catch (IOException e) {
			LOGGER.error(e);
		}
		return null;
	}

	public String getInstancesFromUser(String authToken) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpGet httpGet;
		try {
			httpGet = new HttpGet(computeEndPoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			return EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (URISyntaxException e) {
			LOGGER.error(e);
		} catch (HttpException e) {
			LOGGER.error(e);
		} catch (IOException e) {
			LOGGER.error(e);
		}
		return null;
	}

	public String removeAllInstances(String authToken) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpDelete httpDelete;
		try {
			httpDelete = new HttpDelete(computeEndPoint);
			httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpDelete);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			return EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (URISyntaxException e) {
			LOGGER.error(e);
		} catch (HttpException e) {
			LOGGER.error(e);
		} catch (IOException e) {
			LOGGER.error(e);
		}
		return null;
	}

	public String removeInstance(String authToken, String instanceId) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpDelete httpDelete;
		try {
			httpDelete = new HttpDelete(computeEndPoint + "/" + instanceId);
			httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpDelete);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			return EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (URISyntaxException e) {
			LOGGER.error(e);
		} catch (HttpException e) {
			LOGGER.error(e);
		} catch (IOException e) {
			LOGGER.error(e);
		}
		return null;
	}
}
