package org.fogbowcloud.manager.occi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestResourceRepository {

	private static final String SMALL = "fogbow_small";
	private static final String MEDIUM = "fogbow_medium";
	private static final String LARGE = "fogbow_large";
	private Series<Header> headers;
	private Properties properties = new Properties();

	@Before
	public void setup() throws Exception {
		headers = new Series<Header>(Header.class);

		properties.put(ConfigurationConstants.PREFIX_FLAVORS + SMALL, "{cpu=1,mem=1000}");
		properties.put(ConfigurationConstants.PREFIX_FLAVORS + MEDIUM, "{cpu=2,mem=2000}");
		properties.put(ConfigurationConstants.PREFIX_FLAVORS + LARGE, "{cpu=4,mem=4000}");
		
		ResourceRepository.getInstance().reset();
		ResourceRepository.init(properties);
	}
	
	@Test
	public void testGetOneResource() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		List<Category> categories = HeaderUtils.getCategories(headers);
		List<Resource> resources = ResourceRepository.getInstance().get(categories);
		Assert.assertEquals(1, resources.size());
	}

	@Test
	public void testGetSpecificResource() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		Resource resourceEquals = ResourceRepository.getInstance().get(OrderConstants.TERM);

		Assert.assertTrue(category.equals(resourceEquals.getCategory()));
	}
	
	@Test
	public void testGetUnknownSpecificResource() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
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
	
	@Test
	public void testGetAttValue() {
		String cpuValue = "2";
		String memValue = "10";
		String flavorSpec = "{cpu=" + cpuValue + ",mem=" + memValue + "}";
		Assert.assertEquals(cpuValue, ResourceRepository.getAttValue("cpu", flavorSpec));
		Assert.assertEquals(memValue, ResourceRepository.getAttValue("mem", flavorSpec));		
	}
	
	@Test
	public void testGetStaticFlavors() {
		List<Flavor> staticFlavors = ResourceRepository.getStaticFlavors(properties);
		
		Assert.assertEquals(3, staticFlavors.size());
	}	
	
	@Test
	public void testGenerateFlavorResource() {
		String flavorName = "flavor";
		Resource flavorResource = ResourceRepository.generateFlavorResource(flavorName);
		Assert.assertEquals(flavorName, flavorResource.getCategory().getTerm());
		Assert.assertEquals(OrderConstants.TEMPLATE_RESOURCE_SCHEME, flavorResource.getCategory().getScheme());
		Assert.assertEquals(OrderConstants.MIXIN_CLASS, flavorResource.getCategory().getCatClass());		
	}
	
	@Test
	public void testGenerateFlavorResourceNull() {
		String flavorName = null;
		Resource flavorResource = ResourceRepository.generateFlavorResource(flavorName);
		Assert.assertEquals(flavorName, flavorResource);		
	}	
	
	@Test
	public void testGetFlavorsInAllResources() {
		List<String> needToFind = new ArrayList<String>();
		needToFind.add(SMALL);
		needToFind.add(MEDIUM);
		needToFind.add(LARGE);
		
		ResourceRepository.getStaticFlavors(properties);
		List<Resource> allResources = ResourceRepository.getInstance().getAll();
		for (String flavor : needToFind) {
			boolean containsFlavor = false;
			for (Resource resource : allResources) {
				if (resource.getCategory().getTerm().equals(flavor)) {
					containsFlavor = true;
					break;
				}
			}
			if (!containsFlavor) {
				Assert.fail();
			}
		}
	}
	
	
	//TODO Add tests to getExtraOCCI Resource
}
