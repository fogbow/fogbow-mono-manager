package org.fogbowcloud.manager.occi.plugins.opennebula;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.plugins.opennebula.OpenNebulaOCCIComputePlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackConfigurationConstants;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.OCCIComputeApplication;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public class TestComputeOpenNebulaOCCI {

	private static final String PUBLIC_KEY = "public-key";
	private static final String UUID_CIRROS0_3_2_1 = "uuid_cirros0_3_2_1";
	private static final String DEFAULT_URL = "http://localhost:01234/";
	private static final String COMPUTE_TERM = "compute/";
	private static final String ACCESS_TOKEN_ID = "GYUEGHJCBDUycbINU4T238";
	private OpenNebulaOCCIComputePlugin openNebulaOCCIComputePlugin;
	private HttpUriRequestMatcher expectedRequest;
	private HttpClient httpClient;
	
	@Before
	public void setUp() throws HttpException, IOException {
		Properties properties = new Properties();
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY, OCCIComputeApplication.INSTANCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY, OCCIComputeApplication.OS_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY, OCCIComputeApplication.RESOURCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY, OCCIComputeApplication.SMALL_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + PluginHelper.LINUX_X86_TERM, PluginHelper.CIRROS_IMAGE_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + PluginHelper.LINUX_X86_TERM, PluginHelper.CIRROS_IMAGE_TERM);
		properties.put("compute_one_network_id", "5");
		properties.put("compute_one_image_123", "123");
		properties.put("compute_one_flavor_small", "{mem=128, cpu=1}");
		properties.put("compute_one_flavor_medium", "{mem=128, cpu=1}");
		properties.put("compute_one_flavor_large", "{mem=128, cpu=1}");
		properties.put("compute_occi_url", DEFAULT_URL);
		properties.put("compute_occi_template_scheme", "http://occi.localhost/occi/infrastructure/os_tpl#");
		properties.put("compute_occi_template_name_fogbow-linux-x86", UUID_CIRROS0_3_2_1);
		
		openNebulaOCCIComputePlugin = new OpenNebulaOCCIComputePlugin(properties);
		httpClient = Mockito.mock(HttpClient.class);
		HttpResponseFactory factory = new DefaultHttpResponseFactory();
		HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
				HttpStatus.SC_OK, "Return Irrelevant"), null);

		Mockito.when(httpClient.execute(Mockito.any(HttpUriRequest.class))).thenReturn(response);

		openNebulaOCCIComputePlugin.setClient(httpClient);
	}
	
	@Test
	public void testRequestInstance() throws HttpException, IOException, URISyntaxException {		
		List<Category> requestCategories = new ArrayList<Category>();
		requestCategories.add(new Category("fogbow_small", RequestConstants.TEMPLATE_RESOURCE_SCHEME, "mixin"));
		requestCategories.add(new Category("fogbow-linux-x86", RequestConstants.TEMPLATE_OS_SCHEME, "mixin"));
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		
		HttpUriRequest request = new HttpPost(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader("Authorization",
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN_ID);
		request.addHeader("Category", UUID_CIRROS0_3_2_1);
		request.addHeader("X-OCCI-Attribute", OpenNebulaOCCIComputePlugin.DEFAULT_CORE_ID);
		request.addHeader("X-OCCI-Attribute", OpenNebulaOCCIComputePlugin.DEFAULT_FOGBOW_NAME);
		
		expectedRequest = new HttpUriRequestMatcher(request);		
		
		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.requestInstance(token, requestCategories, xOCCIAtt);			
		} catch (Exception e) {}
		
		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));			
	}
	
	@Test
	public void testRequestInstanceWithinPublicKey() throws HttpException, IOException, URISyntaxException {		
		List<Category> requestCategories = new ArrayList<Category>();
		requestCategories.add(new Category("fogbow_small", RequestConstants.TEMPLATE_RESOURCE_SCHEME, "mixin"));
		requestCategories.add(new Category("fogbow-linux-x86", RequestConstants.TEMPLATE_OS_SCHEME, "mixin"));
		requestCategories.add(new Category(RequestConstants.PUBLIC_KEY_TERM
				+ "; scheme=\"" + RequestConstants.CREDENTIALS_RESOURCE_SCHEME
				+ "\"; class=\"" + RequestConstants.MIXIN_CLASS + "\""));
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.DATA_PUBLIC_KEY.getValue(), "=" + PUBLIC_KEY);
		
		HttpUriRequest request = new HttpPost(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader("Authorization",
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN_ID);
		request.addHeader("Category", UUID_CIRROS0_3_2_1);
		request.addHeader("Category", "public_key");
		request.addHeader("X-OCCI-Attribute", OpenNebulaOCCIComputePlugin.DEFAULT_CORE_ID);
		request.addHeader("X-OCCI-Attribute", OpenNebulaOCCIComputePlugin.DEFAULT_FOGBOW_NAME);
		request.addHeader("X-OCCI-Attribute", PUBLIC_KEY);
		
		expectedRequest = new HttpUriRequestMatcher(request);		
		
		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.requestInstance(token, requestCategories, xOCCIAtt);			
		} catch (Exception e) {}
		
		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));			
	}	
	
	@Test
	public void testGetInstance() throws HttpException, IOException, URISyntaxException {		
		String instanceId = "instanceid";
		HttpUriRequest request = new HttpGet(DEFAULT_URL + "/" + COMPUTE_TERM + instanceId);
		request.addHeader("Authorization",
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN_ID);
		expectedRequest = new HttpUriRequestMatcher(request);		
		
		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.getInstance(token, instanceId);			
		} catch (Exception e) {}
		
		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));			
	}
	
	@Test
	public void testRemoveInstances() throws HttpException, IOException, URISyntaxException {		
		HttpUriRequest request = new HttpDelete(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader("Authorization",
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN_ID);
		expectedRequest = new HttpUriRequestMatcher(request);		
		
		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.removeInstances(token);			
		} catch (Exception e) {}
		
		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));			
	}	
	
	@Test
	public void testRemoveInstance() throws HttpException, IOException, URISyntaxException {		
		String instanceId = "instanceid";
		HttpUriRequest request = new HttpDelete(DEFAULT_URL + "/" + COMPUTE_TERM + instanceId);
		request.addHeader("Authorization",
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN_ID);
		expectedRequest = new HttpUriRequestMatcher(request);		
		
		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.removeInstance(token, instanceId);			
		} catch (Exception e) {}
		
		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));			
	}	
	
	@Test
	public void testGetInstances() throws HttpException, IOException, URISyntaxException {		
		HttpUriRequest request = new HttpGet(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader("Authorization",
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_AUTH_TOKEN, ACCESS_TOKEN_ID);
		expectedRequest = new HttpUriRequestMatcher(request);		
		
		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.getInstances(token);			
		} catch (Exception e) {}
		
		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));			
	}	
	
	private class HttpUriRequestMatcher extends ArgumentMatcher<HttpUriRequest> {

		private HttpUriRequest request;

		public HttpUriRequestMatcher(HttpUriRequest request) {
			this.request = request;
		}

		public boolean matches(Object object) {
			HttpUriRequest comparedRequest = (HttpUriRequest) object;
			if (!this.request.getURI().equals(comparedRequest.getURI())) {
				return false;
			}
			if (!checkHeaders(comparedRequest.getAllHeaders())) {
				return false;
			}
			if (!this.request.getMethod().equals(comparedRequest.getMethod())) {
				System.out.println(this.request.getMethod());
				return false;
			}
			return true;
		}
		
		public boolean checkHeaders(Header[] comparedHeaders) {
			boolean headerEquals = true;
			for (Header headerComp : comparedHeaders) {
				if (headerComp.getName().equals("Category")) {
					for (Header header : this.request.getAllHeaders()) {
						if (header.getName().equals("Category")
								&& !headerComp.getValue().contains(header.getValue())) {
							headerEquals = false;
						}
					}
				} else if (headerComp.getName().equals("X-OCCI-Attribute")) {
					for (Header header : this.request.getAllHeaders()) {
						if (header.getName().equals("X-OCCI-Attribute")
								&& !headerComp.getValue().contains(header.getValue())) {
							headerEquals = false;
						}
					}
				} else {
					for (Header header : this.request.getAllHeaders()) {
						if (headerComp.getName().equals(header.getName())
								&& !headerComp.getValue().equals(header.getValue())){
							headerEquals = false;
						}
					}					
				}
			}
					
			return headerEquals;
		}
	}
}
