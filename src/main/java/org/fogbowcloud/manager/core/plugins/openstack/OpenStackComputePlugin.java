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
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.ssh.DefaultSSHTunnel;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenStackComputePlugin implements ComputePlugin {

	private static final String INSTANCE_SCHEME = "http://schemas.openstack.org/compute/instance#";
	private static final String SCHEME_COMPUTE = "http://schemas.ogf.org/occi/infrastructure#";
	public static final String OS_SCHEME = "http://schemas.openstack.org/template/os#";
	
	private static final String ABSOLUTE = "absolute";
	private static final String LIMITS = "limits";
	private static final Logger LOGGER = Logger.getLogger(OpenStackComputePlugin.class);
	private static final String TERM_COMPUTE = "compute";
	private static final String CLASS_COMPUTE = "kind";
	private static final String COMPUTE_ENDPOINT = "/compute/";
	private final String federationTenantId;
	private final String COMPUTE_V2_API_ENDPOINT = "/v2/";

	private static final String MAX_TOTAL_CORES_ATT = "maxTotalCores";
	private static final String TOTAL_CORES_USED_ATT = "totalCoresUsed";
	private static final String MAX_TOTAL_RAM_SIZE_ATT = "maxTotalRAMSize";
	private static final String TOTAL_RAM_USED_ATT = "totalRAMUsed";

	private String computeOCCIEndpoint;
	private String computeV2APIEndepoint;
	private Map<String, Category> fogTermToOpensStackCategory = new HashMap<String, Category>();

	public OpenStackComputePlugin(Properties properties) {
		this.computeOCCIEndpoint = properties.getProperty("compute_openstack_occi_url")
				+ COMPUTE_ENDPOINT;
		this.computeV2APIEndepoint = properties.getProperty("compute_openstack_v2api_url")
				+ COMPUTE_V2_API_ENDPOINT;
		this.federationTenantId = properties.getProperty("federation_user_tenant_id");

		fogTermToOpensStackCategory.put(RequestConstants.SMALL_TERM,
				createFlavorCategory("compute_openstack_flavor_small", properties));
		fogTermToOpensStackCategory.put(RequestConstants.MEDIUM_TERM,
				createFlavorCategory("compute_openstack_flavor_medium", properties));
		fogTermToOpensStackCategory.put(RequestConstants.LARGE_TERM,
				createFlavorCategory("compute_openstack_flavor_large", properties));
		fogTermToOpensStackCategory.put(RequestConstants.LINUX_X86_TERM, new Category(
				properties.getProperty("compute_openstack_default_cirros_image"), 
				OS_SCHEME, OCCIHeaders.MIXIN_CLASS));
		fogTermToOpensStackCategory.put(RequestConstants.USER_DATA_TERM, 
				new Category("user_data", INSTANCE_SCHEME, OCCIHeaders.MIXIN_CLASS));
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

		//removing fogbow-request category
		categories.remove(new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.CLASS));
		
		for (Category category : categories) {
			if (fogTermToOpensStackCategory.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			}
			openStackCategories.add(fogTermToOpensStackCategory.get(category.getTerm()));
		}
		
		xOCCIAtt.put("org.openstack.compute.user_data", xOCCIAtt.remove(DefaultSSHTunnel.USER_DATA_ATT));

		HttpClient httpClient = new DefaultHttpClient();
		HttpPost httpPost;
		try {
			httpPost = new HttpPost(computeOCCIEndpoint);
			httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
			httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			for (Category category : openStackCategories) {
				httpPost.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
			}
			for (String attName : xOCCIAtt.keySet()) {
				httpPost.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
						attName + "=" + "\"" + xOCCIAtt.get(attName) + "\"");
			}
			HttpResponse response = httpClient.execute(httpPost);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, EntityUtils.toString(
						response.getEntity(), String.valueOf(Charsets.UTF_8)));
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
				throw new OCCIException(ErrorType.NOT_FOUND, EntityUtils.toString(
						response.getEntity(), String.valueOf(Charsets.UTF_8)));
			}
			String instanceLocation = response.getFirstHeader("Location").getValue();
			return getInstanceId(instanceLocation);
		} catch (URISyntaxException e) {
			LOGGER.error(e);
		} catch (HttpException e) {
			LOGGER.error(e);
		} catch (IOException e) {
			LOGGER.error(e);
		}
		return null;
	}

	private String getInstanceId(String instanceLocation) {
		//location format: OCCI_URL + COMPUTE_ENDPOINT + instanceID
		String[] tokens = instanceLocation.split(COMPUTE_ENDPOINT);		
		return tokens[1];
	}

	public Instance getInstance(String authToken, String instanceId) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpGet httpGet;
		try {
			httpGet = new HttpGet(computeOCCIEndpoint + instanceId);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));
			return Instance.parseInstance(instanceId, responseStr);
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
			httpGet = new HttpGet(computeOCCIEndpoint);
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			}
			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));
			return parseInstances(responseStr);
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

	private List<Instance> parseInstances(String responseStr) {
		List<Instance> instances = new ArrayList<Instance>();
		String[] lines = responseStr.split("\n");
		for (String line : lines) {
			if (line.contains(Instance.PREFIX_DEFAULT_INSTANCE)) {
				instances.add(Instance.parseInstance(line));
			}
		}
		return instances;
	}

	public void removeInstances(String authToken) {
		HttpClient httpCLient = new DefaultHttpClient();
		HttpDelete httpDelete;
		try {
			httpDelete = new HttpDelete(computeOCCIEndpoint);
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
			httpDelete = new HttpDelete(computeOCCIEndpoint + instanceId);
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
		HttpClient httpCLient = new DefaultHttpClient();
		HttpGet httpGet;
		try {
			httpGet = new HttpGet(computeV2APIEndepoint + federationTenantId + "/limits");
			httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			HttpResponse response = httpCLient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
			} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
			}
			String responseStr = EntityUtils.toString(response.getEntity(),
					String.valueOf(Charsets.UTF_8));

			String maxCpu = getAttFromJson(MAX_TOTAL_CORES_ATT, responseStr);
			String cpuInUse = getAttFromJson(TOTAL_CORES_USED_ATT, responseStr);
			String maxMem = getAttFromJson(MAX_TOTAL_RAM_SIZE_ATT, responseStr);
			String memInUse = getAttFromJson(TOTAL_RAM_USED_ATT, responseStr);

			int cpuIdle = Integer.parseInt(maxCpu) - Integer.parseInt(cpuInUse);
			int memIdle = Integer.parseInt(maxMem) - Integer.parseInt(memInUse);

			return new ResourcesInfo(String.valueOf(cpuIdle), cpuInUse,
					String.valueOf(memIdle), memInUse, getFlavors(cpuIdle, memIdle), null);

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

	private String getAttFromJson(String attName, String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject(LIMITS).getJSONObject(ABSOLUTE).getString(attName).toString();
		} catch (JSONException e) {
			return null;
		}
	}

	private List<Flavor> getFlavors(int cpuIdle, int memIdle) {
		List<Flavor> flavors = new ArrayList<Flavor>();
		// flavors
		int capacity = Math.min(cpuIdle / 1, memIdle / 2048);
		Flavor smallFlavor = new Flavor(RequestConstants.SMALL_TERM, "1", "2048", capacity);
		capacity = Math.min(cpuIdle / 2, memIdle / 4096);
		Flavor mediumFlavor = new Flavor(RequestConstants.MEDIUM_TERM, "2", "4096", capacity);
		capacity = Math.min(cpuIdle / 4, memIdle / 8192);
		Flavor largeFlavor = new Flavor(RequestConstants.LARGE_TERM, "4", "8192", capacity);
		flavors.add(smallFlavor);
		flavors.add(mediumFlavor);
		flavors.add(largeFlavor);
		return flavors;
	}
}
