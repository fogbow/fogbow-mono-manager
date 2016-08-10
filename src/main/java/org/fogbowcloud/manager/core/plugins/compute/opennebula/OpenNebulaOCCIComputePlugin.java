package org.fogbowcloud.manager.core.plugins.compute.opennebula;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.compute.occi.OCCIComputePlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
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

public class OpenNebulaOCCIComputePlugin extends OCCIComputePlugin {

	public static final String DEFAULT_FOGBOW_NAME = "Fogbow-One";
	public static final String DEFAULT_CORE_ID = "fogbow_core_id";
	private static final String PUBLIC_KEY_TERM = "public_key";
	private static final String PUBLIC_KEY_SCHEME = "http://schemas.openstack.org/instance/credentials#";
	private static final String NAME_PUBLIC_KEY_ATTRIBUTE = "org.openstack.credentials.publickey.name";
	private static final String DATA_PUBLIC_KEY_ATTRIBUTE = "org.openstack.credentials.publickey.data";	
	private OpenNebulaComputePlugin openNebulaComputePlugin;
	
	public OpenNebulaOCCIComputePlugin(Properties properties) {
		super(properties);		
		setFlavorsProvided(properties);

		this.openNebulaComputePlugin = new OpenNebulaComputePlugin(properties);
		super.fogTermToCategory.put(OrderConstants.PUBLIC_KEY_TERM, new Category(PUBLIC_KEY_TERM,
				PUBLIC_KEY_SCHEME, OrderConstants.MIXIN_CLASS));
	}

	protected Set<Header> getExtraHeaders(List<Category> orderCategories,
			Map<String, String> xOCCIAtt, Token token) {
		
		HashSet<Header> headers = new HashSet<Header>();
		List<Category> occiCategories = new ArrayList<Category>();
		
		String headerShhPublic = "";
		for (Category category : orderCategories) {			
			occiCategories.add(super.fogTermToCategory.get(category.getTerm()));
		
			// adding ssh public key
			if (category.getTerm().equals(OrderConstants.PUBLIC_KEY_TERM)) {
				headerShhPublic += NAME_PUBLIC_KEY_ATTRIBUTE + "=" + "\"public_key_one\",";
				headerShhPublic += DATA_PUBLIC_KEY_ATTRIBUTE + "=\""
						+ xOCCIAtt.get(OrderAttribute.DATA_PUBLIC_KEY.getValue()) + "\"";
			}
		}
		
		String headerAttribute = "occi.core.id=\"" + DEFAULT_CORE_ID + "\"" + ",occi.core.title=\""
				+ DEFAULT_FOGBOW_NAME + "\"," + "occi.compute.hostname=\"" + DEFAULT_FOGBOW_NAME
				+ "\"";
		
		if (!headerShhPublic.isEmpty()) {
			headerAttribute += "," + headerShhPublic;
		}
		
		String userdataBase64 = xOCCIAtt.get(OrderAttribute.USER_DATA_ATT.getValue());		
		if (userdataBase64 != null) {
			headerAttribute += ",org.openstack.compute.user_data=\"" + userdataBase64 + "\"";					
		}
		
		headers.add(new BasicHeader(OCCIHeaders.AUTHORIZATION,
				getAuthorization(token.getAccessId())));		
		headers.add(new BasicHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, headerAttribute.substring(0,
				headerAttribute.length())));		
		headers.add(new BasicHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE));
		headers.add(new BasicHeader(OCCIHeaders.ACCEPT, OCCIHeaders.OCCI_ACCEPT));
		
		return headers;
	}
	
	@Override
	public Instance getInstance(Token token, String instanceId) {
		Set<Header> addicionalHeaders = new HashSet<Header>();
		addicionalHeaders.add(new BasicHeader(OCCIHeaders.AUTHORIZATION,
				getAuthorization(token.getAccessId())));
		addicionalHeaders.add(new BasicHeader(OCCIHeaders.ACCEPT,
				OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		String responseStr = doRequest("get", computeOCCIEndpoint + instanceId,
				token.getAccessId(), addicionalHeaders).getResponseString();

		return Instance.parseInstance(instanceId, responseStr);
	}

	@Override
	public List<Instance> getInstances(Token token) {
		Set<Header> addicionalHeaders = new HashSet<Header>();
		addicionalHeaders.add(new BasicHeader(OCCIHeaders.AUTHORIZATION,
				getAuthorization(token.getAccessId())));
		addicionalHeaders.add(new BasicHeader(OCCIHeaders.ACCEPT,
				OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		String responseStr = doRequest("get", computeOCCIEndpoint, token.getAccessId(),
				addicionalHeaders).getResponseString();

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
		Set<Header> addicionalHeaders = new HashSet<Header>();
		addicionalHeaders.add(new BasicHeader(OCCIHeaders.AUTHORIZATION,
				getAuthorization(token.getAccessId())));
		addicionalHeaders.add(new BasicHeader(OCCIHeaders.ACCEPT,
				OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		doRequest("delete", computeOCCIEndpoint, token.getAccessId(), addicionalHeaders);
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		Set<Header> addicionalHeaders = new HashSet<Header>();
		addicionalHeaders.add(new BasicHeader(OCCIHeaders.AUTHORIZATION,
				getAuthorization(token.getAccessId())));
		addicionalHeaders.add(new BasicHeader(OCCIHeaders.ACCEPT,
				OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		doRequest("delete", computeOCCIEndpoint + instanceId, token.getAccessId(),
				addicionalHeaders);
	}

	protected String getAuthorization(String token) {
		if (token.contains("Basic")) {
			return token;
		}	
		String newTokenAuthorization = new String(Base64.encodeBase64(token.getBytes()));
		newTokenAuthorization = "Basic " + newTokenAuthorization;
		return newTokenAuthorization;
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
			Series<org.restlet.data.Header> requestHeaders = (Series<org.restlet.data.Header>) request
					.getAttributes().get("org.restlet.http.headers");
			String token = "";
			for (org.restlet.data.Header header : requestHeaders) {
				if (header.getName().contains(OCCIHeaders.ACCEPT)) {
					try {
						String headerAccept = header.getValue();
						ClientInfo clientInfo = null;
						if (headerAccept.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
							clientInfo = new ClientInfo(MediaType.TEXT_PLAIN);
						} else if ((headerAccept.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE))) {
							requestHeaders.removeAll(OCCIHeaders.ACCEPT);
							requestHeaders.add(new org.restlet.data.Header(
									OCCIHeaders.ACCEPT, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
							clientInfo = new ClientInfo(MediaType.TEXT_PLAIN);
						} else {
							clientInfo = new ClientInfo(new MediaType(headerAccept));
						}
						proxiedRequest.setClientInfo(clientInfo);
					} catch (Exception e) {
					}
				} else if (header.getName().contains(
						HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN))
						|| header.getName().contains(OCCIHeaders.X_AUTH_TOKEN)) {
					token = header.getValue();
				}
			}

			if (requestHeaders.getFirst(OCCIHeaders.AUTHORIZATION) != null
					&& !requestHeaders.getFirstValue("Authorization").contains("Basic ")) {
				requestHeaders.removeAll(OCCIHeaders.AUTHORIZATION);
				requestHeaders.add(new org.restlet.data.Header(OCCIHeaders.AUTHORIZATION,
						getAuthorization(token)));
				requestHeaders.removeAll(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN));
			} else if (requestHeaders.getFirst("X-auth-token") != null
					|| requestHeaders.getFirst("X-Auth-Token") != null) {
				requestHeaders.add(new org.restlet.data.Header(OCCIHeaders.AUTHORIZATION,
						getAuthorization(token)));
				requestHeaders.removeAll(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN));
			}

			if (requestHeaders.getFirst(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE)) == null) {
				requestHeaders.add(new org.restlet.data.Header(HeaderUtils
						.normalize(OCCIHeaders.CONTENT_TYPE), OCCIHeaders.OCCI_CONTENT_TYPE));
				requestHeaders.add(new org.restlet.data.Header(OCCIHeaders.CONTENT_TYPE,
						OCCIHeaders.OCCI_CONTENT_TYPE));
			}

			proxiedRequest.getAttributes().put("org.restlet.http.headers", requestHeaders);
			clienteForBypass.handle(proxiedRequest, response);
		} catch (URISyntaxException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
	}
	
	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		return openNebulaComputePlugin.getResourcesInfo(token);
	}
	
	protected void setClient(HttpClient client) {
		super.setClient(client);;
	}
}
