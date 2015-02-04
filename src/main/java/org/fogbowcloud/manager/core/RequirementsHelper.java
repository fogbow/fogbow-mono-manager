package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fogbowcloud.manager.core.model.Flavor;

import condor.classad.ClassAdParser;
import condor.classad.Env;
import condor.classad.Expr;
import condor.classad.Op;
import condor.classad.RecordExpr;

public class RequirementsHelper {

	public static final String GLUE_LOCATION_TERM = "GlueLocationTermExample";
	public static final String GLUE_VCPU_TERM = "GlueVCPUTermExample";
	public static final String GLUE_DISK_TERM = "GlueDISKTermExample";
	public static final String GLUE_MEM_RAM_TERM = "GlueMemRAMTermExample";
	public static final String[] EXPRESSIONS_CLASSAD = new String[] { "==", ">=", "<=", ">", "<",
			"!=" };

	public static boolean checkRequirements(String requirementsString) {
		try {
			ClassAdParser adParser = new ClassAdParser(requirementsString);
			Op expr = (Op) adParser.parse();
			if (expr != null) {
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
				newRequirementsString += allRequerement[i];
				if ((allRequerement.length - 2) != i) {
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
				return location = requirement.split("==")[1].replace("\"", "").trim();
			}
		}
		return location;
	}

	public boolean checkFlavorPerRequirements(Flavor flavor, String requirementsStr) {
		String[] listAttrRequirements = requirementsStr.split("&&");
		Env env = new Env();
		for (String attrRequirements : listAttrRequirements) {
			String[] valueMoreValue = null;
			for (int i = 0; i < EXPRESSIONS_CLASSAD.length; i++) {
				valueMoreValue = attrRequirements.split(EXPRESSIONS_CLASSAD[i]);
				if (valueMoreValue.length > 1) {
					break;
				}
			}
			String value = null;
			if (attrRequirements.contains(RequirementsHelper.GLUE_DISK_TERM)) {
				value = flavor.getDisk();
			} else if (attrRequirements.contains(RequirementsHelper.GLUE_MEM_RAM_TERM)) {
				value = flavor.getMem();
			} else if (attrRequirements.contains(RequirementsHelper.GLUE_VCPU_TERM)) {
				value = flavor.getCpu();
			}
			env.push((RecordExpr) new ClassAdParser("[" + valueMoreValue[0] + " = " + value + "]")
					.parse());
		}

		ClassAdParser classAdParser = new ClassAdParser(requirementsStr);
		Op expr = (Op) classAdParser.parse();
		Expr eval = expr.eval(env);

		return eval.isTrue();
	}

	public String findFlavor(List<Flavor> flavors, String requirementsStr) {
		List<Flavor> listFlavor = new ArrayList<Flavor>();
		for (Flavor flavor : flavors) {
			if (checkFlavorPerRequirements(flavor, requirementsStr)) {
				listFlavor.add(flavor);
			}
		}

		if (listFlavor.size() == 0) {
			return null;
		}

		Collections.sort(listFlavor, new FlavorComparator());

		return listFlavor.get(0).getName();
	}

	public class FlavorComparator implements Comparator<Flavor> {
		private final int DISK_VALUE_RELEVANCE = 1;
		private final int MEM_VALUE_RELEVANCE = 1;
		private final int VCPU_VALUE_RELEVANCE = 1;

		@Override
		public int compare(Flavor flavorOne, Flavor flavorTwo) {
			if (calculateRelevance(flavorOne, flavorTwo) > calculateRelevance(flavorTwo, flavorOne)) {
				return 1;
			}
			if (calculateRelevance(flavorOne, flavorTwo) < calculateRelevance(flavorTwo, flavorOne)) {
				return -1;
			}
			return 0;
		}

		public double calculateRelevance(Flavor flavorOne, Flavor flavorTwo) {
			double cpuOne = Double.parseDouble(flavorOne.getCpu());
			double cpuTwo = Double.parseDouble(flavorTwo.getCpu());
			double memOne = Double.parseDouble(flavorOne.getMem());
			double memTwo = Double.parseDouble(flavorTwo.getMem());
			double diskOne = Double.parseDouble(flavorOne.getDisk());
			double diskTwo = Double.parseDouble(flavorTwo.getDisk());

			return ((cpuOne / cpuTwo) * 1 / VCPU_VALUE_RELEVANCE)
					+ ((memOne / memTwo) * 1 / MEM_VALUE_RELEVANCE)
					+ ((diskOne / diskTwo) * 1 / DISK_VALUE_RELEVANCE);
		}
	}
}
