package org.fogbowcloud.manager.core.plugins.occi;

import java.net.URI;
import java.net.URISyntaxException;
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
import org.apache.http.client.HttpClient;
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
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackConfigurationConstants;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.data.ClientInfo;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.util.Series;

public class OCCIComputePlugin implements ComputePlugin {

	protected static final String SCHEME_COMPUTE = "http://schemas.ogf.org/occi/infrastructure#";
	private static final int LAST_SUCCESSFUL_STATUS = 204;
	protected static final String TERM_COMPUTE = "compute";

	private static String osScheme;
	private String instanceScheme;
	private String resourceScheme;
	private String networkId;
	private String templateScheme;

	protected Map<String, Category> fogTermToCategory = new HashMap<String, Category>();
	public static final String COMPUTE_ENDPOINT = "/compute/";
	protected String oCCIEndpoint;
	protected String computeOCCIEndpoint;
	private HttpClient client;	

	protected static final Logger LOGGER = Logger.getLogger(OCCIComputePlugin.class);

	public OCCIComputePlugin(Properties properties) {
		this.oCCIEndpoint = properties.getProperty("compute_occi_url");
		this.computeOCCIEndpoint = oCCIEndpoint + COMPUTE_ENDPOINT;

		instanceScheme = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY);
		osScheme = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY);
		resourceScheme = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY);
		networkId = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_NETWORK_KEY);
		templateScheme = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_TEMPLATE_SCHEME_KEY);

		
		Map<String, String> imageProperties = getImageProperties(properties);

		if (imageProperties == null || imageProperties.isEmpty()) {
			LOGGER.warn(ResponseConstants.IMAGES_NOT_SPECIFIED);
		}
	
		for (String imageName : imageProperties.keySet()) {
			fogTermToCategory.put(imageName, new Category(imageProperties.get(imageName), osScheme,
					RequestConstants.MIXIN_CLASS));
			ResourceRepository.getInstance().addImageResource(imageName);
		}		
						
		Map<String, String> templateProperties = getTemplateProperties(properties);
		
		if (templateProperties == null || templateProperties.isEmpty()) {
			LOGGER.warn(ResponseConstants.TEMPLATE_NOT_SPECIFIED);
		}
		
		for (String templateName : templateProperties.keySet()) {
			fogTermToCategory.put(templateName, new Category(templateProperties.get(templateName),
					templateScheme, RequestConstants.MIXIN_CLASS));
			ResourceRepository.getInstance().addImageResource(templateName);
		}					
		
		fogTermToCategory.put(
				RequestConstants.SMALL_TERM,
				createFlavorCategory(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY,
						properties));
		fogTermToCategory
				.put(RequestConstants.MEDIUM_TERM,
						createFlavorCategory(
								OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY,
								properties));
		fogTermToCategory.put(
				RequestConstants.LARGE_TERM,
				createFlavorCategory(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY,
						properties));

		fogTermToCategory.put(RequestConstants.USER_DATA_TERM, new Category("user_data",
				instanceScheme, RequestConstants.MIXIN_CLASS));
	}

	@Override
	public String requestInstance(Token token, List<Category> requestCategories,
			Map<String, String> xOCCIAtt) {

		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ requestCategories + "; xOCCIAtt=" + xOCCIAtt);

		List<Category> occiCategories = new ArrayList<Category>();

		Category categoryCompute = new Category(TERM_COMPUTE, SCHEME_COMPUTE,
				RequestConstants.KIND_CLASS);
		occiCategories.add(categoryCompute);

		// removing fogbow-request category
		requestCategories.remove(new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS));

		for (Category category : requestCategories) {
			if (fogTermToCategory.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			}
			occiCategories.add(fogTermToCategory.get(category.getTerm()));
		}

		Set<Header> headers = new HashSet<Header>();
		for (Category category : occiCategories) {
			headers.add(new BasicHeader(OCCIHeaders.CATEGORY, category.toHeader()));
		}
		for (String attName : xOCCIAtt.keySet()) {
			headers.add(new BasicHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, attName + "=" + "\""
					+ xOCCIAtt.get(attName) + "\""));
		}

		headers.addAll(getExtraHeaders(requestCategories, xOCCIAtt));

		// specifying network using inline creation of link instance
		if (networkId != null && !"".equals(networkId)) {
			headers.add(new BasicHeader(OCCIHeaders.LINK, "</network/" + networkId + ">; "
					+ "rel=\"http://schemas.ogf.org/occi/infrastructure#network\"; "
					+ "category=\"http://schemas.ogf.org/occi/infrastructure#networkinterface\";"));
		}

		HttpResponse response = doRequest("post", computeOCCIEndpoint, token.getAccessId(), headers)
				.getHttpResponse();

		Header locationHeader = response.getFirstHeader("Location");
		if (locationHeader != null) {
			return normalizeInstanceId(locationHeader.getValue());
		}
		return null;
	}

	protected Set<Header> getExtraHeaders(List<Category> requestCategories,
			Map<String, String> xOCCIAtt) {
		return new HashSet<Header>();
	}

	public Instance getInstance(Token token, String instanceId) {

		String responseStr = doRequest("get", computeOCCIEndpoint + instanceId, token.getAccessId())
				.getResponseString();

		return Instance.parseInstance(instanceId, responseStr);
	}

	public List<Instance> getInstances(Token token) {
		String responseStr = doRequest("get", computeOCCIEndpoint, token.getAccessId())
				.getResponseString();

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

	public void removeInstances(Token token) {
		doRequest("delete", computeOCCIEndpoint, token.getAccessId());
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		doRequest("delete", computeOCCIEndpoint + instanceId, token.getAccessId());
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		return null;
	}

	private static Map<String, String> getImageProperties(Properties properties) {
		Map<String, String> imageProperties = new HashMap<String, String>();

		for (Object propName : properties.keySet()) {
			String propNameStr = (String) propName;
			if (propNameStr.startsWith(OpenStackConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX)) {
				imageProperties.put(propNameStr
						.substring(OpenStackConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX
								.length()), properties.getProperty(propNameStr));
			}
		}
		LOGGER.debug("Image properties: " + imageProperties);
		return imageProperties;
	}

	private static Map<String, String> getTemplateProperties(Properties properties) {
		Map<String, String> templateProperties = new HashMap<String, String>();

		for (Object propName : properties.keySet()) {
			String propNameStr = (String) propName;
			if (propNameStr.startsWith(OpenStackConfigurationConstants.COMPUTE_OCCI_TEMPLATE_PREFIX)) {
				templateProperties.put(propNameStr.substring(OpenStackConfigurationConstants.COMPUTE_OCCI_TEMPLATE_PREFIX.length()),
						properties.getProperty(propNameStr));
			}
		}
		LOGGER.debug("Image properties: " + templateProperties);
		return templateProperties;
	}
	
	protected Category createFlavorCategory(String flavorPropName, Properties properties) {
		return new Category(properties.getProperty(flavorPropName), resourceScheme,
				RequestConstants.MIXIN_CLASS);
	}

	protected Response doRequest(String method, String endpoint, String authToken) {
		return doRequest(method, endpoint, authToken, new HashSet<Header>());
	}

	protected Response doRequest(String method, String endpoint, String authToken,
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
				initClient();
			}
			httpResponse = client.execute(request);
			responseStr = EntityUtils.toString(httpResponse.getEntity(),
					String.valueOf(Charsets.UTF_8));
		} catch (Exception e) {
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

	private void initClient() {
		client = new DefaultHttpClient();
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		client = new DefaultHttpClient(new ThreadSafeClientConnManager(params, client
				.getConnectionManager().getSchemeRegistry()), params);
	}

	protected String normalizeInstanceId(String instanceId) {
		if (!instanceId.contains("/")) {
			return instanceId;
		}
		String[] splitInstanceId = instanceId.split("/");
		return splitInstanceId[splitInstanceId.length - 1];
	}

	@SuppressWarnings("unchecked")
	@Override
	public void bypass(Request request, org.restlet.Response response) {
		if (computeOCCIEndpoint == null || computeOCCIEndpoint.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
		}

		URI origRequestURI;
		try {
			origRequestURI = new URI(request.getResourceRef().toString());
			URI occiURI = new URI(oCCIEndpoint);
			URI newRequestURI = new URI(occiURI.getScheme(), occiURI.getUserInfo(),
					occiURI.getHost(), occiURI.getPort(), origRequestURI.getPath(),
					origRequestURI.getQuery(), origRequestURI.getFragment());
			Client clienteForBypass = new Client(Protocol.HTTP);
			Request proxiedRequest = new Request(request.getMethod(), newRequestURI.toString());

			// forwarding headers from cloud to response
			Series<org.restlet.engine.header.Header> requestHeaders = (Series<org.restlet.engine.header.Header>) request
					.getAttributes().get("org.restlet.http.headers");

			for (org.restlet.engine.header.Header header : requestHeaders) {
				if (header.getName().contains(OCCIHeaders.ACCEPT)) {
					try {
						String headerAccept = header.getValue();
						ClientInfo clientInfo = null;
						if (headerAccept.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
							clientInfo = new ClientInfo(MediaType.TEXT_PLAIN);
						} else {
							clientInfo = new ClientInfo(new MediaType(headerAccept));
						}
						proxiedRequest.setClientInfo(clientInfo);
					} catch (Exception e) {
					}
				}
			}

			proxiedRequest.getAttributes().put("org.restlet.http.headers", requestHeaders);
			clienteForBypass.handle(proxiedRequest, response);
		} catch (URISyntaxException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());

		}
	}

	public static String getOSScheme() {
		return osScheme;
	}	
	
	public void setClient(HttpClient client) {
		this.client = client;
	} 
	
	protected class Response {

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
