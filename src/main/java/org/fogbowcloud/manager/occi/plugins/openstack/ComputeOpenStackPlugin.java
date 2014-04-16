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
import org.fogbowcloud.manager.xmpp.core.ResourcesInfo;

public class ComputeOpenStackPlugin implements ComputePlugin {

	public static final String OS_SCHEME = "http://schemas.openstack.org/template/os#";
	private String computeEndPoint;
	private static final Logger LOGGER = Logger.getLogger(ComputeOpenStackPlugin.class);

	private final String TERM_COMPUTE = "compute";
	private final String SCHEME_COMPUTE = "http://schemas.ogf.org/occi/infrastructure#";
	private final String CLASS_COMPUTE = "kind";

	public static final String SMALL_FLAVOR_TERM = "m1-small";
	public static final String MEDIUM_FLAVOR_TERM = "m1-medium";
	public static final String LARGE_FLAVOR_TERM = "m1-large";
	public static final String CIRROS_IMAGE_TERM = "cadf2e29-7216-4a5e-9364-cf6513d5f1fd";

	private Map<String, Category> fogTermToOpensStackCategory;

	public ComputeOpenStackPlugin(String computeEndPoint) {
		this.computeEndPoint = computeEndPoint;
		fogTermToOpensStackCategory = new HashMap<String, Category>();

		// FIXME this code have to load this configuration from a conf file
		fogTermToOpensStackCategory.put(RequestConstants.SMALL_TERM, new Category(
				SMALL_FLAVOR_TERM, "http://schemas.openstack.org/template/resource#",
				OCCIHeaders.MIXIN_CLASS));
		fogTermToOpensStackCategory.put(RequestConstants.MEDIUM_TERM, new Category(
				MEDIUM_FLAVOR_TERM, "http://schemas.openstack.org/template/resource#",
				OCCIHeaders.MIXIN_CLASS));
		fogTermToOpensStackCategory.put(RequestConstants.LARGE_TERM, new Category(
				LARGE_FLAVOR_TERM, "http://schemas.openstack.org/template/resource#",
				OCCIHeaders.MIXIN_CLASS));
		fogTermToOpensStackCategory.put(RequestConstants.LINUX_X86_TERM, new Category(
				CIRROS_IMAGE_TERM, OS_SCHEME, OCCIHeaders.MIXIN_CLASS));
	}

	@Override
	public String requestInstance(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		List<Category> openStackCategories = new ArrayList<Category>();

		Category categoryCompute = new Category(TERM_COMPUTE, SCHEME_COMPUTE, CLASS_COMPUTE);
		openStackCategories.add(categoryCompute);

		for (Category category : categories) {
			if (fogTermToOpensStackCategory.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			}
			openStackCategories.add(fogTermToOpensStackCategory.get(category.getTerm()));
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

	@Override
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

	@Override
	public ResourcesInfo getResourcesInfo(String authToken) {
		// TODO Auto-generated method stub
		return null;
	}
}
