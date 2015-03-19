package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.core.RequirementsHelper.ValueAndOperator;
import org.fogbowcloud.manager.core.model.Flavor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import condor.classad.ClassAdParser;
import condor.classad.Op;

public class TestRequirementsHelper {

	private RequirementsHelper requirementsHelper;

	@Before
	public void setUp() {
		this.requirementsHelper = new RequirementsHelper();
	}

	@SuppressWarnings("static-access")
	@Test
	public void TestCheckValidRequirements() {
		String requirementsString = "X == 1 && Y >= 0";
		Assert.assertTrue(requirementsHelper.checkRequirements(requirementsString));
	}

	@SuppressWarnings("static-access")
	@Test
	public void TestCheckInvalidRequirements() {
		String wrongRequirementsString = "X (Wrong) 1 && Y == 0";
		Assert.assertFalse(requirementsHelper.checkRequirements(wrongRequirementsString));
	}

	@SuppressWarnings("static-access")
	@Test
	public void TestNormalizeAttrForCheck() {
		String value = "teste";
		String valueExpected = "\"" + value + "\"";
		Assert.assertEquals(valueExpected, requirementsHelper.normalizeLocationToCheck(value));
		Assert.assertEquals(valueExpected,
				requirementsHelper.normalizeLocationToCheck(valueExpected));
		Assert.assertNull(requirementsHelper.normalizeLocationToCheck(null));
	}

	@SuppressWarnings("static-access")
	@Test
	public void TestCheckLocation() {
		String value = "\"valueCorrect\"";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertTrue(requirementsHelper.matchLocation(requirementsStr, value));

		value = "valueCorrect";
		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + "\"" + value + "\"";
		Assert.assertTrue(requirementsHelper.matchLocation(requirementsStr, value));
		
		value = "valueCorrect";
		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "!=" + "\"" + value + "\"";
		Assert.assertFalse(requirementsHelper.matchLocation(requirementsStr, value));
		
		value = "valueCorrect";
		requirementsStr = "(" + RequirementsHelper.GLUE_LOCATION_TERM + "!=" + "\"" + value + "\")" + "||X==1";
		Assert.assertFalse(requirementsHelper.matchLocation(requirementsStr, value));		
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void TestCheckLocationSameValue() {
		String value = "\"valueCorrect\"";
		String requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + value + " && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertTrue(requirementsHelper.matchLocation(requirementsStr, value));		
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void TestCheckLocationNull() {
		String value = "\"valueCorrect\"";
		String requirementsStr = "x==1 && x>9";
		Assert.assertFalse(requirementsHelper.matchLocation(requirementsStr, value));
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void TestCheckLocationWrongExpression() {
		String value = "\"valueCorrect\"";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "=\"\"";
		Assert.assertFalse(requirementsHelper.matchLocation(requirementsStr, value));
	}	

	@Test
	public void TestGetOneLocation() {
		String value = "local1";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertEquals(1, RequirementsHelper.getLocationsInRequiremets(requirementsStr).size());

		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertEquals(1, RequirementsHelper.getLocationsInRequiremets(requirementsStr).size());
	}

	@Test
	public void TestGetTwoLocation() {
		String value = "local1";
		String valueTwo = "local2";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value
				+ "&&" + RequirementsHelper.GLUE_LOCATION_TERM + "==" + valueTwo;
		Assert.assertEquals(2, RequirementsHelper.getLocationsInRequiremets(requirementsStr).size());
	}

	@Test
	public void TestGetOneLocationWithTwoValues() {
		String value = "local1";
		String valueTwo = "local2";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value
				+ "&&" + RequirementsHelper.GLUE_LOCATION_TERM + "!=" + valueTwo;
		Assert.assertEquals(1, RequirementsHelper.getLocationsInRequiremets(requirementsStr).size());
	}
	
	@Test
	public void TestExistsLocation() {
		String value = "local1";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertTrue(RequirementsHelper.existsLocation(requirementsStr));

		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertTrue(RequirementsHelper.existsLocation(requirementsStr));
		
		requirementsStr =  "X ==" + value;
		Assert.assertFalse(RequirementsHelper.existsLocation(requirementsStr));
		
		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + value + "|| X==" + value;
		Assert.assertTrue(RequirementsHelper.existsLocation(requirementsStr));
	}

	@SuppressWarnings("static-access")
	@Test
	public void TestCheckFlavor() {
		Flavor flavor = new Flavor("test", "12", "400", "11");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10";
		Assert.assertTrue(requirementsHelper.checkFlavorPerRequirements(flavor, requirementsStr));
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void TestCheckFlavorWithDiskEmpty() {
		Flavor flavor = new Flavor("test", "12", "400", "0");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10";
		Assert.assertTrue(requirementsHelper.checkFlavorPerRequirements(flavor, requirementsStr));
		
		flavor = new Flavor("test", "12", "400", "");
		flavor.setDisk(null);
		Assert.assertTrue(requirementsHelper.checkFlavorPerRequirements(flavor, requirementsStr));
	}	
	
	@SuppressWarnings("static-access")
	@Test
	public void TestCheckFlavorWithLocation() {
		Flavor flavor = new Flavor("test", "12", "400", "11");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String location = RequirementsHelper.GLUE_LOCATION_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10 && " + location + "==\"location\"";
		Assert.assertTrue(requirementsHelper.checkFlavorPerRequirements(flavor, requirementsStr));
	}

	@SuppressWarnings("static-access")
	@Test
	public void TestCheckInvalidFlavor() {
		Flavor flavor = new Flavor("test", "12", "400", "9");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10";
		Assert.assertFalse(requirementsHelper.checkFlavorPerRequirements(flavor, requirementsStr));
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void TestCheckFlavorWithNullRequirements() {
		Flavor flavor = new Flavor("test", "12", "400", "9");
		String requirementsStr = null;
		Assert.assertFalse(requirementsHelper.checkFlavorPerRequirements(flavor, requirementsStr));
	}	

	@SuppressWarnings("static-access")
	@Test
	public void TestFindFlavor() {
		String firstValue = "1";
		Flavor flavorOne = new Flavor("One", firstValue, "1", "100", "10");
		Flavor flavorTwo = new Flavor("Two", "2", "2", "200", "20");
		Flavor flavorThree = new Flavor("Three", "3", "30", "300", "30");

		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(flavorThree);
		flavors.add(flavorOne);
		flavors.add(flavorTwo);

		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 5 && " + mem + " > 50 && " + vCpu + " > 0";

		Assert.assertEquals(firstValue, requirementsHelper.findFlavor(flavors, requirementsStr)
				.getId());
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void TestFindFlavorWithOutDisk() {
		String firstValue = "1";
		Flavor flavorOne = new Flavor("One", firstValue, "1", "100", "10");
		flavorOne.setDisk(null);
		Flavor flavorTwo = new Flavor("Two", "2", "2", "200", "20");
		flavorTwo.setDisk(null);
		Flavor flavorThree = new Flavor("Three", "3", "30", "300", "30");
		flavorThree.setDisk(null);

		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(flavorThree);
		flavors.add(flavorOne);
		flavors.add(flavorTwo);

		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 5 && " + mem + " > 50 && " + vCpu + " > 0";

		Assert.assertEquals(firstValue, requirementsHelper.findFlavor(flavors, requirementsStr)
				.getId());
	}	

	@SuppressWarnings("static-access")
	@Test
	public void TestFindFlavorSameMenAndCore() {
		String firstValue = "1";
		Flavor flavorOne = new Flavor("One", firstValue, "1", "100", "10");
		Flavor flavorTwo = new Flavor("Two", "2", "1", "100", "20");

		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(flavorTwo);
		flavors.add(flavorOne);

		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 5 && " + mem + " > 50 && " + vCpu + " > 0";

		Assert.assertEquals(firstValue, requirementsHelper.findFlavor(flavors, requirementsStr)
				.getId());
	}	
	
	@Test
	public void testGetValueSmaller() {
		String attrName = "X";
		String requirementsStr = attrName + ">1&&" + attrName + ">=10";
		Assert.assertEquals("10",
				RequirementsHelper.getValueSmallerPerAttribute(requirementsStr, attrName));

		requirementsStr = attrName + ">1&&" + attrName + ">=10||" + attrName + ">=5";
		Assert.assertEquals("5",
				RequirementsHelper.getValueSmallerPerAttribute(requirementsStr, attrName));

		requirementsStr = attrName + ">1&&" + attrName + ">=10 && Y>=12 || (A==\"Test\")";
		Assert.assertEquals("10",
				RequirementsHelper.getValueSmallerPerAttribute(requirementsStr, attrName));

		requirementsStr = "(" + attrName + ">1&&" + attrName + ">=10 && Y>=12 || (A==\"Test\")) || D>=10 ";
		Assert.assertEquals("10",
				RequirementsHelper.getValueSmallerPerAttribute(requirementsStr, attrName));

		requirementsStr = attrName + "<1&&" + attrName + "<=10";
		Assert.assertEquals("0",
				RequirementsHelper.getValueSmallerPerAttribute(requirementsStr, attrName));

		requirementsStr = attrName + "<0&&" + attrName + "<=10";
		Assert.assertEquals("-1",
				RequirementsHelper.getValueSmallerPerAttribute(requirementsStr, attrName));
	}
	
	@Test
	public void testNormalizeOp() {		
		String requirementsStr = "(((X>1) && (Y==1)) || Y<10) && Y==10 && X>10";
		Op normalizeOPTypeTwo = RequirementsHelper.normalizeOPTypeTwo(toOp(requirementsStr), "X");
		Assert.assertEquals("((X>1)&&(X>10))", normalizeOPTypeTwo.toString());
		
		requirementsStr = "((X>1) && (Y==1))";
		normalizeOPTypeTwo = RequirementsHelper.normalizeOPTypeTwo(toOp(requirementsStr), "X");
		Assert.assertEquals("(X>1)", normalizeOPTypeTwo.toString());
		
		requirementsStr = "((X>1) && (Y==1))";
		normalizeOPTypeTwo = RequirementsHelper.normalizeOPTypeTwo(toOp(requirementsStr), "X");
		Assert.assertEquals("(X>1)", normalizeOPTypeTwo.toString());
		
		requirementsStr = "((X>1) && (Y==1) && (X>1) && (Y==1))";
		normalizeOPTypeTwo = RequirementsHelper.normalizeOPTypeTwo(toOp(requirementsStr), "X");
		Assert.assertEquals("((X>1)&&(X>1))", normalizeOPTypeTwo.toString());
	}
	
	@Test
	public void testNormalizeOpList() {
		List<String> listAtt = new ArrayList<String>();
		listAtt.add("X");
		listAtt.add("Y");
		String requirementsStr = "(((X>1) && (Y==1)) || Y<10) && Y==10 && X>10";
		Op normalizeOPTypeTwo = RequirementsHelper.normalizeOPTypeTwo(toOp(requirementsStr), listAtt);
		Assert.assertEquals("(((((X>1)&&(Y==1))||(Y<10))&&(Y==10))&&(X>10))", normalizeOPTypeTwo.toString());

		requirementsStr = "W>=0 && ((((X>1) && (Y==1)) || Y<10) && Y==10 && X>10)";
		normalizeOPTypeTwo = RequirementsHelper.normalizeOPTypeTwo(toOp(requirementsStr), listAtt);
		Assert.assertEquals("(((((X>1)&&(Y==1))||(Y<10))&&(Y==10))&&(X>10))", normalizeOPTypeTwo.toString());
		
		requirementsStr = "(W>=0 || X<=1) && ((((X>1) && (Y==1)) || Y<10) && Y==10 && X>10)";
		normalizeOPTypeTwo = RequirementsHelper.normalizeOPTypeTwo(toOp(requirementsStr), listAtt);
		Assert.assertEquals("((X<=1)&&(((((X>1)&&(Y==1))||(Y<10))&&(Y==10))&&(X>10)))", normalizeOPTypeTwo.toString());
	}	
	
	@Test
	public void testFindValuesInRequiremets() {
		String requirements = "X==\"id1\" || X==\"id2\"";
		String attName = "X";
		List<ValueAndOperator> findValuesInRequiremets = RequirementsHelper.findValuesInRequiremets(toOp(requirements), attName);
		Assert.assertEquals(2, findValuesInRequiremets.size());
		
		requirements = "X==\"id1\" || X==\"id2\" || X==\"id3\"";
		attName = "X";
		findValuesInRequiremets = RequirementsHelper.findValuesInRequiremets(toOp(requirements), attName);
		Assert.assertEquals(3, findValuesInRequiremets.size());
	}
	
	private Op toOp(String requirementsStr) {
		ClassAdParser classAdParser = new ClassAdParser(requirementsStr);
		return (Op) classAdParser.parse();	 
	}
}
