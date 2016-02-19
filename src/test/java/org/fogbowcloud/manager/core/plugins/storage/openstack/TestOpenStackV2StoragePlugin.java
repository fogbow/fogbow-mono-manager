package org.fogbowcloud.manager.core.plugins.storage.openstack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import com.google.common.base.Charsets;

public class TestOpenStackV2StoragePlugin {

	private static final String STORAGE_URL = "http://localhost:0000";
	private static final String SIZE = "2";
	private static final String ACCESS_ID = "accessId";
	private static final String TENANT_ID = "tenantId";
	
	private HttpClient client;
	private HttpUriRequestMatcher expectedRequest;
	private OpenStackV2StoragePlugin openStackV2StoragePlugin;
	private Token tokenDefault; 
	
	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put(OpenStackV2StoragePlugin.STORAGE_NOVAV2_URL_KEY, STORAGE_URL);
		openStackV2StoragePlugin = new OpenStackV2StoragePlugin(properties);
		
		client = Mockito.mock(HttpClient.class);
		HttpResponseFactory factory = new DefaultHttpResponseFactory();
		HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
				HttpStatus.SC_NO_CONTENT, "Return Irrelevant"), null);
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(response);
		openStackV2StoragePlugin.setClient(client);
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(OpenStackV2StoragePlugin.TENANT_ID, TENANT_ID);
		tokenDefault = new Token(ACCESS_ID, "user", new Date(), attributes);
	}
	
	@Test
	public void testRequestInstance() throws Exception {
		HttpUriRequest request = new HttpPost(STORAGE_URL + OpenStackV2StoragePlugin.COMPUTE_V2_API_ENDPOINT
				+ TENANT_ID + OpenStackV2StoragePlugin.SUFIX_ENDPOINT_VOLUMES);
		request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
		request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, tokenDefault.getAccessId());				
		expectedRequest = new HttpUriRequestMatcher(request, openStackV2StoragePlugin
				.generateJsonEntityToRequest(SIZE).toString());	
				
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), SIZE);
		try {
			openStackV2StoragePlugin.requestInstance(tokenDefault, null, xOCCIAtt);			
		} catch (Exception e) {
		}
		
		Mockito.verify(client).execute(Mockito.argThat(expectedRequest));
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceWithoutTenantId() throws Exception {
		tokenDefault.getAttributes().clear();
		openStackV2StoragePlugin.requestInstance(tokenDefault, null, null);			
	}	
	
	@Test
	public void testGenerateJsonEntityToRequest() throws JSONException {
		JSONObject jsonEntity = openStackV2StoragePlugin.generateJsonEntityToRequest(SIZE);
		Assert.assertEquals(SIZE, jsonEntity.getJSONObject(OpenStackV2StoragePlugin.KEY_JSON_VOLUME)
				.getString(OpenStackV2StoragePlugin.KEY_JSON_SIZE));
	}
	
	@Test
	public void testGetInstanceFromJson() throws JSONException {
		String instanceId = "instanceId";
		Instance instance = openStackV2StoragePlugin.getInstanceFromJson(
				generateInstanceJsonResponse(instanceId).toString());
		Assert.assertEquals(instanceId, instance.getId());
	}
	
	@Test
	public void testGetInstance() throws Exception {
		String instanceId = "intanceId";
		
		HttpUriRequest request = new HttpGet(STORAGE_URL + OpenStackV2StoragePlugin.COMPUTE_V2_API_ENDPOINT
				+ TENANT_ID + OpenStackV2StoragePlugin.SUFIX_ENDPOINT_VOLUMES + "/" + instanceId);
		request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
		request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, tokenDefault.getAccessId());			
		expectedRequest = new HttpUriRequestMatcher(request, null);	
		
		try {
			openStackV2StoragePlugin.getInstance(tokenDefault, instanceId);			
		} catch (Exception e) {
		}
		
		Mockito.verify(client).execute(Mockito.argThat(expectedRequest));
	}
	
	@Test(expected=OCCIException.class)
	public void testGetInstanceWithoutTenantId() throws Exception {
		tokenDefault.getAttributes().clear();
		openStackV2StoragePlugin.getInstance(tokenDefault, "instanceId");			
	}	
	
	@Test
	public void testGetInstances() throws Exception {
		HttpUriRequest request = new HttpGet(STORAGE_URL + OpenStackV2StoragePlugin.COMPUTE_V2_API_ENDPOINT
				+ TENANT_ID + OpenStackV2StoragePlugin.SUFIX_ENDPOINT_VOLUMES);
		request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
		request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, tokenDefault.getAccessId());			
		expectedRequest = new HttpUriRequestMatcher(request, null);	
		
		try {
			openStackV2StoragePlugin.getInstances(tokenDefault);			
		} catch (Exception e) {
		}
		
		Mockito.verify(client).execute(Mockito.argThat(expectedRequest));
	}		
	
	@Test(expected=OCCIException.class)
	public void testGetInstancesWithoutTenantId() throws Exception {
		tokenDefault.getAttributes().clear();
		openStackV2StoragePlugin.getInstances(tokenDefault);
	}	
	
	@Test
	public void testGetInstancesFromJson() throws JSONException {
		List<Instance> instances = new ArrayList<Instance>();
		instances.add(new Instance("one"));
		instances.add(new Instance("two"));
		instances.add(new Instance("three"));
		List<Instance> instancesFromJson = openStackV2StoragePlugin.getInstancesFromJson(
				generateInstancesJsonResponse(instances).toString());
		
		for (Instance instance : instancesFromJson) {
			boolean t = false;
			for (Instance instanceFromJson : instancesFromJson) {
				if (instance.getId().equals(instanceFromJson.getId())) {
					t = true;
					break;
				}
			} 
			if (t == false) {
				Assert.fail();				
			}
		}
	}
	
	@Test
	public void testRemoveInstance() throws Exception {
		String instanceId = "intanceId";
		
		HttpUriRequest request = new HttpDelete(STORAGE_URL + OpenStackV2StoragePlugin.COMPUTE_V2_API_ENDPOINT
				+ TENANT_ID + OpenStackV2StoragePlugin.SUFIX_ENDPOINT_VOLUMES + "/" + instanceId);
		request.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.JSON_CONTENT_TYPE);
		request.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.JSON_CONTENT_TYPE);
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, tokenDefault.getAccessId());			
		expectedRequest = new HttpUriRequestMatcher(request, null);	
		
		try {
			openStackV2StoragePlugin.removeInstance(tokenDefault, instanceId);			
		} catch (Exception e) {
		}
		
		Mockito.verify(client).execute(Mockito.argThat(expectedRequest));
	}
	
	@Test
	public void testRemoveInstances() throws Exception {
		List<Instance> instances = new ArrayList<Instance>();
		instances.add(new Instance("one"));
		instances.add(new Instance("two"));
		instances.add(new Instance("three"));		
		
		HttpResponse response = Mockito.mock(HttpResponse.class);
		Mockito.when(response.getStatusLine()).thenReturn(
				new BasicStatusLine(new ProtocolVersion("", 1, 1), 1, ""));
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class)))
				.thenReturn(response);
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		Mockito.when(response.getEntity()).thenReturn(httpEntity);
		InputStream value = new ByteArrayInputStream(
				generateInstancesJsonResponse(instances).toString().getBytes());
		Mockito.when(httpEntity.getContent()).thenReturn(value);
		Mockito.when(httpEntity.getContentLength()).thenReturn(2L);
		
		expectedRequest = new HttpUriRequestMatcher();	
		
		openStackV2StoragePlugin.removeInstances(tokenDefault);
		Mockito.verify(client, Mockito.times(4)).execute(Mockito.argThat(expectedRequest));
	}	
	
	@Test(expected=OCCIException.class)
	public void testRemoveInstanceWithoutTenantId() throws Exception {
		tokenDefault.getAttributes().clear();
		openStackV2StoragePlugin.removeInstance(tokenDefault, "instanceId");			
	}		
	
	private JSONObject generateInstancesJsonResponse(List<Instance> instances) throws JSONException {
		JSONArray instancesArray = new JSONArray();
		for (Instance instance : instances) {
			JSONObject instanceIdJson = new JSONObject();
			instanceIdJson.put(OpenStackV2StoragePlugin.KEY_JSON_ID, instance.getId());
			
			instancesArray.put(instanceIdJson);
		}
		return new JSONObject().put(OpenStackV2StoragePlugin.KEY_JSON_VOLUMES, instancesArray);
	}
	
	private JSONObject generateInstanceJsonResponse(String instanceId) throws JSONException {
		JSONObject jsonId = new JSONObject();
		jsonId.put(OpenStackV2StoragePlugin.KEY_JSON_ID, instanceId);
		JSONObject jsonStorage = new JSONObject();
		jsonStorage.put(OpenStackV2StoragePlugin.KEY_JSON_VOLUME, jsonId);
		return jsonStorage;
	}
	
	private class HttpUriRequestMatcher extends ArgumentMatcher<HttpUriRequest> {

		private HttpUriRequest request;
		private String entityStrCompare;
		private boolean doNotCheck;

		public HttpUriRequestMatcher() {
			this.doNotCheck = true;
		}
		
		public HttpUriRequestMatcher(Object request, String entityStrCompare) {
			this.request = (HttpUriRequest) request;
			this.entityStrCompare = entityStrCompare;
		}

		public boolean matches(Object object) {
			if (doNotCheck) {
				return true;
			}
			
			HttpUriRequest comparedRequest = null;
			comparedRequest = (HttpUriRequest) object;
			if (object instanceof HttpPost && entityStrCompare != null) {
				try {
					HttpEntityEnclosingRequestBase httpEntityEnclosingRequestBase = (HttpEntityEnclosingRequestBase) object;
					String entityStr = EntityUtils.toString(httpEntityEnclosingRequestBase.getEntity(), Charsets.UTF_8);
					if (!entityStrCompare.equals(entityStr)) {
						return false;
					}
				} catch (Exception e) {}
			}		
			if (!this.request.getURI().equals(comparedRequest.getURI())) {
				return false;
			}
			if (!checkHeaders(comparedRequest.getAllHeaders())) {
				return false;
			}
			if (!this.request.getMethod().equals(comparedRequest.getMethod())) {
				return false;
			}
			return true;
		}

		public boolean checkHeaders(Header[] comparedHeaders) {
			for (Header comparedHeader : comparedHeaders) {
				boolean headerEquals = false;
				for (Header header : this.request.getAllHeaders()) {
					if (header.getName().equals(OCCIHeaders.X_AUTH_TOKEN)) {
						if (header.getName().equals(comparedHeader.getName())) {
							headerEquals = true;
							break;
						}
					} else 
					if (header.getName().equals(comparedHeader.getName())
							&& header.getValue().equals(comparedHeader.getValue())) {
						headerEquals = true;
						continue;
					}
				}
				if (!headerEquals) {
					return false;
				}
			}
			return true;
		}
	}	
	
}
