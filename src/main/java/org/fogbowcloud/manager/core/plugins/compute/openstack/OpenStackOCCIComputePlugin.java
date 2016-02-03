package org.fogbowcloud.manager.core.plugins.compute.openstack;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.compute.occi.OCCIComputePlugin;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
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

public class OpenStackOCCIComputePlugin extends OCCIComputePlugin{

	private static final String PUBLIC_KEY_TERM = "public_key";
	private static final String PUBLIC_KEY_SCHEME = "http://schemas.openstack.org/instance/credentials#";
	private static final String NAME_PUBLIC_KEY_ATTRIBUTE = "org.openstack.credentials.publickey.name";
	private static final String DATA_PUBLIC_KEY_ATTRIBUTE = "org.openstack.credentials.publickey.data";
	private static final String NAME_PUBLIC_KEY_DEFAULT = "fogbow_keypair";
	
	private OpenStackNovaV2ComputePlugin openStackNovaV2ComputePlugin;
	
	public OpenStackOCCIComputePlugin(Properties properties) {
		super(properties);
		openStackNovaV2ComputePlugin = new OpenStackNovaV2ComputePlugin(properties);
		super.fogTermToCategory.put(OrderConstants.PUBLIC_KEY_TERM, new Category(
				PUBLIC_KEY_TERM, PUBLIC_KEY_SCHEME, OrderConstants.MIXIN_CLASS));
	}
	
	protected Set<Header> getExtraHeaders(List<Category> requestCategories,
			Map<String, String> xOCCIAtt, Token token) {
		List<Category> openStackCategories = new ArrayList<Category>();
		
		for (Category category : requestCategories) {
			openStackCategories.add(super.fogTermToCategory.get(category.getTerm()));
			
			// adding ssh public key
			if (category.getTerm().equals(OrderConstants.PUBLIC_KEY_TERM)) {		
				xOCCIAtt.put(NAME_PUBLIC_KEY_ATTRIBUTE, NAME_PUBLIC_KEY_DEFAULT);
				xOCCIAtt.put(DATA_PUBLIC_KEY_ATTRIBUTE, xOCCIAtt.get(OrderAttribute.DATA_PUBLIC_KEY.getValue()));
				xOCCIAtt.remove(OrderAttribute.DATA_PUBLIC_KEY.getValue());
			}
		}

		String userdataBase64 = xOCCIAtt.remove(OrderAttribute.USER_DATA_ATT.getValue());
		
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
						} else if (headerAccept.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE)) {
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
			
			// Removing one header if request has more than one (one normalized and other not normalized)
			if (requestHeaders.getValuesArray(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN)).length == 1
					&& requestHeaders.getValuesArray(OCCIHeaders.X_AUTH_TOKEN).length == 1) {
				requestHeaders.removeFirst(OCCIHeaders.X_AUTH_TOKEN);
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
		return openStackNovaV2ComputePlugin.getResourcesInfo(token);
	}	

	protected void convertRequestToOcci(Request request, Series<org.restlet.engine.header.Header> requestHeaders) {
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
	
	@Override
	public void uploadImage(Token token, String imagePath, String imageName, String diskFormat) {
		openStackNovaV2ComputePlugin.uploadImage(token, imagePath, imageName, null);		
	}
	
	@Override
	public String getImageId(Token token, String imageName) {
		return openStackNovaV2ComputePlugin.getImageId(token, imageName);
	}

	protected Flavor getFlavor(Token token, String requirements) {
		Flavor flavorFound = openStackNovaV2ComputePlugin.getFlavor(token, requirements);
		normalizeNameFlavorOCCI(flavorFound);
		return flavorFound;
	}
	
	protected void normalizeNameFlavorOCCI(Flavor flavor) {
		if (flavor != null) {
			flavor.setName(flavor.getName().replace(".", "-"));			
		}
	}
	
	protected void setFlavorsProvided(Properties properties) {		
	}
	
	protected void setFlavors(List<Flavor> flavors) {
		super.setFlavors(flavors);
	}
	
	protected void setClient(HttpClient client) {
		super.setClient(client);;
	}
	
}
