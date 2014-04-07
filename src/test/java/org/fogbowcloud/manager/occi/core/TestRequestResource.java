package org.fogbowcloud.manager.occi.core;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.RequestHelper;
import org.fogbowcloud.manager.occi.RequestServerResource;
import org.fogbowcloud.manager.occi.request.RequestConstants;
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
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE),
				RequestHelper.CONTENT_TYPE_OCCI);
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + "=10");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.TYPE.getValue() + "=\"one-time\"");

		List<Category> categories = HeaderUtils.getCategories(headers);
		List<Resource> resources = ResourceRepository.get(categories);
		Assert.assertEquals(1, resources.size());
	}

	@Test(expected = OCCIException.class)
	public void testGetRequestResourcesWrongAttribute() {
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "worng-attribute" + "=10");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testGetRequestResourcesWrongCategory() {
		Category category = new Category("wrong-term", RequestConstants.SCHEME,
				OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
		List<Resource> resources = ResourceRepository.get(categories);
	}
	
	@Test(expected = OCCIException.class)
	public void testGetRequestResourcesWithoutCategory() {		
		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
		List<Resource> resources = ResourceRepository.get(categories);
	}

}
