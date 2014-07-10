package org.fogbowcloud.manager.occi.core;

import java.util.List;

import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestResourceRepository {

	Series<Header> headers;

	@Before
	public void setup() throws Exception {
		headers = new Series<Header>(Header.class);
		ResourceRepository.getInstance().reset();
	}

	@Test
	public void testGetOneResource() {
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		List<Category> categories = HeaderUtils.getCategories(headers);
		List<Resource> resources = ResourceRepository.getInstance().get(categories);
		Assert.assertEquals(1, resources.size());
	}

	@Test
	public void testGetSpecificResource() {
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		Resource resourceEquals = ResourceRepository.getInstance().get(RequestConstants.TERM);

		Assert.assertTrue(category.equals(resourceEquals.getCategory()));
	}
	
	@Test
	public void testGetUnknownSpecificResource() {
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		Resource resourceEquals = ResourceRepository.getInstance().get("unknown");

		Assert.assertNull(resourceEquals);
	}
	
	@Test
	public void testAddImageToResources() {
		int numberOfResources = ResourceRepository.getInstance().getAll().size();
		ResourceRepository.getInstance().addImageResource("image1");		
		List<Resource> resources = ResourceRepository.getInstance().getAll();
		Assert.assertEquals(numberOfResources + 1, resources.size());
		Assert.assertTrue(ResourceRepository.getInstance().get("image1") != null);
	}
}
