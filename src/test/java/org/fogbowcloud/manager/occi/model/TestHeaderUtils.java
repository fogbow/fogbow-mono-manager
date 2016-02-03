package org.fogbowcloud.manager.occi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.order.OrderConstants;
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
		String token = HeaderUtils.getAuthToken(headers, null,
				"Keystone uri=' http://localhost:5000'");

		Assert.assertEquals(OCCITestHelper.ACCESS_TOKEN, token);
	}

	@Test(expected = OCCIException.class)
	public void testEmptyToken() {
		headers.add(OCCIHeaders.X_AUTH_TOKEN, "");
		HeaderUtils.getAuthToken(headers, null, "Keystone uri=' http://localhost:5000'");
	}

	@Test
	public void testGetOneCategory() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> listCAtegory = HeaderUtils.getCategories(headers);

		Assert.assertEquals(1, listCAtegory.size());
	}

	@Test
	public void testGetManyCategory() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		Category category2 = new Category("m1-namo", "namo-teste", OrderConstants.MIXIN_CLASS);
		Category category3 = new Category("stonage", "stonage", OrderConstants.MIXIN_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category2.toHeader());
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category3.toHeader());
		List<Category> categories = HeaderUtils.getCategories(headers);

		Assert.assertEquals(3, categories.size());
	}
	
	@Test
	public void testGetManyCategoriesWithMoreAttributes() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), "term1; scheme=\"scheme1\"; class=\"kind\"; location=\"location1\"; title=\"title1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), "term2; scheme=\"scheme2\"; class=\"mixin\"; location=\"location2\"; title=\"title2\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), "term3; scheme=\"scheme3\"; class=\"mixin\"; location=\"location3\"; title=\"title3\"");
		List<String> categoryTerms = new ArrayList<String>();
		categoryTerms.add("term1");
		categoryTerms.add("term2");
		categoryTerms.add("term3");
		List<Category> categories = HeaderUtils.getCategories(headers);
		Assert.assertEquals(3, categories.size());
		int categoriesOK = 0;
		for (String term : categoryTerms) {
			for (Category category : categories) {
				if (category.getTerm().equals(term)){
					categoriesOK++;
					break;
				}
			}
		}
		Assert.assertEquals(3, categoriesOK);
	}
	
	@Test
	public void testGetManyCategoriesInTheSameHeader() {
		String categoryValues = "term1; scheme=\"scheme1\"; class=\"kind\"; location=\"location1\"; title=\"title1\", "
				+ "term2; scheme=\"scheme2\";class=\"mixin\"; location=\"location2\"; title=\"title2\", "
				+ "term3; scheme=\"scheme3\";class=\"mixin\"; location=\"location3\"; title=\"title3\"";		
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), categoryValues);		
		List<String> categoryTerms = new ArrayList<String>();
		categoryTerms.add("term1");
		categoryTerms.add("term2");
		categoryTerms.add("term3");
		List<Category> categories = HeaderUtils.getCategories(headers);
		Assert.assertEquals(3, categories.size());
		int categoriesOK = 0;
		for (String term : categoryTerms) {
			for (Category category : categories) {
				if (category.getTerm().equals(term)){
					categoriesOK++;
					break;
				}
			}
		}
		Assert.assertEquals(3, categoriesOK);
	}
	
	@Test
	public void testGetManyCategoriesWithMoreAttributesInTheSameHeader() {
		String categoryValues = "term1; scheme=\"scheme1\"; class=\"kind\", term2; scheme=\"scheme2\";class=\"mixin\", "
				+ "term3; scheme=\"scheme3\";class=\"mixin\"";		
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), categoryValues);		
		List<Category> categories = HeaderUtils.getCategories(headers);
		List<String> categoryTerms = new ArrayList<String>();
		categoryTerms.add("term1");
		categoryTerms.add("term2");
		categoryTerms.add("term3");
		Assert.assertEquals(3, categories.size());
		int categoriesOK = 0;
		for (String term : categoryTerms) {
			for (Category category : categories) {
				if (category.getTerm().equals(term)){
					categoriesOK++;
					break;
				}
			}
		}
		Assert.assertEquals(3, categoriesOK);
	}
	
	@Test
	public void testGetAccept() {
		String acceptValue = "text/plain";		
		headers.add(HeaderUtils.normalize(OCCIHeaders.ACCEPT), acceptValue);		
		List<String> acceptContents = HeaderUtils.getAccept(headers);
		Assert.assertEquals(1, acceptContents.size());
		Assert.assertTrue(acceptContents.contains(acceptValue));
	}
	
	@Test
	public void testGetAcceptWithMoreInSameHeader() {
		String acceptValues = "text/plain, text/occi";		
		headers.add(HeaderUtils.normalize(OCCIHeaders.ACCEPT), acceptValues);		
		List<String> parsedAccepts = new ArrayList<String>();
		parsedAccepts.add("text/plain");
		parsedAccepts.add("text/occi");
		List<String> acceptContents = HeaderUtils.getAccept(headers);
		Assert.assertEquals(2, acceptContents.size());
		for (String accept : parsedAccepts) {
			Assert.assertTrue(acceptContents.contains(accept));
		}
	}
	
	@Test
	public void testGetAcceptWithMoreAtDifferentHeader() {
		List<String> parsedAccepts = new ArrayList<String>();
		parsedAccepts.add("text/plain");
		parsedAccepts.add("text/occi");

		for (String accept : parsedAccepts) {
			headers.add(HeaderUtils.normalize(OCCIHeaders.ACCEPT), accept);		
		}

		List<String> acceptContents = HeaderUtils.getAccept(headers);
		Assert.assertEquals(2, acceptContents.size());
		for (String accept : parsedAccepts) {
			Assert.assertTrue(acceptContents.contains(accept));
		}
	}
	
	@Test
	public void testGetXOCCIAttribute() {		
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "attribute.name=\"value\"");		
		Map<String, String> occiAttributes = HeaderUtils.getXOCCIAtributes(headers);

		Assert.assertEquals(1, occiAttributes.size());
		Assert.assertTrue(occiAttributes.containsKey("attribute.name"));
		Assert.assertTrue(occiAttributes.containsValue("value"));
	}
	
	@Test
	public void testGetXOCCIAttributes() {		
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "attribute.name1=\"value1\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "attribute.name2=\"value2\"");
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "attribute.name3=\"value3\"");
		Map<String, String> occiAttributes = HeaderUtils.getXOCCIAtributes(headers);

		Assert.assertEquals(3, occiAttributes.size());
		for (int i = 1; i <= 3; i++){
			Assert.assertTrue(occiAttributes.containsKey("attribute.name" + i));
			Assert.assertTrue(occiAttributes.containsValue("value" + i));
		}
	}
	
	@Test
	public void testGetXOCCIAttributesInTheSameHeader() {		
		headers.add(HeaderUtils.normalize(OCCIHeaders.X_OCCI_ATTRIBUTE), "attribute.name1=\"value1\", attribute.name2=\"value2\", attribute.name3=\"value3\"");		
		Map<String, String> occiAttributes = HeaderUtils.getXOCCIAtributes(headers);

		Assert.assertEquals(3, occiAttributes.size());
		for (int i = 1; i <= 3; i++){
			Assert.assertTrue(occiAttributes.containsKey("attribute.name" + i));
			Assert.assertTrue(occiAttributes.containsValue("value" + i));
		}
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
				OCCIHeaders.OCCI_CONTENT_TYPE);
		HeaderUtils.checkOCCIContentType(headers);
	}

	@Test(expected = OCCIException.class)
	public void testWrongContentType() {
		headers.add(HeaderUtils.normalize(OCCIHeaders.CONTENT_TYPE), "wrong");
		HeaderUtils.checkOCCIContentType(headers);
	}

	@Test
	public void testValidCategoryFogbowOrder() {
		Category category = new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, OrderConstants.TERM);
	}

	@Test(expected = OCCIException.class)
	public void testInvalidCategoryFogbowOrder() {
		Category category = new Category("fogbow-request-wrong", OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());
		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, OrderConstants.TERM);
	}

	@Test
	public void testNormalize() {
		Assert.assertEquals("Fulano", HeaderUtils.normalize("FULANO"));
	}

	@Test(expected = OCCIException.class)
	public void testCheckCategoriesWrongCategory() {
		Category category = new Category("wrong-term", OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS);
		headers.add(HeaderUtils.normalize(OCCIHeaders.CATEGORY), category.toHeader());

		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, OrderConstants.TERM);
	}

	@Test(expected = OCCIException.class)
	public void testCheckCategoriesWithoutCategory() {
		List<Category> categories = HeaderUtils.getCategories(headers);
		HeaderUtils.checkCategories(categories, OrderConstants.TERM);
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
