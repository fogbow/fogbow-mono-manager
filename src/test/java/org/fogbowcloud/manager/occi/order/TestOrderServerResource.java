package org.fogbowcloud.manager.occi.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderServerResource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestOrderServerResource {

	Series<Header> headers;

	@Before
	public void setup() throws Exception {
		headers = new Series<Header>(Header.class);
	}

	@Test
	public void testNormalizeXOCCIAtt() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=10");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);

		Assert.assertEquals("10", xOCCIAtt.get(OrderAttribute.INSTANCE_COUNT.getValue()));
	}

	@Test(expected = OCCIException.class)
	public void testNormalizeXOCCIAttWrongNameAttribute() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "worng-attribute" + "=10");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testCheckOrderType() {
		OrderServerResource.checkOrderType("one-time");
	}

	@Test(expected = OCCIException.class)
	public void testCheckOrderInvalidType() {
		OrderServerResource.checkOrderType("wrong");
	}

	@Test
	public void testValidAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + " = 6");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
		int instances = Integer.valueOf(xOCCIAtt.get(OrderAttribute.INSTANCE_COUNT.getValue()));

		Assert.assertEquals(6, instances);
	}

	@Test
	public void testValidAttributeInstacesValeuDefault() {
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
		int instances = Integer.valueOf(xOCCIAtt.get(OrderAttribute.INSTANCE_COUNT.getValue()));

		Assert.assertEquals(1, instances);
	}

	@Test(expected = OCCIException.class)
	public void testWrongAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + " = wrong");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testEmptyAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + " =");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testCheckAttributes() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.TYPE.getValue() + "=\"one-time\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesWrongType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.TYPE.getValue() + "=\"wrong\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testCheckAttributesDate() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.VALID_FROM.getValue() + "=\"2014-04-01\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesWrongDate() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.VALID_FROM.getValue() + "=\"wrong\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}
	
	@Test
	public void testNormalizeRequirementsNull() {
		Assert.assertNull(OrderServerResource.normalizeRequirements(new ArrayList<Category>(),
				new HashMap<String, String>(), new ArrayList<Flavor>()).get(
				OrderAttribute.REQUIREMENTS.getValue()));
	}
	
	@Test(expected=OCCIException.class)
	public void testNormalizeRequirementsWithAttributeRequirement() {
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		String requirements = RequirementsHelper.GLUE_VCPU_TERM + ">=1";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirements);
		Map<String, String> normalizeRequirements = OrderServerResource.normalizeRequirements(
				new ArrayList<Category>(), xOCCIAtt, new ArrayList<Flavor>());
		Assert.assertEquals(requirements,
				normalizeRequirements.get(OrderAttribute.REQUIREMENTS.getValue()));
		
		// Wrong requirements
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), "/wrongrequirements/");
		normalizeRequirements = OrderServerResource.normalizeRequirements(
				new ArrayList<Category>(), xOCCIAtt, new ArrayList<Flavor>());
	}

	@Test(expected=OCCIException.class)
	public void testNormalizeRequirementsWithCategoryRequirement() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = OrderServerResource.normalizeRequirements(
				categories, new HashMap<String, String>(), listFlavorsFogbow);
		Assert.assertEquals(RequirementsHelper.GLUE_MEM_RAM_TERM + ">=" + memMedium + "&&"
				+ RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium,
				normalizeRequirements.get(OrderAttribute.REQUIREMENTS.getValue()));
		
		// Wrong flavor name
		categories = new ArrayList<Category>();
		categories.add(new Category("WrongFlavor", OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		OrderServerResource.normalizeRequirements(categories, new HashMap<String, String>(),
				listFlavorsFogbow).get(OrderAttribute.REQUIREMENTS.getValue());
	}
	
	@Test
	public void testNormalizeRequirementsWithAttributeAndCategoryRequirement() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		String requeriments = RequirementsHelper.GLUE_VCPU_TERM + ">=1";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requeriments);
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = OrderServerResource.normalizeRequirements(
				categories, xOCCIAtt, listFlavorsFogbow);
		Assert.assertEquals("(" + requeriments + ")&&(" + RequirementsHelper.GLUE_MEM_RAM_TERM
				+ ">=" + memMedium + "&&" + RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium
				+ ")", normalizeRequirements.get(OrderAttribute.REQUIREMENTS.getValue())); 
	}	
	
	@Test(expected=OCCIException.class)
	public void testNormalizeRequirementsWithoutAttributeRequirementAndWithCategory() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = OrderServerResource.normalizeRequirements(
				categories, new HashMap<String, String>(), listFlavorsFogbow);
		Assert.assertEquals(RequirementsHelper.GLUE_MEM_RAM_TERM + ">=" + memMedium + "&&"
				+ RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium,
				normalizeRequirements.get(OrderAttribute.REQUIREMENTS.getValue()));
		
		// Wrong flavor name
		categories = new ArrayList<Category>();
		categories.add(new Category("WrongFlavor", OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		OrderServerResource.normalizeRequirements(categories, new HashMap<String, String>(),
				listFlavorsFogbow).get(OrderAttribute.REQUIREMENTS.getValue());
	}	
}
