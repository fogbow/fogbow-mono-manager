package org.fogbowcloud.manager.occi.core;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.RequestHelper;
import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestHeaderUtils {

	Series<Header> headers;

	@Before
	public void setup() throws Exception {
		headers = new Series<Header>(Header.class);
	}

	@Test
	public void testValidSyntaxToken() {
		headers.add(OCCIHeaders.X_AUTH_TOKEN, RequestHelper.ACCESS_TOKEN);
		String token = HeaderUtils.getToken(headers);

		Assert.assertEquals(RequestHelper.ACCESS_TOKEN, token);
	}

	@Test(expected = OCCIException.class)
	public void testEmptyToken() {
		headers.add(OCCIHeaders.X_AUTH_TOKEN, "");
		HeaderUtils.getToken(headers);
	}

	@Test
	public void testValidAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 6");
		int instances = HeaderUtils.getNumberOfInstances(headers);

		Assert.assertEquals(6, instances);
	}

	@Test
	public void testValidAttributeInstacesValeuDefault() {
		int instances = HeaderUtils.getNumberOfInstances(headers);

		Assert.assertEquals(1, instances);
	}

	@Test(expected = OCCIException.class)
	public void testWrongAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + " = wrong");
		HeaderUtils.getNumberOfInstances(headers);
	}

	@Test(expected = OCCIException.class)
	public void testEmptyAttributeInstaces() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + " =");
		HeaderUtils.getNumberOfInstances(headers);
	}

	@Test
	public void testGetOneCategory() {
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> listCAtegory = HeaderUtils.getListCategory(headers);

		Assert.assertEquals(1, listCAtegory.size());
	}

	@Test
	public void testGetManyCategory() {
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		Category category2 = new Category("m1-namo", "namo-teste", OCCIHeaders.MIXIN_CLASS);
		Category category3 = new Category("stonage", "stonage", OCCIHeaders.MIXIN_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category2.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category3.toHeader());
		List<Category> listCAtegory = HeaderUtils.getListCategory(headers);

		Assert.assertEquals(3, listCAtegory.size());
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongSchemaSyntax() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK ; schemeWRONG=\"schema\" ; class=\"mixin\"");
		HeaderUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongClassSyntax() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK ; scheme=\"schema\" ; classWRONG=\"mixin\"");
		HeaderUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWithoutClass() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), "termOK ; scheme=\"schema\"");
		HeaderUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryEmptyTerm() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"; scheme=\"schema\" ; class=\"mixin\"");
		HeaderUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryEmptyScheme() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK; scheme=\"\" ; class=\"mixin\"");
		HeaderUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryEmptyClass() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK; scheme=\"scheme\" ; class=\"\"");
		HeaderUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongTerm() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK = wrongsyntax; scheme=\"scheme\" ; class=\"class\"");
		HeaderUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongSyntax() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK; scheme=\"scheme\" ; class=\"\"; wrong =\"wrong\"");
		HeaderUtils.getListCategory(headers);
	}

	@Test
	public void testValidCheckFogbowHeaders() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE),
				RequestHelper.CONTENT_TYPE_OCCI);
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		HeaderUtils.checkFogbowHeaders(headers);
	}

	@Test
	public void testValidContentType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE),
				RequestHelper.CONTENT_TYPE_OCCI);
		HeaderUtils.checkOCCIContentType(headers);
	}

	@Test(expected = OCCIException.class)
	public void testWrongContentType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE), "wrong");
		HeaderUtils.checkOCCIContentType(headers);
	}

	@Test
	public void testCheckAttributes() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + "=10");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.TYPE.getValue() + "=\"one-time\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_FROM.getValue() + "=\"2014-04-01\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		HeaderUtils.checkXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesWrongType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + "=10");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.TYPE.getValue() + "=\"wrong\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_FROM.getValue() + "=\"2014-04-01\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		HeaderUtils.checkXOCCIAtt(xOCCIAtt);
	}

	@Test(expected = OCCIException.class)
	public void testCheckAttributesWrongDate() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.INSTANCE_COUNT.getValue() + "=10");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.TYPE.getValue() + "=\"one-tine\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_FROM.getValue() + "=\"wrong\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE),
				RequestAttribute.VALID_UNTIL.getValue() + "=\"2014-03-30\"");
		Map<String, String> xOCCIAtt = HeaderUtils.getXOCCIAtributes(headers);

		HeaderUtils.checkXOCCIAtt(xOCCIAtt);
	}

	@Test
	public void testValidCategoryFogbowRequest() {
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> listCategory = HeaderUtils.getListCategory(headers);
		HeaderUtils.validateRequestCategory(listCategory);
	}

	@Test(expected = OCCIException.class)
	public void testInvalidCategoryFogbowRequest() {
		Category category = new Category("fogbow-request-wrong", FogbowResourceConstants.SCHEME,
				OCCIHeaders.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> listCategory = HeaderUtils.getListCategory(headers);
		HeaderUtils.validateRequestCategory(listCategory);
	}

	@Test
	public void testNormalize() {
		Assert.assertEquals("Fulano", HeaderUtils.normalize("FULANO"));
	}
}
