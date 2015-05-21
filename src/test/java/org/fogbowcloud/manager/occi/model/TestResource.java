package org.fogbowcloud.manager.occi.model;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Resource;
import org.junit.Assert;
import org.junit.Test;

public class TestResource {

	@Test
	public void validResource() {
		new Resource("term", "scheme", "class", new ArrayList<String>(), new ArrayList<String>(),
				"location", "title", "rel");
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
	public void testToHeader() {
		List<String> attributes = new ArrayList<String>();
		attributes.add("attribute1");
		attributes.add("attribute2");
		List<String> actions = new ArrayList<String>();
		actions.add("action1");
		actions.add("action2");
		Resource resource = new Resource("term", "scheme", "class", attributes, actions,
				"location", "title", "rel");

		String testString = "term; scheme=\"scheme\"; class=\"class\"; title=\"title\"; "
				+ "rel=\"rel\"; location=\"location\"; "
				+ "attributes=\"attribute1 attribute2\"; actions=\"action1 action2\"";
		Assert.assertEquals(testString, resource.toHeader());
	}
	
	@Test
	public void testConstructorResourceStr(){
		String resourceStr = "compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"; "
				+ "title=\"Compute Resource\"; rel=\"http://schemas.ogf.org/occi/core#resource\"; "
				+ "location=\"http://localhost:8787/compute/\"; attributes=\"occi.compute.architecture occi.compute.state{immutable} "
				+ "occi.compute.speed occi.compute.memory occi.compute.cores occi.compute.hostname\"; "
				+ "actions=\"http://schemas.ogf.org/occi/infrastructure/compute/action#start "
				+ "http://schemas.ogf.org/occi/infrastructure/compute/action#stop "
				+ "http://schemas.ogf.org/occi/infrastructure/compute/action#restart "
				+ "http://schemas.ogf.org/occi/infrastructure/compute/action#suspend";
		Resource resource = new Resource(resourceStr);
		
		Assert.assertEquals(new Category("compute", "http://schemas.ogf.org/occi/infrastructure#",
				"kind"), resource.getCategory());
		Assert.assertEquals("Compute Resource", resource.getTitle());
		Assert.assertEquals("http://localhost:8787/compute/", resource.getLocation());
		Assert.assertEquals("http://schemas.ogf.org/occi/core#resource", resource.getRel());
		
		List<String> attributes = new ArrayList<String>();
		attributes.add("occi.compute.architecture");
		attributes.add("occi.compute.state{immutable}");
		attributes.add("occi.compute.speed");
		attributes.add("occi.compute.memory");
		attributes.add("occi.compute.cores");
		attributes.add("occi.compute.hostname");
		for (String attribute : attributes) {
			Assert.assertTrue(resource.getAttributes().contains(attribute));
		}
		
		List<String> actions = new ArrayList<String>();
		actions.add("http://schemas.ogf.org/occi/infrastructure/compute/action#start");
		actions.add("http://schemas.ogf.org/occi/infrastructure/compute/action#stop");
		actions.add("http://schemas.ogf.org/occi/infrastructure/compute/action#restart");
		actions.add("http://schemas.ogf.org/occi/infrastructure/compute/action#suspend");
		for (String action : actions) {
			Assert.assertTrue(resource.getActions().contains(action));
		}
	}

	@Test
	public void testContainsAttribute() {
		List<String> attributes = new ArrayList<String>();
		attributes.add("attribute1");
		attributes.add("attribute2");
		attributes.add("attribute3");
		Resource resource = new Resource("term", "scheme", "class", attributes, null, null, null,
				null);

		Assert.assertTrue(resource.supportAtt("attribute1"));
	}

	public void testMatches() {
		Resource resource = new Resource("term", "scheme", "class", null, null, null, null, null);
		Category category = new Category("term", "scheme", "class");

		Assert.assertTrue(resource.matches(category));
	}

}
