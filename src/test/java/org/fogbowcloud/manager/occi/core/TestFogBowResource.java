package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestFogBowResource {

	@Test
	public void validCategory() {
		new FogbowResource("term", "scheme", "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryEmptyTerm() {
		new FogbowResource("", "scheme", "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryNullTerm() {
		new FogbowResource(null, "scheme", "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryEmptyScheme() {
		new FogbowResource("term", "", "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryNullScheme() {
		new FogbowResource("term", null, "class", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryEmptyClass() {
		new FogbowResource("term", "scheme", "", null, null, null, null, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidCategoryNullClass() {
		new FogbowResource("term", "scheme", null, null, null, null, null, null);
	}

	@Test
	public void testContainsAttribute() {
		List<String> attributes = new ArrayList<String>();
		attributes.add("attribute1");
		attributes.add("attribute2");
		attributes.add("attribute3");
		FogbowResource fogbowResource = new FogbowResource("term", "scheme", "class", attributes,
				null, null, null, null);

		Assert.assertTrue(fogbowResource.supportAtt("attribute1"));
	}

	public void testMatches() {
		FogbowResource fogbowResource = new FogbowResource("term", "scheme", "class", null, null,
				null, null, null);
		Category category = new Category("term", "scheme", "class");

		Assert.assertTrue(fogbowResource.matches(category));
	}

}
