package org.fogbowcloud.manager.core.plugins.compute.opennebula;

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
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.plugins.compute.occi.OCCIComputePlugin;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenStackConfigurationConstants;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.OCCIComputeApplication;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public class TestComputeOpenNebulaOCCI {

	private static final String FOGBOW_LINUX_X86 = "fogbow-linux-x86-tlp";
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
		openNebulaOCCIComputePlugin = new OpenNebulaOCCIComputePlugin(getProperties());
		
		httpClient = Mockito.mock(HttpClient.class);
		HttpResponseFactory factory = new DefaultHttpResponseFactory();
		HttpResponse response = factory.newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1,
				HttpStatus.SC_OK, "Return Irrelevant"), null);

		Mockito.when(httpClient.execute(Mockito.any(HttpUriRequest.class))).thenReturn(response);

		openNebulaOCCIComputePlugin.setClient(httpClient);
	}

	private Properties getProperties() {
		Properties properties = new Properties();
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY,
				OCCIComputeApplication.INSTANCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY,
				OCCIComputeApplication.OS_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY,
				OCCIComputeApplication.RESOURCE_SCHEME);
		properties.put(OCCIComputePlugin.PREFIX_OCCI_FLAVORS_PROVIDED +
				OCCIComputeApplication.SMALL_FLAVOR_TERM, "{cpu=1,mem=1000,disk=10}");
		properties.put(OCCIComputePlugin.PREFIX_OCCI_FLAVORS_PROVIDED +
				OCCIComputeApplication.MEDIUM_FLAVOR_TERM, "{cpu=2,mem=2000,disk=20}");
		properties.put(OCCIComputePlugin.PREFIX_OCCI_FLAVORS_PROVIDED +
				OCCIComputeApplication.LARGE_FLAVOR_TERM, "{cpu=4,mem=4000,disk=30}");
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX
				+ PluginHelper.LINUX_X86_TERM, PluginHelper.CIRROS_IMAGE_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_TEMPLATE_SCHEME_KEY,
				"http://occi.localhost/occi/infrastructure/os_tpl#");
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_URL_KEY, DEFAULT_URL);

		properties.put(OneConfigurationConstants.COMPUTE_ONE_URL, DEFAULT_URL);
		properties.put(OneConfigurationConstants.COMPUTE_ONE_SMALL_KEY, "{mem=128, cpu=1}");
		properties.put(OneConfigurationConstants.COMPUTE_ONE_MEDIUM_KEY, "{mem=128, cpu=1}");
		properties.put(OneConfigurationConstants.COMPUTE_ONE_LARGE_KEY, "{mem=128, cpu=1}");
		properties.put(OneConfigurationConstants.COMPUTE_ONE_NETWORK_KEY, "5");
		properties.put(OneConfigurationConstants.COMPUTE_ONE_IMAGE_PREFIX_KEY + "image1", "image1");
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_TEMPLATE_PREFIX
				+ FOGBOW_LINUX_X86, UUID_CIRROS0_3_2_1);
		return properties;
	}

	@Test(expected=OCCIException.class)
	public void testThereIsNotFlavorSpecified() {
		Properties properties = new Properties();			
		new OpenNebulaOCCIComputePlugin(properties);			
	}
	
	@Test
	public void testRequestInstance() throws HttpException, IOException, URISyntaxException {
		HttpUriRequest request = new HttpPost(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader(OCCIHeaders.AUTHORIZATION,
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, ACCESS_TOKEN_ID);
		request.addHeader(OCCIHeaders.CATEGORY, UUID_CIRROS0_3_2_1);
		request.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OpenNebulaOCCIComputePlugin.DEFAULT_CORE_ID);
		request.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OpenNebulaOCCIComputePlugin.DEFAULT_FOGBOW_NAME);
		request.addHeader(OCCIHeaders.CATEGORY, OCCIComputeApplication.SMALL_FLAVOR_TERM
				+ "; scheme=\"" + OCCIComputeApplication.RESOURCE_SCHEME + "\"; class=\"mixin\"");
		expectedRequest = new HttpUriRequestMatcher(request);

		List<Category> requestCategories = new ArrayList<Category>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(RequestAttribute.REQUIREMENTS.getValue(), requirementsStr);

		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.requestInstance(token, requestCategories, xOCCIAtt,
					UUID_CIRROS0_3_2_1);
		} catch (Exception e) {
		}

		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));
	}

	@Test
	public void testRequestInstanceWithPublicKeyAndUserData() throws HttpException, IOException,
			URISyntaxException {
		String publicKey = "publicKey";
		HttpUriRequest request = new HttpPost(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader(OCCIHeaders.AUTHORIZATION,
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, ACCESS_TOKEN_ID);
		request.addHeader(OCCIHeaders.CATEGORY, UUID_CIRROS0_3_2_1);
		request.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OpenNebulaOCCIComputePlugin.DEFAULT_CORE_ID);
		request.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OpenNebulaOCCIComputePlugin.DEFAULT_FOGBOW_NAME);
		request.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, publicKey);
		request.addHeader(OCCIHeaders.CATEGORY, OCCIComputeApplication.SMALL_FLAVOR_TERM
				+ "; scheme=\"" + OCCIComputeApplication.RESOURCE_SCHEME + "\"; class=\"mixin\"");		
		expectedRequest = new HttpUriRequestMatcher(request);

		List<Category> requestCategories = new ArrayList<Category>();
		requestCategories.add(new Category(RequestConstants.PUBLIC_KEY_TERM,
				RequestConstants.CREDENTIALS_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.DATA_PUBLIC_KEY.getValue(), publicKey);
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(RequestAttribute.REQUIREMENTS.getValue(), requirementsStr);

		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.requestInstance(token, requestCategories, xOCCIAtt,
					UUID_CIRROS0_3_2_1);
		} catch (Exception e) {
		}

		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));
	}

	@Test
	public void testRequestInstanceWithinPublicKey() throws HttpException, IOException,
			URISyntaxException {

		List<Category> requestCategories = new ArrayList<Category>();
		requestCategories.add(new Category(RequestConstants.PUBLIC_KEY_TERM + "; scheme=\""
				+ RequestConstants.CREDENTIALS_RESOURCE_SCHEME + "\"; class=\""
				+ RequestConstants.MIXIN_CLASS + "\""));
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.DATA_PUBLIC_KEY.getValue(), "=" + PUBLIC_KEY);
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAtt.put(RequestAttribute.REQUIREMENTS.getValue(), requirementsStr);

		HttpUriRequest request = new HttpPost(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader(OCCIHeaders.AUTHORIZATION,
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, ACCESS_TOKEN_ID);
		request.addHeader(OCCIHeaders.CATEGORY, UUID_CIRROS0_3_2_1);		
		request.addHeader(OCCIHeaders.CATEGORY, "public_key");
		request.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OpenNebulaOCCIComputePlugin.DEFAULT_CORE_ID);
		request.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				OpenNebulaOCCIComputePlugin.DEFAULT_FOGBOW_NAME);
		request.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, PUBLIC_KEY);
		request.addHeader(OCCIHeaders.CATEGORY, OCCIComputeApplication.SMALL_FLAVOR_TERM
				+ "; scheme=\"" + OCCIComputeApplication.RESOURCE_SCHEME + "\"; class=\"mixin\"");
		
		expectedRequest = new HttpUriRequestMatcher(request);

		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {			
			openNebulaOCCIComputePlugin.requestInstance(token, requestCategories, xOCCIAtt,
					UUID_CIRROS0_3_2_1);
		} catch (Exception e) {
		}

		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));
	}

	@Test
	public void testGetInstance() throws HttpException, IOException, URISyntaxException {
		String instanceId = "instanceid";
		HttpUriRequest request = new HttpGet(DEFAULT_URL + "/" + COMPUTE_TERM + instanceId);
		request.addHeader(OCCIHeaders.AUTHORIZATION,
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, ACCESS_TOKEN_ID);
		expectedRequest = new HttpUriRequestMatcher(request);

		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.getInstance(token, instanceId);
		} catch (Exception e) {
		}

		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));
	}

	@Test
	public void testRemoveInstances() throws HttpException, IOException, URISyntaxException {
		HttpUriRequest request = new HttpDelete(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader(OCCIHeaders.AUTHORIZATION,
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, ACCESS_TOKEN_ID);
		expectedRequest = new HttpUriRequestMatcher(request);

		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.removeInstances(token);
		} catch (Exception e) {
		}

		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));
	}

	@Test
	public void testRemoveInstance() throws HttpException, IOException, URISyntaxException {
		String instanceId = "instanceid";
		HttpUriRequest request = new HttpDelete(DEFAULT_URL + "/" + COMPUTE_TERM + instanceId);
		request.addHeader(OCCIHeaders.AUTHORIZATION,
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, ACCESS_TOKEN_ID);
		expectedRequest = new HttpUriRequestMatcher(request);

		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.removeInstance(token, instanceId);
		} catch (Exception e) {
		}

		Mockito.verify(httpClient).execute(Mockito.argThat(expectedRequest));
	}

	@Test
	public void testGetInstances() throws HttpException, IOException, URISyntaxException {
		HttpUriRequest request = new HttpGet(DEFAULT_URL + "/" + COMPUTE_TERM);
		request.addHeader(OCCIHeaders.AUTHORIZATION,
				openNebulaOCCIComputePlugin.getAuthorization(ACCESS_TOKEN_ID));
		request.addHeader(OCCIHeaders.X_FEDERATION_AUTH_TOKEN, ACCESS_TOKEN_ID);
		expectedRequest = new HttpUriRequestMatcher(request);

		Token token = new Token(ACCESS_TOKEN_ID, "user", new Date(), new HashMap<String, String>());
		try {
			openNebulaOCCIComputePlugin.getInstances(token);
		} catch (Exception e) {
		}

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
				return false;
			}
			return true;
		}

		public boolean checkHeaders(Header[] comparedHeaders) {
			boolean headerEquals = true;
			for (Header headerComp : comparedHeaders) {
				if (headerComp.getName().equals(OCCIHeaders.CATEGORY)) {
					for (Header header : this.request.getAllHeaders()) {
						if (header.getName().equals(OCCIHeaders.CATEGORY)
								&& !headerComp.getValue().contains(header.getValue())) {
							headerEquals = false;
						}
					}
				} else if (headerComp.getName().equals(OCCIHeaders.X_OCCI_ATTRIBUTE)) {
					for (Header header : this.request.getAllHeaders()) {
						if (header.getName().equals(OCCIHeaders.X_OCCI_ATTRIBUTE)
								&& !headerComp.getValue().contains(header.getValue())) {
							headerEquals = false;
						}
					}
				} else {
					for (Header header : this.request.getAllHeaders()) {
						if (headerComp.getName().equals(header.getName())
								&& !headerComp.getValue().equals(header.getValue())) {
							headerEquals = false;
						}
					}
				}
			}

			return headerEquals;
		}
	}
}
