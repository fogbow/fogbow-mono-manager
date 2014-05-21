package org.fogbowcloud.manager.occi.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestInstance {

	private List<Resource> resources = new ArrayList<Resource>();
	private Map<String, String> attributes = new HashMap<String, String>();
	private Link link;

	@Before
	public void setUp() {
		Category category = new Category("compute", "http://compute", "kind");
		Resource resource = new Resource(category, new ArrayList<String>(),
				new ArrayList<String>(), "location", "title", "rel");
		List<String> actionsAndSupportedAttributes = new ArrayList<String>();
		actionsAndSupportedAttributes.add("value1");
		actionsAndSupportedAttributes.add("value2");
		Resource resource2 = new Resource(category, new ArrayList<String>(),
				new ArrayList<String>(), "location", "title", "rel");
		resources.add(resource);
		resources.add(resource2);

		attributes.put("org.openstack.compute.console.vnc", "N/A");
		attributes.put("occi.compute.architecture", "x86");
		attributes.put("occi.compute.speed", "0.0");

		link = new Link("</network/admin>", attributes);
	}

	@Test
	public void testParseIdOneValue() {
		String textResponse = "X-OCCI-Location: http://localhost:8787/compute/c1490";
		Instance instance = Instance.parseInstance(textResponse);

		Assert.assertEquals(textResponse, instance.toOCCIMassageFormatLocation());
	}

	@Test
	public void testParseDetails() {
		String textResponse = getFormatedResources();
		textResponse += "\n";
		textResponse += link.toOCCIMessageFormatLink() + "\n";
		textResponse += getFormatedAttributes();
		Instance instance = Instance.parseInstance("id", textResponse);

		Assert.assertEquals(textResponse, instance.toOCCIMassageFormatDetails());
	}

	@Test
	public void testParseDetailsOnlyAttributes() {
		String textResponse = getFormatedAttributes();
		Instance instance = Instance.parseInstance("id", textResponse);

		Assert.assertEquals(textResponse, instance.toOCCIMassageFormatDetails());
	}

	@Test
	public void testParseDetailsOnlyCategory() {
		String textResponse = getFormatedResources();
		Instance instance = Instance.parseInstance("id", textResponse);

		Assert.assertEquals(textResponse, instance.toOCCIMassageFormatDetails());
	}

	@Test
	public void addAttributes() {
		Instance instance = new Instance("");
		instance.addAttribute("key1", "value1");
		instance.addAttribute("key2", "value2");
		Assert.assertEquals(2, instance.getAttributes().size());
	}

	private String getFormatedResources() {
		String textResponse = "";
		for (Resource resource : resources) {
			textResponse += "Category: " + resource.toHeader() + "\n";
		}
		return textResponse = textResponse.trim();
	}

	private String getFormatedAttributes() {
		String textResponse = "";
		for (String key : attributes.keySet()) {
			textResponse += "X-OCCI-Attribute: " + key + "=\"" + attributes.get(key) + "\"\n";
		}
		return textResponse = textResponse.trim();
	}
}
