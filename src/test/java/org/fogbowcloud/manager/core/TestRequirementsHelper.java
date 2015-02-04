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
	public void TestNormalizeRequirements() {
		String sufix = "&&" + RequirementsHelper.GLUE_LOCATION_TERM + "==\"x\"";
		String prefix = "X == 1 && Y >=0 ";

		String requirementsStr = prefix + sufix;
		String normalizedRequirements = requirementsHelper.normalizeRequirements(requirementsStr);
		Assert.assertEquals(prefix, normalizedRequirements);
	}

	@SuppressWarnings("static-access")
	@Test
	public void TestGetLocationRequirements() {
		String valueLocation = "default";
		String requirementsStr = "X == 1 && Y >=0 && " + RequirementsHelper.GLUE_LOCATION_TERM
				+ " == \"" + valueLocation + "\"";
		String locationRequirements = requirementsHelper.getLocationRequirements(requirementsStr);
		Assert.assertEquals(valueLocation, locationRequirements);
	}

	@SuppressWarnings("static-access")
	@Test
	public void TestGetNullLocationRequirements() {
		String requirementsStr = "X == 1 && Y >=0 && ";
		String requirementsStrTwo = "X == 1 && Y >=0 && " + RequirementsHelper.GLUE_LOCATION_TERM
				+ " >= \"" + "x" + "\"";
		String requirementsStrThree = "X == 1 && Y >=0 & " + RequirementsHelper.GLUE_LOCATION_TERM
				+ " >= \"" + "x" + "\"";

		Assert.assertNull(requirementsHelper.getLocationRequirements(requirementsStr));
		Assert.assertNull(requirementsHelper.getLocationRequirements(requirementsStrTwo));
		Assert.assertNull(requirementsHelper.getLocationRequirements(requirementsStrThree));
	}

	@Test
	public void TestCheckFlavor() {
		Flavor flavor = new Flavor("test", "12", "400", "11");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10";
		Assert.assertTrue(requirementsHelper.checkFlavorPerRequirements(flavor, requirementsStr));
	}

	@Test
	public void TestCheckInvalidFlavor() {
		Flavor flavor = new Flavor("test", "12", "400", "9");
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " > 10 && " + mem + " < 500 && " + vCpu + " >= 10";
		Assert.assertFalse(requirementsHelper.checkFlavorPerRequirements(flavor, requirementsStr));
	}

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
