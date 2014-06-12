package org.fogbowcloud.manager.occi.core;

import java.util.List;

import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
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
		headers.add(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		String token = HeaderUtils.getAuthToken(headers);

		Assert.assertEquals(OCCITestHelper.ACCESS_TOKEN, token);
	}

	@Test(expected = OCCIException.class)
	public void testEmptyToken() {
		headers.add(OCCIHeaders.X_AUTH_TOKEN, "");
		HeaderUtils.getAuthToken(headers);
	}

	@Test
	public void testGetOneCategory() {
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> listCAtegory = HeaderUtils.getCategories(headers);

		Assert.assertEquals(1, listCAtegory.size());
	}

	@Test
	public void testGetManyCategory() {
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		Category category2 = new Category("m1-namo", "namo-teste", RequestConstants.MIXIN_CLASS);
		Category category3 = new Category("stonage", "stonage", RequestConstants.MIXIN_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category2.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category3.toHeader());
		List<Category> listCAtegory = HeaderUtils.getCategories(headers);

		Assert.assertEquals(3, listCAtegory.size());
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongSchemaSyntax() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK ; schemeWRONG=\"schema\" ; class=\"mixin\"");
		HeaderUtils.getCategories(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongClassSyntax() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK ; scheme=\"schema\" ; classWRONG=\"mixin\"");
		HeaderUtils.getCategories(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWithoutClass() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), "termOK ; scheme=\"schema\"");
		HeaderUtils.getCategories(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryEmptyTerm() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"; scheme=\"schema\" ; class=\"mixin\"");
		HeaderUtils.getCategories(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryEmptyScheme() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK; scheme=\"\" ; class=\"mixin\"");
		HeaderUtils.getCategories(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryEmptyClass() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK; scheme=\"scheme\" ; class=\"\"");
		HeaderUtils.getCategories(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongTerm() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK = wrongsyntax; scheme=\"scheme\" ; class=\"class\"");
		HeaderUtils.getCategories(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongSyntax() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY),
				"termOK; scheme=\"scheme\" ; class=\"\"; wrong =\"wrong\"");
		HeaderUtils.getCategories(headers);
	}

	@Test
	public void testValidContentType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE),
				OCCITestHelper.CONTENT_TYPE_OCCI);
		HeaderUtils.checkOCCIContentType(headers);
	}

	@Test(expected = OCCIException.class)
	public void testWrongContentType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE), "wrong");
		HeaderUtils.checkOCCIContentType(headers);
	}

	@Test
	public void testValidCategoryFogbowRequest() {
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
	}

	@Test(expected = OCCIException.class)
	public void testInvalidCategoryFogbowRequest() {
		Category category = new Category("fogbow-request-wrong", RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
	}

	@Test
	public void testNormalize() {
		Assert.assertEquals("Fulano", HeaderUtils.normalize("FULANO"));
	}

	@Test(expected = OCCIException.class)
	public void testCheckCategoriesWrongCategory() {
		Category category = new Category("wrong-term", RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
	}

	@Test(expected = OCCIException.class)
	public void testCheckCategoriesWithoutCategory() {
		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, RequestConstants.TERM);
	}

	@Test
	public void testCheckValueInteger() {
		HeaderUtils.checkIntegerValue("10");
	}

	@Test(expected = OCCIException.class)
	public void testCheckWrongValueInteger() {
		HeaderUtils.checkIntegerValue("wrong");
	}

	@Test
	public void testCheckValueDate() {
		HeaderUtils.checkDateValue("2014-01-20");
	}

	@Test(expected = OCCIException.class)
	public void testCheckWrongValueDate() {
		HeaderUtils.checkDateValue("wrong");
	}
}
