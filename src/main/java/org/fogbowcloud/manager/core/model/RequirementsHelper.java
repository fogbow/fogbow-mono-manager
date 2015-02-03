package org.fogbowcloud.manager.core.model;

import java.util.List;

import condor.classad.ClassAdParser;
import condor.classad.Expr;
public class RequirementsHelper {

	public static final String GLUE_LOCATION_TERM = "GlueLocationTermExample";
	
	public static boolean checkRequirements(String requirementsString) {
		try {
			ClassAdParser adParser = new ClassAdParser(requirementsString);
			Expr parse = adParser.parse();
			if (parse != null) {
				return true;
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}
	
	public static String normalizeRequirements(String requirementsString) {
		String[] allRequerement = requirementsString.split("&&");
		String newRequirementsString = "";
		 
		for (int i = 0; i < allRequerement.length; i++) {
			if (!allRequerement[i].contains(GLUE_LOCATION_TERM)) {
				newRequirementsString += requirementsString;
				if ((allRequerement.length - 1) != i) {
					newRequirementsString += "&&";
				}
			}
		}
		return newRequirementsString;
	}
	
	public static String getLocationRequirements(String requirementsString) {
		String[] allRequerement = requirementsString.split("&&");
		String location = null;
		for (String requirement : allRequerement) {
			if (requirement.contains(GLUE_LOCATION_TERM) && requirement.contains("==")) {
				return location = requirement.split("==")[1];
			}
		}
		return location;
	}

	public String findFlavor(List<Flavor> flavors) {
		return null;
	}	
}
