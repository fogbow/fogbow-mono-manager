package org.fogbowcloud.manager.occi.request;

import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
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
				OCCIHeaders.KIND_CLASS);
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
				OCCIHeaders.KIND_CLASS);
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
}
