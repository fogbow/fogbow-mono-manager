package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.core.model.Flavor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
		Assert.assertTrue(requirementsHelper.checkLocation(requirementsStr, value));

		value = "valueCorrect";
		requirementsStr = RequirementsHelper.GLUE_LOCATION_TERM + "==" + "\"" + value + "\"";
		Assert.assertTrue(requirementsHelper.checkLocation(requirementsStr, value));
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
	public void TestFindFlavor() {
		String firstValue = "One";
		Flavor flavorOne = new Flavor(firstValue, "1", "100", "10");
		Flavor flavorTwo = new Flavor("Two", "2", "200", "20");
		Flavor flavorThree = new Flavor("Three", "30", "300", "30");

		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(flavorThree);
		flavors.add(flavorOne);
		flavors.add(flavorTwo);

		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 5 && " + mem + " > 50 && " + vCpu + " > 0";

		Assert.assertEquals(firstValue, requirementsHelper.findFlavor(flavors, requirementsStr));
	}
}
