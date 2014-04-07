package org.fogbowcloud.manager.occi.core;

import java.util.List;

import org.fogbowcloud.manager.occi.RequestHelper;
import org.fogbowcloud.manager.occi.RequestResource;
import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestRequestResource {

	Series<Header> headers;

	@Before
	public void setup() throws Exception {
		headers = new Series<Header>(Header.class);
	}

	@Test
	public void testGetRequestResources() {
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE),
				RequestHelper.CONTENT_TYPE_OCCI);
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + "=10");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.TYPE.getValue() + "=\"one-time\"");

		List<FogbowResource> fogbowResources = RequestResource.getRequestResources(headers);
		Assert.assertEquals(1, fogbowResources.size());
	}

	@Test(expected = OCCIException.class)
	public void testGetRequestResourcesWrongAttribute() {
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "worng-attribute" + "=10");

		List<FogbowResource> fogbowResources = RequestResource.getRequestResources(headers);
		Assert.assertEquals(1, fogbowResources.size());
	}

	@Test(expected = OCCIException.class)
	public void testGetRequestResourcesWrongCategory() {
		Category category = new Category("wrong-term", FogbowResourceConstants.SCHEME,
				OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		List<FogbowResource> fogbowResources = RequestResource.getRequestResources(headers);
		Assert.assertEquals(1, fogbowResources.size());
	}
	
	@Test(expected = OCCIException.class)
	public void testGetRequestResourcesWithoutCategory() {
		List<FogbowResource> fogbowResources = RequestResource.getRequestResources(headers);
	}

}
