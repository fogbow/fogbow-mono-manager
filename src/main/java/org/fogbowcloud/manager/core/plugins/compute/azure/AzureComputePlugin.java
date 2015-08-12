package org.fogbowcloud.manager.core.plugins.compute.azure;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.core.plugins.util.SslHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.restlet.Request;
import org.restlet.Response;

public class AzureComputePlugin implements ComputePlugin {

	private static final Logger LOGGER = Logger
			.getLogger(AzureComputePlugin.class);

	private static final String BASE_URL = "https://management.core.windows.net/";
	private static final String GET_FLAVOR_COMMAND = "/rolesizes";
	private static final String GET_CLOUD_SERVICE_COMMAND = "/services/hostedservices";
	private static final String GET_VMS_COMMAND = "/services/hostedservices/%s/deployments/%s";
	private static final String DELETE_VM_COMMAND = "/services/hostedservices/%s/deployments/%s";
	private static final String DELETE_CLOUD_SERVICE_COMMAND = "/services/hostedservices/%s";

	private static final String[] AZURE_STATE_RUNNING = { "Running" };
	private static final String[] AZURE_STATE_FAILED = { "Deleting" };
	private static final String[] AZURE_STATE_PENDING = { "Deploying",
			"Starting", "RunningTransitioning", "SuspendedTransitioning" };
	private static final String[] AZURE_STATE_SUSPENDED = { "Suspending",
			"Suspended" };
	
	private static final int XML_VM_NAME = 0;
	private static final int XML_VM_ID = 0;
	private static final int XML_VM_STATE = 3;
	private static final int XML_ROLE_INSTANCES = 7;
	private static final int XML_ROLE_INSTANCE = 0;
	private static final int XML_ROLE_INSTANCE_FLAVOR = 5;
	private static final int XML_FLAVOR_NAME = 0;
	private static final int XML_FLAVOR_CPU = 2;
	private static final int XML_FLAVOR_MEM = 3;
	private static final int XML_FLAVOR_DISK = 8;
	private static final int XML_CLOUD_SERVICE = 1;

	private SSLConnectionSocketFactory sslSocketFactory;
	protected Map<String, Flavor> flavors;
	private HttpClientWrapper httpWrapper;

	public AzureComputePlugin(HttpClientWrapper httpWrapper) {
		this.httpWrapper = httpWrapper;
	}

	public AzureComputePlugin() {
		this(new HttpClientWrapper());
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		if (token.get(AzureAttributes.SUBSCRIPTION_ID_KEY) == null) {
			LOGGER.error("Subscription ID can't be null");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"Subscription ID can't be null");
		}
		List<String> cloudServicesNames = getCloudServicesNames(token);
		List<Instance> instances = new LinkedList<Instance>();
		for (String cloudService : cloudServicesNames) {
			Instance instance = getInstance(token, cloudService);
			if (instance != null) {
				instances.add(instance);
			}
		}
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(String.format(GET_VMS_COMMAND, instanceId, instanceId));
		HttpResponseWrapper response = httpWrapper.doGetSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		try {
			checkStatusResponse(response.getStatusLine());
			return mountInstanceFromXML(token, response);
		} catch (OCCIException occiException) {
			/*
			 * Azure throws the error "not found" when there is a cloud service
			 * not associated with any virtual machines, we want to ignore those
			 * cloud services.
			 */
			if (!occiException.getType().equals(ErrorType.NOT_FOUND)) {
				throw occiException;
			}
		}
		return null;
	}
	
	public void removeCloudService(Token token, String cloudServiceId){
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(String.format(DELETE_CLOUD_SERVICE_COMMAND, cloudServiceId));
		HttpResponseWrapper response = 	httpWrapper.doDeleteSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		checkStatusResponse(response.getStatusLine());
	}
	
	public void removeCloudServices(Token token) {
		List<String> cloudServices = getCloudServicesNames(token);
		for (String cloudService : cloudServices) {
			removeCloudService(token, cloudService);
		}	
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		StringBuilder urlDeleteVM = new StringBuilder(BASE_URL);
		urlDeleteVM.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		urlDeleteVM.append(String.format(DELETE_VM_COMMAND, instanceId, instanceId));
		HttpResponseWrapper response = httpWrapper.doDeleteSSL(urlDeleteVM.toString(),
				getSSLFromToken(token), getHeaders(null));
		checkStatusResponse(response.getStatusLine());
	}

	@Override
	public void removeInstances(Token token) {
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			removeInstance(token, instance.getId());
		}	
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {

		return null;
	}

	@Override
	public void bypass(Request request, Response response) {
		// TODO Auto-generated method stub
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName,
			String diskFormat) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getImageId(Token token, String imageName) {

		return null;
	}

	@Override
	public ImageState getImageState(Token token, String imageName) {

		return null;
	}

	private InstanceState getOCCIStatus(String azureStatus) {
		for (String azureStatusRunning : AZURE_STATE_RUNNING) {
			if (azureStatus.equals(azureStatusRunning)) {
				return InstanceState.RUNNING;
			}
		}
		for (String azureStatusPending : AZURE_STATE_PENDING) {
			if (azureStatus.equals(azureStatusPending)) {
				return InstanceState.PENDING;
			}
		}
		for (String azureStatusFailed : AZURE_STATE_FAILED) {
			if (azureStatus.equals(azureStatusFailed)) {
				return InstanceState.FAILED;
			}
		}
		for (String azureStatusSuspended : AZURE_STATE_SUSPENDED) {
			if (azureStatus.equals(azureStatusSuspended)) {
				return InstanceState.SUSPENDED;
			}
		}
		return InstanceState.PENDING;
	}

	private Instance mountInstanceFromXML(Token token,
			HttpResponseWrapper response) {
		Element virtualMachine = getElementFromResponse(response.getContent());
		Map<String, String> attributes = new HashMap<String, String>();

		String name = virtualMachine.getChildren().get(XML_VM_NAME).getText();
		String id = virtualMachine.getChildren().get(XML_VM_ID).getText();
		String stateStr = virtualMachine.getChildren().get(XML_VM_STATE)
				.getText();
		Element roleInstance = virtualMachine.getChildren()
				.get(XML_ROLE_INSTANCES).getChildren().get(XML_ROLE_INSTANCE);
		InstanceState state = getOCCIStatus(stateStr);
		String flavorStr = roleInstance.getChildren()
				.get(XML_ROLE_INSTANCE_FLAVOR).getText();
		Flavor flavor = getFlavors(token).get(flavorStr);

		attributes.put("occi.core.id", id);
		attributes.put("occi.compute.hostname", name);
		attributes.put("occi.compute.cores", flavor.getCpu());
		attributes.put("occi.compute.memory",
				String.valueOf(Integer.parseInt(flavor.getMem()) / 1024)); // Gb
		attributes.put("occi.compute.architecture", "Not defined");
		attributes.put("occi.compute.speed", "Not defined");
		attributes.put("occi.compute.state", state.getOcciState());

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.generateFlavorResource(flavorStr));

		return new Instance(id, resources, attributes,
				new ArrayList<Instance.Link>(), state);
	}
	
	private List<String> getCloudServicesNames(Token token) {
		List<String> cloudServicesNames = new LinkedList<String>();
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(GET_CLOUD_SERVICE_COMMAND);
		HttpResponseWrapper response = httpWrapper.doGetSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		checkStatusResponse(response.getStatusLine());
		List<Element> cloudServices = getElementFromResponse(
				response.getContent()).getChildren();
		for (Element cloudService : cloudServices) {
			String cloudServiceName = cloudService.getChildren().get(XML_CLOUD_SERVICE)
					.getText();
			cloudServicesNames.add(cloudServiceName);
		}
		return cloudServicesNames;
	}

	private Map<String, Flavor> getFlavors(Token token) {
		if (flavors != null) {
			return flavors;
		}
		flavors = new HashMap<String, Flavor>();
		StringBuilder url = new StringBuilder(BASE_URL);
		url.append(token.get(AzureAttributes.SUBSCRIPTION_ID_KEY));
		url.append(GET_FLAVOR_COMMAND);
		HttpResponseWrapper response = httpWrapper.doGetSSL(url.toString(),
				getSSLFromToken(token), getHeaders(null));
		checkStatusResponse(response.getStatusLine());
		List<Element> flavorsAzure = getElementFromResponse(
				response.getContent()).getChildren();
		for (Element flavorAzure : flavorsAzure) {
			String name = flavorAzure.getChildren().get(XML_FLAVOR_NAME)
					.getText();
			String cpu = flavorAzure.getChildren().get(XML_FLAVOR_CPU)
					.getText();
			String mem = flavorAzure.getChildren().get(XML_FLAVOR_MEM)
					.getText();
			String disk = String.valueOf(Integer.parseInt(flavorAzure
					.getChildren().get(XML_FLAVOR_DISK).getText()) / 1024);
			Flavor flavor = new Flavor(name, cpu, mem, disk);
			flavors.put(name, flavor);
		}
		return flavors;
	}

	private SSLConnectionSocketFactory getSSLFromToken(Token token) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		return sslSocketFactory;
	}

	private Element getElementFromResponse(String response) {
		SAXBuilder builder = new SAXBuilder();
		Document document;
		try {
			document = builder.build(new StringReader(response));
		} catch (Exception e) {
			LOGGER.warn("It was not possible to retrieve"
					+ " XML from the response", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
		Element element = document.getRootElement();
		return element;
	}

	protected void checkStatusResponse(StatusLine statusLine) {
		if (statusLine.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
			throw new OCCIException(ErrorType.UNAUTHORIZED,
					ResponseConstants.UNAUTHORIZED);
		} else if (statusLine.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
			throw new OCCIException(ErrorType.NOT_FOUND,
					statusLine.getReasonPhrase());
		} else if (statusLine.getStatusCode() > 204) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					statusLine.getReasonPhrase());
		}
	}

	private Map<String, String> getHeaders(Map<String, String> extraHeaders) {
		Map<String, String> headers = new HashMap<String, String>();
		if (extraHeaders != null) {
			headers.putAll(extraHeaders);
		}
		headers.put("x-ms-version", " 2013-03-01");
		return headers;
	}

}
