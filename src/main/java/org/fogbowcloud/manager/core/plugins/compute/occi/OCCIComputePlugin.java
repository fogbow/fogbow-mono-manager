package org.fogbowcloud.manager.core.plugins.compute.occi;

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
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenStackConfigurationConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
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
	public static final String OCCI_FLAVORS_NOT_SPECIFIED = "There is not a OCCI flavor specified in configuration file.";

	private String osScheme;
	private String instanceScheme;
	private String resourceScheme;
	private String networkId;

	protected Map<String, Category> fogTermToCategory = new HashMap<String, Category>();
	public static final String COMPUTE_ENDPOINT = "/compute/";
	protected String oCCIEndpoint;
	protected String computeOCCIEndpoint;
	private HttpClient client;	
	private List<Flavor> flavors = new ArrayList<Flavor>();

	protected static final Logger LOGGER = Logger.getLogger(OCCIComputePlugin.class);
	public static final String PREFIX_OCCI_FLAVORS_PROVIDED = "compute_occi_flavors_";

	public OCCIComputePlugin(Properties properties) {
		this.oCCIEndpoint = properties.getProperty("compute_occi_url");
		this.computeOCCIEndpoint = oCCIEndpoint + COMPUTE_ENDPOINT;
		
		setFlavorsProvided(properties);

		instanceScheme = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY);
		osScheme = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY);
		resourceScheme = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY);
		networkId = properties
				.getProperty(OpenStackConfigurationConstants.COMPUTE_OCCI_NETWORK_KEY);

		fogTermToCategory.put(OrderConstants.USER_DATA_TERM, new Category("user_data",
				instanceScheme, OrderConstants.MIXIN_CLASS));
	}

	@Override
	public String requestInstance(Token token, List<Category> orderCategories,
			Map<String, String> xOCCIAtt, String localImageId) {
		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ orderCategories + "; xOCCIAtt=" + xOCCIAtt);

		List<Category> occiCategories = new ArrayList<Category>();

		Category categoryCompute = new Category(TERM_COMPUTE, SCHEME_COMPUTE,
				OrderConstants.KIND_CLASS);
		occiCategories.add(categoryCompute);

		// removing fogbow-request category
		orderCategories.remove(new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS));

		if (localImageId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		occiCategories.add(new Category(localImageId, osScheme, OrderConstants.MIXIN_CLASS));
				
		// Finding and adding flavor
		
		Flavor flavorRef = getFlavor(token, xOCCIAtt.get(OrderAttribute.REQUIREMENTS.getValue()));
		if (flavorRef != null) {
			occiCategories.add(new Category(flavorRef.getName(), resourceScheme, OrderConstants.MIXIN_CLASS));						
		}
		
		Set<Header> headers = new HashSet<Header>();
		
		for (Category category : new ArrayList<Category>(orderCategories)) {
			if (category.getScheme().equals(OrderConstants.TEMPLATE_RESOURCE_SCHEME)) {
				orderCategories.remove(category);
				continue;
			}			
			if (fogTermToCategory.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			}			 
			occiCategories.add(fogTermToCategory.get(category.getTerm()));
		}
		
		headers.addAll(getExtraHeaders(orderCategories, xOCCIAtt, token));
		
			
		for (Category category : occiCategories) {
			headers.add(new BasicHeader(OCCIHeaders.CATEGORY, category.toHeader()));
		}
		for (String attName : xOCCIAtt.keySet()) {
			if (!attName.contains("org.fogbowcloud")) {
				headers.add(new BasicHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, attName + "=" + "\""
						+ xOCCIAtt.get(attName) + "\""));				
			}
		}

		// specifying network using inline creation of link instance
		if (networkId != null && !"".equals(networkId)) {
			headers.add(new BasicHeader(OCCIHeaders.LINK, "</network/" + networkId + ">; "
					+ "rel=\"http://schemas.ogf.org/occi/infrastructure#network\"; "
					+ "category=\"http://schemas.ogf.org/occi/infrastructure#networkinterface\";"));
		}	
		
		HttpResponse response = doRequest("post", computeOCCIEndpoint, token.getAccessId(),
				normalizeHeaders(headers)).getHttpResponse();
		
		Header locationHeader = response.getFirstHeader("Location");
		if (locationHeader != null) {
			return normalizeInstanceId(locationHeader.getValue());
		}
		return null;
	}
	
	private Set<Header> normalizeHeaders(Set<Header> headers) {
		Set<Header> newHeaders = new HashSet<Header>();
		Map<String, String> mapUniqueHeaders = new HashMap<String, String>();
		for (Header header : headers) {			
			String name = header.getName();
			String valueMap = mapUniqueHeaders.get(name);
			if (valueMap != null) {
				mapUniqueHeaders.put(name, valueMap + "," + header.getValue());			
			} else {
				mapUniqueHeaders.put(name, header.getValue());
			}			
		}		
		for (String key : mapUniqueHeaders.keySet()) {
			newHeaders.add(new BasicHeader(key, mapUniqueHeaders.get(key)));
		}
		return newHeaders;
	}

	protected Set<Header> getExtraHeaders(List<Category> orderCategories,
			Map<String, String> xOCCIAtt, Token token) {
		return new HashSet<Header>();
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {

		String responseStr = doRequest("get", computeOCCIEndpoint + instanceId, token.getAccessId())
				.getResponseString();

		return Instance.parseInstance(instanceId, responseStr);
	}

	@Override
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

	@Override
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

	protected Category createFlavorCategory(String flavorPropName, Properties properties) {
		return new Category(properties.getProperty(flavorPropName), resourceScheme,
				OrderConstants.MIXIN_CLASS);
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
		client = HttpClients.createMinimal();
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

	protected void setClient(HttpClient client) {
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

	@Override
	public void uploadImage(Token token, String imagePath, String imageName, String diskFormat) {
	}
	
	@Override
	public String getImageId(Token token, String imageName) {
		return null;
	}

	protected List<Flavor> getFlavors() {
		return flavors;
	}

	protected void setFlavors(List<Flavor> flavors) {
		this.flavors = flavors;
	}

	protected Flavor getFlavor(Token token, String requirements) {
		return RequirementsHelper.findSmallestFlavor(getFlavors(), requirements);
	}
	
	protected void setFlavorsProvided(Properties properties) {		
		for (final Object keyPropertie : properties.keySet()) {
			final String key = (String) keyPropertie;
			if (key.startsWith(PREFIX_OCCI_FLAVORS_PROVIDED)) {
				String value = properties.getProperty(key);
				String cpu = ResourceRepository.getAttValue("cpu", value);
				String mem = ResourceRepository.getAttValue("mem", value);
				String disk = ResourceRepository.getAttValue("disk", value);
				
				String flavorCorrect = key.replace(PREFIX_OCCI_FLAVORS_PROVIDED, "");
				
				flavors.add(new Flavor(flavorCorrect, flavorCorrect, cpu, mem, disk));
			}
		}
		if (flavors.size() == 0) {
			throw new OCCIException(ErrorType.BAD_REQUEST, OCCI_FLAVORS_NOT_SPECIFIED);
		}
	}
	
	@Override
	public ImageState getImageState(Token token, String imageName) {
		return null;
	}

	@Override
	public String attach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void dettach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		// TODO Auto-generated method stub
		
	}

}
