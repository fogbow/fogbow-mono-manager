package org.fogbowcloud.manager.core.plugins.openstack;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import org.apache.log4j.varia.FallbackErrorHandler;
import org.fogbowcloud.manager.core.model.Flavour;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class OpenStackComputePlugin implements ComputePlugin {

	private static final Logger LOGGER = Logger.getLogger(OpenStackComputePlugin.class);
	private static final String TERM_COMPUTE = "compute";
	private static final String SCHEME_COMPUTE = "http://schemas.ogf.org/occi/infrastructure#";
	private static final String CLASS_COMPUTE = "kind";
	private static final String COMPUTE_ENDPOINT = "/compute";

	public static final String OS_SCHEME = "http://schemas.openstack.org/template/os#";
	public static final String CIRROS_IMAGE_TERM = "cadf2e29-7216-4a5e-9364-cf6513d5f1fd";

	private String computeEndpoint;
	private Map<String, Category> fogTermToOpensStackCategory = new HashMap<String, Category>();

	public OpenStackComputePlugin(Properties properties) {
		this.computeEndpoint = properties.getProperty("compute_openstack_url") + COMPUTE_ENDPOINT;
		fogTermToOpensStackCategory.put(RequestConstants.SMALL_TERM,
				createFlavorCategory("compute_openstack_flavor_small", properties));
		fogTermToOpensStackCategory.put(RequestConstants.MEDIUM_TERM,
				createFlavorCategory("compute_openstack_flavor_medium", properties));
		fogTermToOpensStackCategory.put(RequestConstants.LARGE_TERM,
				createFlavorCategory("compute_openstack_flavor_large", properties));
		fogTermToOpensStackCategory.put(RequestConstants.LINUX_X86_TERM, new Category(
				CIRROS_IMAGE_TERM, OS_SCHEME, OCCIHeaders.MIXIN_CLASS));
	}

	private static Category createFlavorCategory(String flavorPropName, Properties properties) {
		return new Category(properties.getProperty(flavorPropName),
				"http://schemas.openstack.org/template/resource#", OCCIHeaders.MIXIN_CLASS);
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
			httpPost = new HttpPost(computeEndpoint);
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

	public Instance getInstance(String authToken, String instanceId) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpGet httpGet;
		try {
			httpGet = new HttpGet(computeEndpoint + "/" + instanceId);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));
			return Instance.parseInstanceDetails(responseStr);
		} catch (URISyntaxException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (HttpException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
	}

	public List<Instance> getInstances(String authToken) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpGet httpGet;
		try {
			httpGet = new HttpGet(computeEndpoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));
			return returnInstances(responseStr);
		} catch (URISyntaxException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (HttpException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
	}

	private List<Instance> returnInstances(String responseStr) {
		List<Instance> instances = new ArrayList<Instance>();
		String[] lines = responseStr.split("\n");
		for (String line : lines) {
			if (line.contains(Instance.PREFIX_DEFAULT_INSTANCE)) {
				instances.add(Instance.parseInstanceId(line));
			}
		}
		return instances;
	}

	public void removeInstances(String authToken) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpDelete httpDelete;
		try {
			httpDelete = new HttpDelete(computeEndpoint);
			httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpDelete);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
		} catch (URISyntaxException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (HttpException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
	}

	@Override
	public void removeInstance(String authToken, String instanceId) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpDelete httpDelete;
		try {
			httpDelete = new HttpDelete(computeEndpoint + "/" + instanceId);
			httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpDelete);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
		} catch (URISyntaxException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (HttpException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		} catch (IOException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
	}

	@Override
	public ResourcesInfo getResourcesInfo(String authToken) {
		// TODO Fake resources
		String cpuIdle = "1000";
		String cpuInUse = "0";
		String memIdle = "2048000";
		String memInUse = "0";		
		return new ResourcesInfo(null, cpuIdle, cpuInUse, memIdle, memInUse, getFlavors(cpuIdle, memIdle));
	}

	private List<Flavour> getFlavors(String cpuIdleStr, String memIdleStr) {
		int cpuIdle = Integer.parseInt(cpuIdleStr);
		int memIdle = Integer.parseInt(memIdleStr);		
		List<Flavour> flavors = new ArrayList<Flavour>();
		
		//flavors 
		int capacity = Math.min(cpuIdle / 1, memIdle / 2048);		
		Flavour smallFlavor = new Flavour(RequestConstants.SMALL_TERM, "1", "2048", capacity);
		capacity = Math.min(cpuIdle / 2, memIdle / 4096);		
		Flavour mediumFlavor = new Flavour(RequestConstants.MEDIUM_TERM, "2", "4096", capacity);
		capacity = Math.min(cpuIdle / 4, memIdle / 8192);
		Flavour largeFlavor = new Flavour(RequestConstants.LARGE_TERM, "4", "8192", capacity);
		flavors.add(smallFlavor);
		flavors.add(mediumFlavor);
		flavors.add(largeFlavor);
		return flavors;
	}
}
