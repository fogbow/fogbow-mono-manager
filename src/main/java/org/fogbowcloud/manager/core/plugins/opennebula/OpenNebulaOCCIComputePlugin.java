package org.fogbowcloud.manager.core.plugins.opennebula;

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
import org.apache.http.message.BasicHeader;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.occi.OCCIComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
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
		super.fogTermToCategory.put(RequestConstants.PUBLIC_KEY_TERM, new Category(PUBLIC_KEY_TERM,
				PUBLIC_KEY_SCHEME, RequestConstants.MIXIN_CLASS));
	}

	protected Set<Header> getExtraHeaders(List<Category> requestCategories,
			Map<String, String> xOCCIAtt, Token token) {
		
		HashSet<Header> headers = new HashSet<Header>();
		List<Category> occiCategories = new ArrayList<Category>();
		
		String headerShhPublic = "";
		for (Category category : requestCategories) {			
			occiCategories.add(super.fogTermToCategory.get(category.getTerm()));
		
			// adding ssh public key
			if (category.getTerm().equals(RequestConstants.PUBLIC_KEY_TERM)) {
				headerShhPublic += NAME_PUBLIC_KEY_ATTRIBUTE + "=" + "\"public_key_one\",";
				headerShhPublic += DATA_PUBLIC_KEY_ATTRIBUTE + "=\""
						+ xOCCIAtt.get(RequestAttribute.DATA_PUBLIC_KEY.getValue()) + "\"";
			}
		}
		
		String headerAttribute = "occi.core.id=\"" + DEFAULT_CORE_ID + "\"" + ",occi.core.title=\""
				+ DEFAULT_FOGBOW_NAME + "\"," + "occi.compute.hostname=\"" + DEFAULT_FOGBOW_NAME
				+ "\"";
		
		if (!headerShhPublic.isEmpty()) {
			headerAttribute += "," + headerShhPublic;
		}
		
		String userdataBase64 = xOCCIAtt.get(RequestAttribute.USER_DATA_ATT.getValue());		
		if (userdataBase64 != null) {
			userdataBase64 = OpenNebulaComputePlugin.normalizeUserdata(userdataBase64);
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

	public String getAuthorization(String token) {
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
			Series<org.restlet.engine.header.Header> requestHeaders = (Series<org.restlet.engine.header.Header>) request
					.getAttributes().get("org.restlet.http.headers");
			String token = "";
			for (org.restlet.engine.header.Header header : requestHeaders) {
				if (header.getName().contains(OCCIHeaders.ACCEPT)) {
					try {
						String headerAccept = header.getValue();
						ClientInfo clientInfo = null;
						if (headerAccept.contains(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE)) {
							clientInfo = new ClientInfo(MediaType.TEXT_PLAIN);
						} else if ((headerAccept.contains(OCCIHeaders.TEXT_URI_LIST_CONTENT_TYPE))) {
							requestHeaders.removeAll(OCCIHeaders.ACCEPT);
							requestHeaders.add(new org.restlet.engine.header.Header(
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
				requestHeaders.add(new org.restlet.engine.header.Header(OCCIHeaders.AUTHORIZATION,
						getAuthorization(token)));
				requestHeaders.removeAll(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN));
			} else if (requestHeaders.getFirst("X-auth-token") != null
					|| requestHeaders.getFirst("X-Auth-Token") != null) {
				requestHeaders.add(new org.restlet.engine.header.Header(OCCIHeaders.AUTHORIZATION,
						getAuthorization(token)));
				requestHeaders.removeAll(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN));
			}

			if (requestHeaders.getFirst(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE)) == null) {
				requestHeaders.add(new org.restlet.engine.header.Header(HeaderUtils
						.normalize(OCCIHeaders.CONTENT_TYPE), OCCIHeaders.OCCI_CONTENT_TYPE));
				requestHeaders.add(new org.restlet.engine.header.Header(OCCIHeaders.CONTENT_TYPE,
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
}
