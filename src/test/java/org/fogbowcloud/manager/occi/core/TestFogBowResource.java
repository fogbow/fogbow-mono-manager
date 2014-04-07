package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestFogBowResource {

	@Test
	public void validCategory() {
		new Resource("term", "scheme", "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryEmptyTerm() {
		new Resource("", "scheme", "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryNullTerm() {
		new Resource(null, "scheme", "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryEmptyScheme() {
		new Resource("term", "", "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryNullScheme() {
		new Resource("term", null, "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryEmptyClass() {
		new Resource("term", "scheme", "", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryNullClass() {
		new Resource("term", "scheme", null, null, null, null, null, null);
	}

	@Test
	public void testContainsAttribute() {
		List<String> attributes = new ArrayList<String>();
		attributes.add("attribute1");
		attributes.add("attribute2");
		attributes.add("attribute3");
		Resource resource = new Resource("term", "scheme", "class", attributes,
				null, null, null, null);

		Assert.assertTrue(resource.supportAtt("attribute1"));
	}

	public void testMatches() {
		Resource resource = new Resource("term", "scheme", "class", null, null,
				null, null, null);
		Category category = new Category("term", "scheme", "class");

		Assert.assertTrue(resource.matches(category));
	}

}
