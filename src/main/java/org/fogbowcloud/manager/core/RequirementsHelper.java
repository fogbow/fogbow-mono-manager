package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fogbowcloud.manager.core.model.Flavor;

import condor.classad.AttrRef;
import condor.classad.ClassAdParser;
import condor.classad.Constant;
import condor.classad.Env;
import condor.classad.Expr;
import condor.classad.Op;
import condor.classad.RecordExpr;


public class RequirementsHelper {
	public static final String GLUE_LOCATION_TERM = "Glue2CloudComputeInstanceTypeLocation";
	public static final String GLUE_VCPU_TERM = "Glue2CloudComputeInstanceTypevCPU";
	public static final String GLUE_DISK_TERM = "Glue2CloudComputeInstanceTypeDisk";
	public static final String GLUE_MEM_RAM_TERM = "Glue2CloudComputeInstanceTypeRAM";

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

	public static String getValueSmallerPerAttribute(String requirementsStr, String attrName) {
		ClassAdParser classAdParser = new ClassAdParser(requirementsStr);		
		Op expr = (Op) classAdParser.parse();		
		Op opForAtt = normalizeOPTypeTwo(expr, attrName);
		
		List<Integer> values = new ArrayList<Integer>();
		List<ValueAndOperator> findValuesInRequiremets = findValuesInRequiremets(expr, attrName);
		for (ValueAndOperator valueAndOperator : findValuesInRequiremets) {
			int value = Integer.parseInt(valueAndOperator.getValue());
			if (checkValue(opForAtt, attrName, String.valueOf(value - 1))) {
				values.add(value - 1);
			} else if (checkValue(opForAtt, attrName, String.valueOf(value))) {
				values.add(value);
			} else if (checkValue(opForAtt, attrName, String.valueOf(value + 1))) {
				values.add(value + 1);
			}
		}
		
		Collections.sort(values);
		
		if (values.size() > 0) {
			return String.valueOf(values.get(0));			
		}
		return null;
	}
	
	private static boolean checkValue(Op op, String attrName, String value) {
		Env env = new Env();
		env.push((RecordExpr) new ClassAdParser("[" + attrName + " = " + value + "]").parse());
		return op.eval(env).isTrue();		
	}
	
	public static boolean checkFlavorPerRequirements(Flavor flavor, String requirementsStr) {
		try {
			ClassAdParser classAdParser = new ClassAdParser(requirementsStr);		
			Op expr = (Op) classAdParser.parse();
			
			List<String> listAttrSearched = new ArrayList<String>();
			List<String> listAttrProvided = new ArrayList<String>();
			listAttrProvided.add(RequirementsHelper.GLUE_DISK_TERM);
			listAttrProvided.add(RequirementsHelper.GLUE_MEM_RAM_TERM);
			listAttrProvided.add(RequirementsHelper.GLUE_VCPU_TERM);
			
			Env env = new Env();
			String value = null;
			for (String attr : listAttrProvided) {
				List<ValueAndOperator> findValuesInRequiremets = findValuesInRequiremets(expr, attr);
				if (findValuesInRequiremets.size() > 0) {
					if (attr.equals(RequirementsHelper.GLUE_DISK_TERM)) {
						value = flavor.getDisk();
						if (value != null && !value.equals("0") ) {
							listAttrSearched.add(attr);							
						}
					} else if (attr.equals(RequirementsHelper.GLUE_MEM_RAM_TERM)) {
						listAttrSearched.add(attr);
						value = flavor.getMem();
					} else if (attr.equals(RequirementsHelper.GLUE_VCPU_TERM)) {
						listAttrSearched.add(attr);
						value = flavor.getCpu();
					}
					env.push((RecordExpr) new ClassAdParser("[" + attr + " = " + value + "]").parse());
				}
			}
			
			
			classAdParser = new ClassAdParser(requirementsStr);
			expr = (Op) classAdParser.parse();
			expr = normalizeOPTypeTwo(expr, listAttrSearched);
			
			return expr.eval(env).isTrue();
		} catch (Exception e) {
			return false;
		}
	}
	
	public static Flavor findFlavor(List<Flavor> flavors, String requirementsStr) {
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

		return listFlavor.get(0);
	}	

	public static Op normalizeOP(Op expr, String attName) {
		if (expr.arg1 instanceof AttrRef) {
			AttrRef attr = (AttrRef) expr.arg1;
			if (!attr.name.rawString().equals(attName)) {
				return new Op(Op.EQUAL, Constant.TRUE, Constant.TRUE);
			}
			return expr;
		}
		Expr left = expr.arg1;
		if (left instanceof Op) {
			left = normalizeOP((Op) expr.arg1, attName);
		}
		Expr right = expr.arg2;
		if (right instanceof Op) {
			right = normalizeOP((Op) expr.arg2, attName);
		}
		return new Op(expr.op, left, right);
	}
	
	public static Op normalizeOPTypeTwo(Op expr, String attName) {
		if (expr.arg1 instanceof AttrRef) {
			AttrRef attr = (AttrRef) expr.arg1;
			if (!attr.name.rawString().equals(attName)) {
				return null;
			}
			return expr;
		}
		Expr left = expr.arg1;
		if (left instanceof Op) {
			left = normalizeOPTypeTwo((Op) expr.arg1, attName);
		}
		Expr right = expr.arg2;
		if (right instanceof Op) {
			right = normalizeOPTypeTwo((Op) expr.arg2, attName);
		}
		try {
			if (left == null) {
				return (Op) right;
			} else if (right == null) {
				return (Op) left;
			}			
		} catch (Exception e) {	
			return null;
		}
		return new Op(expr.op, left, right);
	}
	
	public static Op normalizeOPTypeTwo(Op expr, List<String> listAttName) {
		if (expr.arg1 instanceof AttrRef) {
			AttrRef attr = (AttrRef) expr.arg1;
			boolean thereIs = false;
			for (String attName : listAttName) {
				if (attr.name.rawString().equals(attName)) {
					thereIs = true;
				}
			}
			if (thereIs) {
				return expr;				
			}
			return null;
		}
		Expr left = expr.arg1;
		if (left instanceof Op) {
			left = normalizeOPTypeTwo((Op) expr.arg1, listAttName);
		}
		Expr right = expr.arg2;
		if (right instanceof Op) {
			right = normalizeOPTypeTwo((Op) expr.arg2, listAttName);
		}
		try {
			if (left == null) {
				return (Op) right;
			} else if (right == null) {
				return (Op) left;
			}			
		} catch (Exception e) {
			return null;
		}
		return new Op(expr.op, left, right);
	}

	protected static String normalizeLocationToCheck(String location) {
		if (location == null) {
			return null;
		}
		if (!location.startsWith("\"")) {
			location = "\"" + location;
		}
		if (!location.endsWith("\"")) {
			location = location + "\"";
		}
		return location;
	}
	
	public static boolean checkLocation(String requirementsStr, String valueLocation) {
		if (requirementsStr == null) {
			return false;
		}		
		ClassAdParser classAdParser = new ClassAdParser(requirementsStr);
		Op expr = (Op) classAdParser.parse();

		valueLocation = normalizeLocationToCheck(valueLocation);
		Env env = new Env();
		env.push((RecordExpr) new ClassAdParser("[" + GLUE_LOCATION_TERM + " = " + valueLocation
				+ "]").parse());

		Op opForAtt = normalizeOPTypeTwo(expr, GLUE_LOCATION_TERM);
		
		if (opForAtt == null) {
			return false;
		}
		return opForAtt.eval(env).isTrue(); 
	}

	public static List<ValueAndOperator> findValuesInRequiremets(Op expr, String attName) {
		List<ValueAndOperator> valuesAndOperator = new ArrayList<ValueAndOperator>();
		if (expr.arg1 instanceof AttrRef) {
			AttrRef attr = (AttrRef) expr.arg1;
			if (attr.name.rawString().equals(attName)) {
				valuesAndOperator.add(new ValueAndOperator(expr.arg2.toString(), expr.op));
			}
			return valuesAndOperator;
		}
		if (expr.arg1 instanceof Op) {
			List<ValueAndOperator> findValuesInRequiremets = findValuesInRequiremets(
					(Op) expr.arg1, attName);
			if (findValuesInRequiremets != null) {
				valuesAndOperator.addAll(findValuesInRequiremets);
			}
		}
		if (expr.arg2 instanceof Op) {
			List<ValueAndOperator> findValuesInRequiremets = findValuesInRequiremets(
					(Op) expr.arg2, attName);
			if (findValuesInRequiremets != null) {
				valuesAndOperator.addAll(findValuesInRequiremets);
			}
		}
		return valuesAndOperator;
	}

	public static List<String> getLocationsInRequiremets(String requirementsStr) {
		ClassAdParser classAdParser = new ClassAdParser(requirementsStr);
		Op expr = (Op) classAdParser.parse();
		List<ValueAndOperator> findValuesInRequiremets = findValuesInRequiremets(expr,
				GLUE_LOCATION_TERM);
		List<String> locations = new ArrayList<String>();
		for (ValueAndOperator valueAndOperator : findValuesInRequiremets) {
			if (valueAndOperator.getOperator() == RecordExpr.EQUAL) {
				locations.add(valueAndOperator.getValue());
			}
		}
		return locations;
	}

	public static class ValueAndOperator {
		private String value;
		private int operator;

		public ValueAndOperator(String value, int operator) {
			this.value = value;
			this.operator = operator;
		}

		public int getOperator() {
			return operator;
		}

		public String getValue() {
			return value;
		}
	}

	public static class FlavorComparator implements Comparator<Flavor> {
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
			if (calculateRelevance(flavorOne, flavorTwo) == calculateRelevance(flavorTwo, flavorOne)) {
				try {
					if (Double.parseDouble(flavorOne.getDisk()) > Double.parseDouble(flavorTwo.getDisk())) {
						return 1;
					} else {
						return -1;
					}
				} catch (Exception e) {
				}
			}
			return 0;
		}

		public double calculateRelevance(Flavor flavorOne, Flavor flavorTwo) {
			double cpuOne = Double.parseDouble(flavorOne.getCpu());
			double cpuTwo = Double.parseDouble(flavorTwo.getCpu());
			double memOne = Double.parseDouble(flavorOne.getMem());
			double memTwo = Double.parseDouble(flavorTwo.getMem());

			return ((cpuOne / cpuTwo) * 1 / VCPU_VALUE_RELEVANCE)
					+ ((memOne / memTwo) * 1 / MEM_VALUE_RELEVANCE);
		}
	}
}
