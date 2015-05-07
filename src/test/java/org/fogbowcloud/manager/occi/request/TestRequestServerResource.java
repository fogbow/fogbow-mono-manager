package org.fogbowcloud.manager.occi.request;

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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestRequestServerResource {

	Series<Header> headers;

	@Before
	public void setup() throws Exception {
		headers = new Series<Header>(Header.class);
	}

	@Test
	public void testNormalizeXOCCIAtt() {
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + "=10");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);

		Assert.assertEquals("10", xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT.getValue()));
	}

	@Test(expected = OCCIException.class)
	public void testNormalizeXOCCIAttWrongNameAttribute() {
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "worng-attribute" + "=10");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testCheckRequestType() {
		RequestServerResource.checkRequestType("one-time");
	}

	@Test(expected = OCCIException.class)
	public void testCheckRequestInvalidType() {
		RequestServerResource.checkRequestType("wrong");
	}

	@Test
	public void testValidAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 6");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
		int instances = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT.getValue()));

		Assert.assertEquals(6, instances);
	}

	@Test
	public void testValidAttributeInstacesValeuDefault() {
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
		int instances = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT.getValue()));

		Assert.assertEquals(1, instances);
	}

	@Test(expected = OCCIException.class)
	public void testWrongAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + " = wrong");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testEmptyAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + " =");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testCheckAttributes() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.TYPE.getValue() + "=\"one-time\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesWrongType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.TYPE.getValue() + "=\"wrong\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testCheckAttributesDate() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_FROM.getValue() + "=\"2014-04-01\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesWrongDate() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_FROM.getValue() + "=\"wrong\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		RequestServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}
	
	@Test
	public void testNormalizeRequirementsNull() {
		Assert.assertNull(RequestServerResource.normalizeRequirements(new ArrayList<Category>(),
				new HashMap<String, String>(), new ArrayList<Flavor>()).get(
				RequestAttribute.REQUIREMENTS.getValue()));
	}
	
	@Test(expected=OCCIException.class)
	public void testNormalizeRequirementsWithAttributeRequirement() {
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		String requirements = RequirementsHelper.GLUE_VCPU_TERM + ">=1";
		xOCCIAtt.put(RequestAttribute.REQUIREMENTS.getValue(), requirements);
		Map<String, String> normalizeRequirements = RequestServerResource.normalizeRequirements(
				new ArrayList<Category>(), xOCCIAtt, new ArrayList<Flavor>());
		Assert.assertEquals(requirements,
				normalizeRequirements.get(RequestAttribute.REQUIREMENTS.getValue()));
		
		// Wrong requirements
		xOCCIAtt.put(RequestAttribute.REQUIREMENTS.getValue(), "/wrongrequirements/");
		normalizeRequirements = RequestServerResource.normalizeRequirements(
				new ArrayList<Category>(), xOCCIAtt, new ArrayList<Flavor>());
	}

	@Test(expected=OCCIException.class)
	public void testNormalizeRequirementsWithCategoryRequirement() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, RequestConstants.TEMPLATE_RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS));
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = RequestServerResource.normalizeRequirements(
				categories, new HashMap<String, String>(), listFlavorsFogbow);
		Assert.assertEquals(RequirementsHelper.GLUE_MEM_RAM_TERM + ">=" + memMedium + "&&"
				+ RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium,
				normalizeRequirements.get(RequestAttribute.REQUIREMENTS.getValue()));
		
		// Wrong flavor name
		categories = new ArrayList<Category>();
		categories.add(new Category("WrongFlavor", RequestConstants.TEMPLATE_RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS));
		RequestServerResource.normalizeRequirements(categories, new HashMap<String, String>(),
				listFlavorsFogbow).get(RequestAttribute.REQUIREMENTS.getValue());
	}
	
	@Test
	public void testNormalizeRequirementsWithAttributeAndCategoryRequirement() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, RequestConstants.TEMPLATE_RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS));
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		String requeriments = RequirementsHelper.GLUE_VCPU_TERM + ">=1";
		xOCCIAtt.put(RequestAttribute.REQUIREMENTS.getValue(), requeriments);
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = RequestServerResource.normalizeRequirements(
				categories, xOCCIAtt, listFlavorsFogbow);
		Assert.assertEquals("(" + requeriments + ")&&(" + RequirementsHelper.GLUE_MEM_RAM_TERM
				+ ">=" + memMedium + "&&" + RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium
				+ ")", normalizeRequirements.get(RequestAttribute.REQUIREMENTS.getValue())); 
	}	
	
	@Test(expected=OCCIException.class)
	public void testNormalizeRequirementsWithoutAttributeRequirementAndWithCategory() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, RequestConstants.TEMPLATE_RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS));
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = RequestServerResource.normalizeRequirements(
				categories, new HashMap<String, String>(), listFlavorsFogbow);
		Assert.assertEquals(RequirementsHelper.GLUE_MEM_RAM_TERM + ">=" + memMedium + "&&"
				+ RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium,
				normalizeRequirements.get(RequestAttribute.REQUIREMENTS.getValue()));
		
		// Wrong flavor name
		categories = new ArrayList<Category>();
		categories.add(new Category("WrongFlavor", RequestConstants.TEMPLATE_RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS));
		RequestServerResource.normalizeRequirements(categories, new HashMap<String, String>(),
				listFlavorsFogbow).get(RequestAttribute.REQUIREMENTS.getValue());
	}	
}
