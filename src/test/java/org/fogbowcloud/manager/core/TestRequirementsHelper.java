package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.core.RequirementsHelper.ValueAndOperator;
import org.fogbowcloud.manager.core.model.Flavor;
import org.junit.Assert;
import org.junit.Test;

import condor.classad.ClassAdParser;
import condor.classad.Expr;
import condor.classad.Op;

public class TestRequirementsHelper {

	@Test
	public void testCheckValidRequirements() {
		String requirementsString = "X == 1 && Y >= 0";
		Assert.assertTrue(RequirementsHelper.checkSyntax(requirementsString));
	}

	@Test
	public void testCheckInvalidRequirements() {
		String wrongRequirementsString = "X (W =rong) 1 && Y == 0";
		Assert.assertFalse(RequirementsHelper.checkSyntax(wrongRequirementsString));
	}
	
	@Test
	public void testNormalizeAttrForCheck() {
		String value = "teste";
		String valueExpected = "\"" + value + "\"";
		Assert.assertEquals(valueExpected, RequirementsHelper.quoteLocation(value));
		Assert.assertEquals(valueExpected,
				RequirementsHelper.quoteLocation(valueExpected));
		Assert.assertNull(RequirementsHelper.quoteLocation(null));
	}
	
	@Test
	public void testMatchLocation() {
		String value = "\"valueCorrect\"";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertTrue(RequirementsHelper.matchLocation(requirementsStr, value));

		value = "valueCorrect";
		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + "\"" + value + "\"";
		Assert.assertTrue(RequirementsHelper.matchLocation(requirementsStr, value));
		
		// ignored location with "!="
		value = "valueCorrect";
		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "!=" + "\"" + value + "\"";
		Assert.assertTrue(RequirementsHelper.matchLocation(requirementsStr, value));

		// ignored location with "!="
		value = "valueCorrect";
		requirementsStr = "(" + RequirementsHelper.GLUE_LOCATION_TERM + "!=" + "\"" + value + "\")" + "||X==1";
		Assert.assertTrue(RequirementsHelper.matchLocation(requirementsStr, value));		
		
		value = "valueCorrect";
		requirementsStr = "";
		Assert.assertTrue(RequirementsHelper.matchLocation(requirementsStr, value));				
	}
	
	
	@Test
	public void testMatchLocationSameValue() {
		String value = "\"valueCorrect\"";
		String requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + value + " && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertTrue(RequirementsHelper.matchLocation(requirementsStr, value));		
	}
	
	
	@Test
	public void testMatchLocationNull() {
		String value = "\"valueCorrect\"";
		String requirementsStr = "x==1 && x>9";
		Assert.assertTrue(RequirementsHelper.matchLocation(requirementsStr, value));
	}
	
	
	@Test
	public void testMatchLocationWrongExpression() {
		String value = "\"valueCorrect\"";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "=\"\"";
		Assert.assertTrue(RequirementsHelper.matchLocation(requirementsStr, value));
	}	

	@Test
	public void testGetOneLocation() {
		String value = "local1";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertEquals(1, RequirementsHelper.getLocations(requirementsStr).size());

		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertEquals(1, RequirementsHelper.getLocations(requirementsStr).size());
	}

	@Test
	public void testGetTwoLocation() {
		String value = "local1";
		String valueTwo = "local2";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value
				+ "&&" + RequirementsHelper.GLUE_LOCATION_TERM + "==" + valueTwo;
		Assert.assertEquals(2, RequirementsHelper.getLocations(requirementsStr).size());
	}

	@Test
	public void testGetOneLocationWithTwoValues() {
		String value = "local1";
		String valueTwo = "local2";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value
				+ "&&" + RequirementsHelper.GLUE_LOCATION_TERM + "!=" + valueTwo;
		Assert.assertEquals(1, RequirementsHelper.getLocations(requirementsStr).size());
	}
	
	@Test
	public void testExistsLocation() {
		String value = "local1";
		String requirementsStr = "x==1 && " + RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertTrue(RequirementsHelper.hasLocation(requirementsStr));

		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + value;
		Assert.assertTrue(RequirementsHelper.hasLocation(requirementsStr));
		
		requirementsStr =  "X ==" + value;
		Assert.assertFalse(RequirementsHelper.hasLocation(requirementsStr));
		
		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + value + "|| X==" + value;
		Assert.assertTrue(RequirementsHelper.hasLocation(requirementsStr));
	}

	@Test 
	public void testGetLocationNoRequirements() {
		String requirementsStr = "";
		Assert.assertEquals(0, RequirementsHelper.getLocations(requirementsStr).size());		
	}
	
	@Test
	public void testCheckFlavor() {
		Flavor flavor = new Flavor("test", "12", "400", "11");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10";
		Assert.assertTrue(RequirementsHelper.matches(flavor, requirementsStr));
	}
	
	
	@Test
	public void testCheckFlavorWithDiskEmpty() {
		Flavor flavor = new Flavor("test", "12", "400", "0");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10";
		Assert.assertTrue(RequirementsHelper.matches(flavor, requirementsStr));
		
		flavor = new Flavor("test", "12", "400", "");
		flavor.setDisk(null);
		Assert.assertTrue(RequirementsHelper.matches(flavor, requirementsStr));
	}
	
	
	@Test
	public void testCheckFlavorWithoutGLUEExpressions() {
		Flavor flavor = new Flavor("test", "12", "400", "10");
		String requirementsStr = "x == 2";
		
		Assert.assertTrue(RequirementsHelper.matches(flavor, requirementsStr));
	}		
	
	
	@Test
	public void testCheckFlavorWithRequirementsNullOrEmpty() {
		Flavor flavor = new Flavor("test", "12", "400", "10");
		// empty
		String requirementsStr = "";		
		Assert.assertTrue(RequirementsHelper.matches(flavor, requirementsStr));
		
		// null
		requirementsStr = null;
		Assert.assertTrue(RequirementsHelper.matches(flavor, requirementsStr));
	}		
	
	
	@Test
	public void testCheckFlavorWithLocation() {
		Flavor flavor = new Flavor("test", "12", "400", "11");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String location = RequirementsHelper.GLUE_LOCATION_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10 && " + location + "==\"location\"";
		Assert.assertTrue(RequirementsHelper.matches(flavor, requirementsStr));
	}

	
	@Test
	public void testCheckInvalidFlavor() {
		Flavor flavor = new Flavor("test", "12", "400", "9");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10";
		Assert.assertFalse(RequirementsHelper.matches(flavor, requirementsStr));
		
		requirementsStr = disk + " < 10 && " + mem + " < 200 && " + vCpu + " < 2 && X == true" ;
		Assert.assertFalse(RequirementsHelper.matches(flavor, requirementsStr));				
	}

	
	@Test
	public void testFindFlavor() {
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

		Assert.assertEquals(firstValue, RequirementsHelper.findSmallestFlavor(flavors, requirementsStr)
				.getId());
	}
	
	@Test
	public void testFindFlavorWithOutDisk() {
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

		Assert.assertEquals(firstValue, RequirementsHelper.findSmallestFlavor(flavors, requirementsStr)
				.getId());
	}	

	
	@Test
	public void testFindFlavorSameMenAndCore() {
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

		Assert.assertEquals(firstValue, RequirementsHelper.findSmallestFlavor(flavors, requirementsStr)
				.getId());
	}	
	
	@Test
	public void testGetValueSmaller() {
		String attrName = "X";
		String requirementsStr = attrName + ">1&&" + attrName + ">=10";
		Assert.assertEquals("10",
				RequirementsHelper.getSmallestValueForAttribute(requirementsStr, attrName));

		requirementsStr = attrName + ">1&&" + attrName + ">=10||" + attrName + ">=5";
		Assert.assertEquals("5",
				RequirementsHelper.getSmallestValueForAttribute(requirementsStr, attrName));

		requirementsStr = attrName + ">1&&" + attrName + ">=10 && Y>=12 || (A==\"Test\")";
		Assert.assertEquals("10",
				RequirementsHelper.getSmallestValueForAttribute(requirementsStr, attrName));

		requirementsStr = "(" + attrName + ">1&&" + attrName + ">=10 && Y>=12 || (A==\"Test\")) || D>=10 ";
		Assert.assertEquals("10",
				RequirementsHelper.getSmallestValueForAttribute(requirementsStr, attrName));

		requirementsStr = attrName + "<1&&" + attrName + "<=10";
		Assert.assertEquals("0",
				RequirementsHelper.getSmallestValueForAttribute(requirementsStr, attrName));

		requirementsStr = attrName + "<0&&" + attrName + "<=10";
		Assert.assertEquals("-1",
				RequirementsHelper.getSmallestValueForAttribute(requirementsStr, attrName));
	}
	
	@Test
	public void testExtractVariableExpression() {		
		String requirementsStr = "(((X>1) && (Y==1)) || Y<10) && Y==10 && X>10";
		Expr normalizeOPTypeTwo = RequirementsHelper.extractVariableExpression(toOp(requirementsStr), "X");
		Assert.assertEquals("((X>1)&&(X>10))", normalizeOPTypeTwo.toString());
		
		requirementsStr = "((X>1) && (Y==1))";
		normalizeOPTypeTwo = RequirementsHelper.extractVariableExpression(toOp(requirementsStr), "X");
		Assert.assertEquals("(X>1)", normalizeOPTypeTwo.toString());
		
		requirementsStr = "((X>1) && (Y==1))";
		normalizeOPTypeTwo = RequirementsHelper.extractVariableExpression(toOp(requirementsStr), "X");
		Assert.assertEquals("(X>1)", normalizeOPTypeTwo.toString());
		
		requirementsStr = "((X>1) && (Y==1) && (X>1) && (Y==1))";
		normalizeOPTypeTwo = RequirementsHelper.extractVariableExpression(toOp(requirementsStr), "X");
		Assert.assertEquals("((X>1)&&(X>1))", normalizeOPTypeTwo.toString());
		
		requirementsStr = "(X>1)";
		Assert.assertNull(RequirementsHelper.extractVariableExpression(toOp(requirementsStr), "A"));		
	}
	
	@Test
	public void testNormalizeOpList() {
		List<String> listAtt = new ArrayList<String>();
		listAtt.add("X");
		listAtt.add("Y");
		String requirementsStr = "(((X>1) && (Y==1)) || Y<10) && Y==10 && X>10";
		Expr normalizeOPTypeTwo = RequirementsHelper.extractVariablesExpression(toOp(requirementsStr), listAtt);
		Assert.assertEquals("(((((X>1)&&(Y==1))||(Y<10))&&(Y==10))&&(X>10))", normalizeOPTypeTwo.toString());

		requirementsStr = "W>=0 && ((((X>1) && (Y==1)) || Y<10) && Y==10 && X>10)";
		normalizeOPTypeTwo = RequirementsHelper.extractVariablesExpression(toOp(requirementsStr), listAtt);
		Assert.assertEquals("(((((X>1)&&(Y==1))||(Y<10))&&(Y==10))&&(X>10))", normalizeOPTypeTwo.toString());
		
		requirementsStr = "(W>=0 || X<=1) && ((((X>1) && (Y==1)) || Y<10) && Y==10 && X>10)";
		normalizeOPTypeTwo = RequirementsHelper.extractVariablesExpression(toOp(requirementsStr), listAtt);
		Assert.assertEquals("((X<=1)&&(((((X>1)&&(Y==1))||(Y<10))&&(Y==10))&&(X>10)))", normalizeOPTypeTwo.toString());
		
		listAtt.clear();
		normalizeOPTypeTwo = RequirementsHelper.extractVariablesExpression(toOp(requirementsStr), listAtt);
		Assert.assertNull(normalizeOPTypeTwo);		
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
		
		requirements = "X==\"id1\" || (X==\"id2\" && X==\"id3\") || X == \"\"";
		attName = "X";
		findValuesInRequiremets = RequirementsHelper.findValuesInRequiremets(toOp(requirements), attName);
		Assert.assertEquals(4, findValuesInRequiremets.size());
		
		requirements = "X==\"id1\" || (X==\"id2\" && X==\"id3\") || X == \"\"";
		attName = "Y";
		findValuesInRequiremets = RequirementsHelper.findValuesInRequiremets(toOp(requirements), attName);
		Assert.assertEquals(0, findValuesInRequiremets.size());		
	}
	
	@Test
	public void testTrueGetSmallestValueForAttribute() {
		String requirementsStr = "true";
		Assert.assertEquals("0", RequirementsHelper.getSmallestValueForAttribute(requirementsStr, RequirementsHelper.GLUE_VCPU_TERM));
	}
	
	@Test
	public void testEmptyGetSmallestValueForAttribute() {
		String requirementsStr = "";
		Assert.assertEquals("0", RequirementsHelper.getSmallestValueForAttribute(requirementsStr, RequirementsHelper.GLUE_VCPU_TERM));
	}	
	
	private Op toOp(String requirementsStr) {
		ClassAdParser classAdParser = new ClassAdParser(requirementsStr);
		return (Op) classAdParser.parse();	 
	}
}
