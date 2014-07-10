package org.fogbowcloud.manager.core.plugins.openstack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.codec.Charsets;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.ssh.DefaultSSHTunnel;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenStackComputePlugin implements ComputePlugin {

	// According to OCCI specification
	private static final String SCHEME_COMPUTE = "http://schemas.ogf.org/occi/infrastructure#";
	private static final int LAST_SUCCESSFUL_STATUS = 204;
	
	private static String osScheme;
	private static String instanceScheme;
	public static String resourceScheme; 
	
	private static final String ABSOLUTE = "absolute";
	private static final String LIMITS = "limits";
	private static final String TERM_COMPUTE = "compute";
	private static final String CLASS_COMPUTE = "kind";
	private static final String COMPUTE_ENDPOINT = "/compute/";
	private final String COMPUTE_V2_API_ENDPOINT = "/v2/";

	private static final String MAX_TOTAL_CORES_ATT = "maxTotalCores";
	private static final String TOTAL_CORES_USED_ATT = "totalCoresUsed";
	private static final String MAX_TOTAL_RAM_SIZE_ATT = "maxTotalRAMSize";
	private static final String TOTAL_RAM_USED_ATT = "totalRAMUsed";

	private String computeOCCIEndpoint;
	private String computeV2APIEndpoint;
	private Map<String, Category> fogTermToOpensStackCategory = new HashMap<String, Category>();
	private DefaultHttpClient client;
	private String networkId;

	private static final Logger LOGGER = Logger.getLogger(OpenStackComputePlugin.class);
	
	public OpenStackComputePlugin(Properties properties) {
		this.computeOCCIEndpoint = properties.getProperty(ConfigurationConstants.COMPUTE_OCCI_URL_KEY)
				+ COMPUTE_ENDPOINT;
		this.computeV2APIEndpoint = properties.getProperty("compute_openstack_v2api_url")
				+ COMPUTE_V2_API_ENDPOINT;
		
		instanceScheme = properties
				.getProperty(ConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY);
		osScheme = properties.getProperty(ConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY);
		resourceScheme = properties.getProperty(ConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY);
		networkId = properties.getProperty(ConfigurationConstants.COMPUTE_OCCI_NETWORK_KEY);	

		Map<String, String> imageProperties = getImageProperties(properties);
		
		if (imageProperties == null || imageProperties.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IMAGES_NOT_SPECIFIED);
		}
		
		for (String imageName : imageProperties.keySet()) {
			fogTermToOpensStackCategory.put(imageName, new Category(imageProperties.get(imageName),
					osScheme, RequestConstants.MIXIN_CLASS));
			ResourceRepository.getInstance().addImageResource(imageName);
		}
		
		fogTermToOpensStackCategory.put(
				RequestConstants.SMALL_TERM,
				createFlavorCategory(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY,
						properties));
		fogTermToOpensStackCategory.put(
				RequestConstants.MEDIUM_TERM,
				createFlavorCategory(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY,
						properties));
		fogTermToOpensStackCategory.put(
				RequestConstants.LARGE_TERM,
				createFlavorCategory(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY,
						properties));
		
		fogTermToOpensStackCategory.put(RequestConstants.USER_DATA_TERM, new Category("user_data",
				instanceScheme, RequestConstants.MIXIN_CLASS));
	}

	private static Map<String, String> getImageProperties(Properties properties) {
		Map<String, String> imageProperties = new HashMap<String, String>();		
		
		for (Object propName : properties.keySet()) {
			String propNameStr = (String) propName;
			if (propNameStr.startsWith(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX)) {
				imageProperties.put(propNameStr
						.substring(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX.length()),
						properties.getProperty(propNameStr));
			}
		}
		LOGGER.debug("Image properties: " + imageProperties);
		return imageProperties;
	}

	private static Category createFlavorCategory(String flavorPropName, Properties properties) {
		return new Category(properties.getProperty(flavorPropName),
				resourceScheme, RequestConstants.MIXIN_CLASS);
	}

	@Override
	public String requestInstance(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		
		LOGGER.debug("Requesting instance with accessId=" + authToken + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt);

		List<Category> openStackCategories = new ArrayList<Category>();

		Category categoryCompute = new Category(TERM_COMPUTE, SCHEME_COMPUTE, CLASS_COMPUTE);
		openStackCategories.add(categoryCompute);

		// removing fogbow-request category
		categories.remove(new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS));

		for (Category category : categories) {
			if (fogTermToOpensStackCategory.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			}
			openStackCategories.add(fogTermToOpensStackCategory.get(category.getTerm()));
		}

		xOCCIAtt.put("org.openstack.compute.user_data",
				xOCCIAtt.remove(DefaultSSHTunnel.USER_DATA_ATT));

		Set<Header> headers = new HashSet<Header>();
		for (Category category : openStackCategories) {
			headers.add(new BasicHeader(OCCIHeaders.CATEGORY, category.toHeader()));
		}
		for (String attName : xOCCIAtt.keySet()) {
			headers.add(new BasicHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, attName + "=" + "\""
					+ xOCCIAtt.get(attName) + "\""));
		}
		
		//specifying network using inline creation of link instance
		if (networkId != null && !"".equals(networkId)){
			headers.add(new BasicHeader(
					OCCIHeaders.LINK,
					"</network/"+ networkId	+ ">; "
							+ "rel=\"http://schemas.ogf.org/occi/infrastructure#network\"; "
							+ "category=\"http://schemas.ogf.org/occi/infrastructure#networkinterface\";"));
		}

		HttpResponse response = doRequest("post", computeOCCIEndpoint, authToken, headers)
				.getHttpResponse();

		Header locationHeader = response.getFirstHeader("Location");
		if (locationHeader != null) {
			return normalizeInstanceId(locationHeader.getValue());
		}
		return null;
	}

	private String normalizeInstanceId(String instanceId) {
		if (!instanceId.contains("/")) {
			return instanceId;
		}
		String[] splitInstanceId = instanceId.split("/");
		return splitInstanceId[splitInstanceId.length - 1];
	}

	public Instance getInstance(String authToken, String instanceId) {

		String responseStr = doRequest("get", computeOCCIEndpoint + instanceId, authToken)
				.getResponseString();

		return Instance.parseInstance(instanceId, responseStr);
	}

	public List<Instance> getInstances(String authToken) {
		String responseStr = doRequest("get", computeOCCIEndpoint, authToken).getResponseString();

		return parseInstances(responseStr);
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
		doRequest("delete", computeOCCIEndpoint, authToken);
	}

	@Override
	public void removeInstance(String authToken, String instanceId) {
		doRequest("delete", computeOCCIEndpoint + instanceId, authToken);
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		String responseStr = doRequest(
				"get",
				computeV2APIEndpoint + token.getAttributes().get(OpenStackIdentityPlugin.TENANT_ID_KEY)
						+ "/limits", token.getAccessId()).getResponseString();

		String maxCpu = getAttFromJson(MAX_TOTAL_CORES_ATT, responseStr);
		String cpuInUse = getAttFromJson(TOTAL_CORES_USED_ATT, responseStr);
		String maxMem = getAttFromJson(MAX_TOTAL_RAM_SIZE_ATT, responseStr);
		String memInUse = getAttFromJson(TOTAL_RAM_USED_ATT, responseStr);

		int cpuIdle = Integer.parseInt(maxCpu) - Integer.parseInt(cpuInUse);
		int memIdle = Integer.parseInt(maxMem) - Integer.parseInt(memInUse);

		return new ResourcesInfo(String.valueOf(cpuIdle), cpuInUse, String.valueOf(memIdle),
				memInUse, getFlavors(cpuIdle, memIdle), null);
	}

	private Response doRequest(String method, String endpoint, String authToken) {
		return doRequest(method, endpoint, authToken, new HashSet<Header>());
	}

	private Response doRequest(String method, String endpoint, String authToken,
			Set<Header> additionalHeaders) {
		HttpResponse httpResponse;
		String responseStr = null;
		try {
			HttpUriRequest request = null;
			if (method.equals("get")) {
				request = new HttpGet(endpoint);
			} else if (method.equals("delete")) {
				request = new HttpDelete(endpoint);
			} else if (method.equals("post")) {
				request = new HttpPost(endpoint);
				request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
			}
			if (authToken != null) {
				request.addHeader(OCCIHeaders.X_AUTH_TOKEN, authToken);
			}
			for (Header header : additionalHeaders) {
				request.addHeader(header);
			}

			LOGGER.debug("AccessId=" + authToken + "; headers=" + additionalHeaders);

			if (client == null) {
				client = new DefaultHttpClient();
				HttpParams params = new BasicHttpParams();
				params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
				client = new DefaultHttpClient(new ThreadSafeClientConnManager(params, client
						.getConnectionManager().getSchemeRegistry()), params);
			}
			httpResponse = client.execute(request);
			responseStr = EntityUtils.toString(httpResponse.getEntity(),
				String.valueOf(Charsets.UTF_8));	
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}

		checkStatusResponse(httpResponse, responseStr);

		return new Response(httpResponse, responseStr);
	}

	private void checkStatusResponse(HttpResponse response, String errorMessage) {
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
			if (errorMessage.contains(ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
				throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
			}
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMessage);
		} else if (response.getStatusLine().getStatusCode() > LAST_SUCCESSFUL_STATUS) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getStatusLine().toString());
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

	public static String getOSScheme() {
		return osScheme;
	}
	
	private class Response {

		private HttpResponse httpResponse;
		private String responseString;

		public Response(HttpResponse httpResponse, String responseString) {
			this.httpResponse = httpResponse;
			this.responseString = responseString;
		}

		public HttpResponse getHttpResponse() {
			return httpResponse;
		}

		public String getResponseString() {
			return responseString;
		}
	}

}