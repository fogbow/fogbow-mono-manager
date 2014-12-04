package org.fogbowcloud.manager.core.plugins.openstack;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.occi.OCCIComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.data.ClientInfo;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.util.Series;

public class OpenStackOCCIComputePlugin extends OCCIComputePlugin{

	private static final String PUBLIC_KEY_TERM = "public_key";
	private static final String PUBLIC_KEY_SCHEME = "http://schemas.openstack.org/instance/credentials#";
	private static final String NAME_PUBLIC_KEY_ATTRIBUTE = "org.openstack.credentials.publickey.name";
	private static final String DATA_PUBLIC_KEY_ATTRIBUTE = "org.openstack.credentials.publickey.data";
	private static final String NAME_PUBLIC_KEY_DEFAULT = "fogbow_keypair";
	
	private static final String ABSOLUTE = "absolute";
	private static final String LIMITS = "limits";
	private String computeV2APIEndpoint;
	private static final String TENANT_ID = "tenantId";
	private final String COMPUTE_V2_API_ENDPOINT = "/v2/";
	
	public OpenStackOCCIComputePlugin(Properties properties) {
		super(properties);
		this.computeV2APIEndpoint = properties.getProperty("compute_openstack_v2api_url")
				+ COMPUTE_V2_API_ENDPOINT;
		super.fogTermToCategory.put(RequestConstants.PUBLIC_KEY_TERM, new Category(
				PUBLIC_KEY_TERM, PUBLIC_KEY_SCHEME, RequestConstants.MIXIN_CLASS));
	}
	
	@Override
	public Set<Header> getExtraHeaders(List<Category> requestCategories, Map<String, String> xOCCIAtt) {
		List<Category> openStackCategories = new ArrayList<Category>();
		
		for (Category category : requestCategories) {
			if (super.fogTermToCategory.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			}
			openStackCategories.add(super.fogTermToCategory.get(category.getTerm()));
			
			// adding ssh public key
			if (category.getTerm().equals(RequestConstants.PUBLIC_KEY_TERM)) {		
				xOCCIAtt.put(NAME_PUBLIC_KEY_ATTRIBUTE, NAME_PUBLIC_KEY_DEFAULT);
				xOCCIAtt.put(DATA_PUBLIC_KEY_ATTRIBUTE, xOCCIAtt.get(RequestAttribute.DATA_PUBLIC_KEY.getValue()));
				xOCCIAtt.remove(RequestAttribute.DATA_PUBLIC_KEY.getValue());
			}
		}

		String userdataBase64 = xOCCIAtt.remove(RequestAttribute.USER_DATA_ATT.getValue());
		
		if (userdataBase64 != null) {
			xOCCIAtt.put("org.openstack.compute.user_data", userdataBase64);
					
		}
		
		Set<Header> headers = new HashSet<Header>();
		for (Category category : openStackCategories) {
			headers.add(new BasicHeader(OCCIHeaders.CATEGORY, category.toHeader()));
		}
		for (String attName : xOCCIAtt.keySet()) {
			headers.add(new BasicHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, attName + "=" + "\""
					+ xOCCIAtt.get(attName) + "\""));
			
		}		
		return headers;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void bypass(Request request, org.restlet.Response response) {
		if (computeOCCIEndpoint == null || computeOCCIEndpoint.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
		}
		
		try {
			URI origRequestURI = new URI(request.getResourceRef().toString());						
			URI occiURI = new URI(oCCIEndpoint);
			URI newRequestURI = new URI(occiURI.getScheme(), occiURI.getUserInfo(),
					occiURI.getHost(), occiURI.getPort(), origRequestURI.getPath(),
					origRequestURI.getQuery(), origRequestURI.getFragment());
			Client clienteForBypass = new Client(Protocol.HTTP);
			Request proxiedRequest = new Request(request.getMethod(), newRequestURI.toString());
		
			// forwarding headers from cloud to response
			Series<org.restlet.engine.header.Header> requestHeaders = (Series<org.restlet.engine.header.Header>) request
					.getAttributes().get("org.restlet.http.headers");			

			
			boolean convertToOcci = false;
			for (org.restlet.engine.header.Header header : requestHeaders) {
				if (header.getName().contains("Content-type")
						&& !header.getValue().equals(OCCIHeaders.OCCI_CONTENT_TYPE)
						&& request.getMethod().getName()
								.equals(org.restlet.data.Method.POST.getName())) {
					convertToOcci = true;
				} else if (header.getName().contains(OCCIHeaders.ACCEPT)) {
					try {
						String headerAccept = header.getValue();
						ClientInfo clientInfo = null;
						if (headerAccept.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
							clientInfo = new ClientInfo(MediaType.TEXT_PLAIN);
						} else {
							clientInfo = new ClientInfo(new MediaType(headerAccept));
						}
						proxiedRequest.setClientInfo(clientInfo);
					} catch (Exception e) {}
				}
			}
			if (convertToOcci) {
				convertRequestToOcci(request, requestHeaders);				
			}
			
			proxiedRequest.getAttributes().put("org.restlet.http.headers", requestHeaders);
			
			clienteForBypass.handle(proxiedRequest, response);									
			
			// Removing Body of response when POST of actions
			if (origRequestURI.toASCIIString().contains("?action=")) {	
				response.setEntity("", MediaType.TEXT_PLAIN);
			}			
		} catch (URISyntaxException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					e.getMessage());
			
		}
	}
	
	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		String responseStr = doRequest(
				"get",
				computeV2APIEndpoint
						+ token.getAttributes().get(TENANT_ID)
		+ "/limits", token.getAccessId()).getResponseString();

		String maxCpu = getAttFromJson(OpenStackConfigurationConstants.MAX_TOTAL_CORES_ATT,
				responseStr);
		String cpuInUse = getAttFromJson(OpenStackConfigurationConstants.TOTAL_CORES_USED_ATT,
				responseStr);
		String maxMem = getAttFromJson(OpenStackConfigurationConstants.MAX_TOTAL_RAM_SIZE_ATT,
				responseStr);
		String memInUse = getAttFromJson(OpenStackConfigurationConstants.TOTAL_RAM_USED_ATT,
				responseStr);

		int cpuIdle = Integer.parseInt(maxCpu) - Integer.parseInt(cpuInUse);
		int memIdle = Integer.parseInt(maxMem) - Integer.parseInt(memInUse);

		return new ResourcesInfo(String.valueOf(cpuIdle), cpuInUse, String.valueOf(memIdle),
				memInUse, getFlavors(cpuIdle, memIdle), null);
	}	

	private void convertRequestToOcci(Request request, Series<org.restlet.engine.header.Header> requestHeaders) {
		try {
			String entityAsText = request.getEntityAsText();
			if (!entityAsText.contains(OCCIHeaders.CATEGORY)
					&& !entityAsText.contains(OCCIHeaders.X_OCCI_ATTRIBUTE)) {
				return;
			}
			requestHeaders.removeAll(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
			requestHeaders.removeAll("Content-type");
			requestHeaders.add(OCCIHeaders.ACCEPT, OCCIHeaders.OCCI_ACCEPT);
			requestHeaders.add("Content-type", OCCIHeaders.OCCI_CONTENT_TYPE);
			
			String category = ""; 
			String attribute = "";
			String[] linesBody = entityAsText.split("\n");
			for (String line : linesBody) {
				if (line.contains(OCCIHeaders.CATEGORY + ":")) {
					category += line.replace(OCCIHeaders.CATEGORY + ":", "").trim() + "\n";
				} else if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE + ":")) {
					attribute += line.replace(OCCIHeaders.X_OCCI_ATTRIBUTE + ":", "")
							.trim() + "\n";
				}
			}
			requestHeaders.add(new org.restlet.engine.header.Header(OCCIHeaders.CATEGORY,
					category.trim().replace("\n", ",")));
			requestHeaders.add(new org.restlet.engine.header.Header("X-occi-attribute",
					attribute.trim().replace("\n", ",")));
			request.setEntity("", MediaType.TEXT_PLAIN);			
		} catch (Exception e) {}
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
