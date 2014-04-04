package org.fogbowcloud.manager.occi.core;

import java.util.List;

import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.fogbowcloud.manager.occi.model.TestRequestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class TestFogBowUtils {

	Series<Header> headers;

	@Before
	public void setup() throws Exception {
		headers = new Series<Header>(Header.class);
	}

	@Test
	public void testValidSyntaxToken() {
		headers.add(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		String token = FogbowUtils.getToken(headers);

		Assert.assertEquals(TestRequestHelper.ACCESS_TOKEN, token);
	}

	@Test(expected = OCCIException.class)
	public void testEmptyToken() {
		headers.add(HeaderConstants.X_AUTH_TOKEN, "");
		FogbowUtils.getToken(headers);
	}

	@Test
	public void testValidAttributeInstaces() {
		headers.add(FogbowUtils.normalize(HeaderConstants.X_OCCI_ATTRIBUTE),
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 1");
		int instances = FogbowUtils.getAttributeInstances(headers);

		Assert.assertEquals(1, instances);
	}

	@Test(expected = OCCIException.class)
	public void testWrongAttributeInstaces() {
		headers.add(FogbowUtils.normalize(HeaderConstants.X_OCCI_ATTRIBUTE),
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = wrong");
		FogbowUtils.getAttributeInstances(headers);
	}

	// *
	@Test(expected = OCCIException.class)
	public void testEmptyAttributeInstaces() {
		headers.add(FogbowUtils.normalize(HeaderConstants.X_OCCI_ATTRIBUTE),
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " =");
		FogbowUtils.getAttributeInstances(headers);
	}

	@Test
	public void testValidAttributeType() {
		headers.add(
				FogbowUtils.normalize(HeaderConstants.X_OCCI_ATTRIBUTE),
				FogbowResourceConstants.ATRIBUTE_TYPE_FOGBOW_REQUEST + " = "
						+ RequestType.ONE_TIME.getValue());
		String type = FogbowUtils.getAttributeType(headers);

		Assert.assertEquals(RequestType.ONE_TIME.getValue(), type);
	}

	@Test(expected = OCCIException.class)
	public void testWrongAttributeType() {
		headers.add(FogbowUtils.normalize(HeaderConstants.X_OCCI_ATTRIBUTE),
				FogbowResourceConstants.ATRIBUTE_TYPE_FOGBOW_REQUEST + " = wrong");
		FogbowUtils.getAttributeType(headers);
	}

	// *
	@Test(expected = OCCIException.class)
	public void testEmptyAttributeType() {
		headers.add(FogbowUtils.normalize(HeaderConstants.X_OCCI_ATTRIBUTE),
				FogbowResourceConstants.ATRIBUTE_TYPE_FOGBOW_REQUEST + " =");
		FogbowUtils.getAttributeType(headers);
	}

	@Test(expected = OCCIException.class)
	public void testWrongAttributeValidFrom() {
		headers.add(FogbowUtils.normalize(HeaderConstants.X_OCCI_ATTRIBUTE),
				FogbowResourceConstants.ATRIBUTE_VALID_FROM_FOGBOW_REQUEST + " = wrong");
		FogbowUtils.getAttributeValidFrom(headers);
	}

	@Test(expected = OCCIException.class)
	public void testWrongAttributeValidUntil() {
		headers.add(FogbowUtils.normalize(HeaderConstants.X_OCCI_ATTRIBUTE),
				FogbowResourceConstants.ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST + " = wrong");
		FogbowUtils.getAttributeValidUntil(headers);
	}

	@Test
	public void testGetOneCategory() {
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY), category.getHeaderFormat());
		List<Category> listCAtegory = FogbowUtils.getListCategory(headers);

		Assert.assertEquals(1, listCAtegory.size());
	}

	@Test
	public void testGetManyCategory() {
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		Category category2 = new Category("m1-namo", "namo-teste", HeaderConstants.MIXIN_CLASS);
		Category category3 = new Category("stonage", "stonage", HeaderConstants.MIXIN_CLASS);
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY), category.getHeaderFormat());
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY), category2.getHeaderFormat());
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY), category3.getHeaderFormat());
		List<Category> listCAtegory = FogbowUtils.getListCategory(headers);

		Assert.assertEquals(3, listCAtegory.size());
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongSchemaSyntax() {
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY),
				"termOK ; schemeWRONG=\"schema\" ; class=\"mixin\"");
		FogbowUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategoryWrongClassSyntax() {
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY),
				"termOK ; scheme=\"schema\" ; classWRONG=\"mixin\"");
		FogbowUtils.getListCategory(headers);
	}

	@Test(expected = OCCIException.class)
	public void testGetCategorWithoutClass() {
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY), "termOK ; scheme=\"schema\"");
		FogbowUtils.getListCategory(headers);
	}

	@Test
	public void testValidCheckFogbowHeaders() {
		headers.add(FogbowUtils.normalize(HeaderConstants.CONTENT_TYPE),
				TestRequestHelper.CONTENT_TYPE_OCCI);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY), category.getHeaderFormat());
		FogbowUtils.checkFogbowHeaders(headers);
	}

	@Test
	public void testValidContentType() {
		headers.add(FogbowUtils.normalize(HeaderConstants.CONTENT_TYPE),
				TestRequestHelper.CONTENT_TYPE_OCCI);
		FogbowUtils.validateContentType(headers);
	}

	@Test(expected = OCCIException.class)
	public void testWrongContentType() {
		headers.add(FogbowUtils.normalize(HeaderConstants.CONTENT_TYPE), "wrong");
		FogbowUtils.validateContentType(headers);
	}

	@Test
	public void testValidCategoryFogbowRequest() {
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY), category.getHeaderFormat());
		List<Category> listCategory = FogbowUtils.getListCategory(headers);
		FogbowUtils.validateRequestCategory(listCategory);
	}

	@Test(expected = OCCIException.class)
	public void testInvalidCategoryFogbowRequest() {
		Category category = new Category("fogbow-request-wrong",
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		headers.add(FogbowUtils.normalize(HeaderConstants.CATEGORY), category.getHeaderFormat());
		List<Category> listCategory = FogbowUtils.getListCategory(headers);
		FogbowUtils.validateRequestCategory(listCategory);
	}
}
