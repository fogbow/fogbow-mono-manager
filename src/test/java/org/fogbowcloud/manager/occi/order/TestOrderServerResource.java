package org.fogbowcloud.manager.occi.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.occi.OCCIConstants;
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
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=10");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);

		Assert.assertEquals("10", xOCCIAtt.get(OrderAttribute.INSTANCE_COUNT.getValue()));
	}

	@Test(expected = OCCIException.class)
	public void testNormalizeXOCCIAttWrongNameAttribute() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME, OrderConstants.KIND_CLASS);
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
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);
		xOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
		int instances = Integer.valueOf(xOCCIAtt.get(OrderAttribute.INSTANCE_COUNT.getValue()));

		Assert.assertEquals(6, instances);
	}

	@Test
	public void testValidAttributeInstacesValeuDefault() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);
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
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesWrongType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), OrderAttribute.TYPE.getValue() + "=\"wrong\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testCheckAttributesDate() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.VALID_FROM.getValue() + "=\"2014-04-01\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=" + OrderConstants.COMPUTE_TERM);
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
				new HashMap<String, String>(), new ArrayList<Flavor>()).get(OrderAttribute.REQUIREMENTS.getValue()));
	}

	@Test(expected = OCCIException.class)
	public void testNormalizeRequirementsWithAttributeRequirement() {
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		String requirements = RequirementsHelper.GLUE_VCPU_TERM + ">=1";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requirements);
		Map<String, String> normalizeRequirements = OrderServerResource.normalizeRequirements(new ArrayList<Category>(),
				xOCCIAtt, new ArrayList<Flavor>());
		Assert.assertEquals(requirements, normalizeRequirements.get(OrderAttribute.REQUIREMENTS.getValue()));

		// Wrong requirements
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), "/wrongrequirements/");
		normalizeRequirements = OrderServerResource.normalizeRequirements(new ArrayList<Category>(), xOCCIAtt,
				new ArrayList<Flavor>());
	}

	@Test(expected = OCCIException.class)
	public void testNormalizeRequirementsWithCategoryRequirement() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = OrderServerResource.normalizeRequirements(categories,
				new HashMap<String, String>(), listFlavorsFogbow);
		Assert.assertEquals(RequirementsHelper.GLUE_MEM_RAM_TERM + ">=" + memMedium + "&&"
				+ RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium,
				normalizeRequirements.get(OrderAttribute.REQUIREMENTS.getValue()));

		// Wrong flavor name
		categories = new ArrayList<Category>();
		categories
				.add(new Category("WrongFlavor", OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		OrderServerResource.normalizeRequirements(categories, new HashMap<String, String>(), listFlavorsFogbow)
				.get(OrderAttribute.REQUIREMENTS.getValue());
	}

	@Test
	public void testNormalizeRequirementsWithAttributeAndCategoryRequirement() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		String requeriments = RequirementsHelper.GLUE_VCPU_TERM + ">=1";
		xOCCIAtt.put(OrderAttribute.REQUIREMENTS.getValue(), requeriments);
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = OrderServerResource.normalizeRequirements(categories, xOCCIAtt,
				listFlavorsFogbow);
		Assert.assertEquals(
				"(" + requeriments + ")&&(" + RequirementsHelper.GLUE_MEM_RAM_TERM + ">=" + memMedium + "&&"
						+ RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium + ")",
				normalizeRequirements.get(OrderAttribute.REQUIREMENTS.getValue()));
	}

	@Test(expected = OCCIException.class)
	public void testNormalizeRequirementsWithoutAttributeRequirementAndWithCategory() {
		List<Category> categories = new ArrayList<Category>();
		String mediumFlavor = "medium";
		categories.add(new Category(mediumFlavor, OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		List<Flavor> listFlavorsFogbow = new ArrayList<Flavor>();
		listFlavorsFogbow.add(new Flavor("small", "1", "1", "0"));
		String cpuMedium = "2";
		String memMedium = "4";
		listFlavorsFogbow.add(new Flavor(mediumFlavor, cpuMedium, memMedium, "0"));
		listFlavorsFogbow.add(new Flavor("large", "4", "8", "0"));
		Map<String, String> normalizeRequirements = OrderServerResource.normalizeRequirements(categories,
				new HashMap<String, String>(), listFlavorsFogbow);
		Assert.assertEquals(RequirementsHelper.GLUE_MEM_RAM_TERM + ">=" + memMedium + "&&"
				+ RequirementsHelper.GLUE_VCPU_TERM + ">=" + cpuMedium,
				normalizeRequirements.get(OrderAttribute.REQUIREMENTS.getValue()));

		// Wrong flavor name
		categories = new ArrayList<Category>();
		categories
				.add(new Category("WrongFlavor", OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		OrderServerResource.normalizeRequirements(categories, new HashMap<String, String>(), listFlavorsFogbow)
				.get(OrderAttribute.REQUIREMENTS.getValue());
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesResourceKindDefault() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testCheckAttributesResourceKindStorage() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=\"" + OrderConstants.STORAGE_TERM + "\"");
		String value = "2";
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.STORAGE_SIZE.getValue() + "=\"" + value + "\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		Map<String, String> normalizeXOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
		Assert.assertEquals(OrderConstants.STORAGE_TERM,
				normalizeXOCCIAtt.get(OrderAttribute.RESOURCE_KIND.getValue()));
		Assert.assertEquals(value, normalizeXOCCIAtt.get(OrderAttribute.STORAGE_SIZE.getValue()));
	}

	@Test
	public void testCheckAttributesResourceKindNetworkDynamicSucess() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=\"" + OrderConstants.NETWORK_TERM + "\"");

		String address = "10.30.0.1/24";
		String gateway = "10.30.10.100";

		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.DYNAMIC.getValue() + "\"");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		Map<String, String> normalizeXOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
		Assert.assertEquals(OrderConstants.NETWORK_TERM,
				normalizeXOCCIAtt.get(OrderAttribute.RESOURCE_KIND.getValue()));
		Assert.assertEquals(address, normalizeXOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals(gateway, normalizeXOCCIAtt.get(OCCIConstants.NETWORK_GATEWAY));
		Assert.assertEquals(OCCIConstants.NetworkAllocation.DYNAMIC.getValue(),
				normalizeXOCCIAtt.get(OCCIConstants.NETWORK_ALLOCATION));
	}
	
	@Test
	public void testCheckAttributesResourceKindNetworkStaticSucess() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=\"" + OrderConstants.NETWORK_TERM + "\"");

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.100";

		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.STATIC.getValue() + "\"");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		Map<String, String> normalizeXOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
		Assert.assertEquals(OrderConstants.NETWORK_TERM,
				normalizeXOCCIAtt.get(OrderAttribute.RESOURCE_KIND.getValue()));
		Assert.assertEquals(address, normalizeXOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals(gateway, normalizeXOCCIAtt.get(OCCIConstants.NETWORK_GATEWAY));
		Assert.assertEquals(OCCIConstants.NetworkAllocation.STATIC.getValue(),
				normalizeXOCCIAtt.get(OCCIConstants.NETWORK_ALLOCATION));
	}
	
	@Test
	public void testCheckAttributesResourceKindNetworkIP6Sucess() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=\"" + OrderConstants.NETWORK_TERM + "\"");

		String address = "2001:db8:1234:0000:/48";
		String gateway = "2001:0db8:85a3:0000:0000:0000:0000:7344";

		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.STATIC.getValue() + "\"");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		Map<String, String> normalizeXOCCIAtt = OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
		Assert.assertEquals(OrderConstants.NETWORK_TERM,
				normalizeXOCCIAtt.get(OrderAttribute.RESOURCE_KIND.getValue()));
		Assert.assertEquals(address, normalizeXOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals(gateway, normalizeXOCCIAtt.get(OCCIConstants.NETWORK_GATEWAY));
		Assert.assertEquals(OCCIConstants.NetworkAllocation.STATIC.getValue(),
				normalizeXOCCIAtt.get(OCCIConstants.NETWORK_ALLOCATION));
	}
	
	@Test(expected = OCCIException.class)
	public void testCheckAttributesResourceKindNetworkWrongAddress() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=\"" + OrderConstants.NETWORK_TERM + "\"");

		String address = "10.30.0.1";
		String gateway = "10.30.10.100";

		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.STATIC.getValue() + "\"");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}
	
	@Test(expected = OCCIException.class)
	public void testCheckAttributesResourceKindNetworkWrongGateway() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=\"" + OrderConstants.NETWORK_TERM + "\"");

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.266";

		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ALLOCATION + "=\"" + OCCIConstants.NetworkAllocation.STATIC.getValue() + "\"");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}
	
	@Test(expected = OCCIException.class)
	public void testCheckAttributesResourceKindNetworkWrongAllocation() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=\"" + OrderConstants.NETWORK_TERM + "\"");

		String address = "10.30.0.1/8";
		String gateway = "10.30.10.240";

		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ADDRESS + "=\"" + address + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_GATEWAY + "=\"" + gateway + "\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OCCIConstants.NETWORK_ALLOCATION + "=\"wrongallocation\"");

		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesResourceKindStorageWithoutSizeAttribute() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.INSTANCE_COUNT.getValue() + "=\"1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				OrderAttribute.RESOURCE_KIND.getValue() + "=\"" + OrderConstants.STORAGE_TERM + "\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		OrderServerResource.normalizeXOCCIAtt(xOCCIAtt);
	}
}
