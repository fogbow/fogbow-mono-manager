package org.fogbowcloud.manager.occi.core;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.fogbowcloud.manager.occi.request.RequestUnit;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class HeaderUtils {

	public static final String X_OCCI_LOCATION = "X-OCCI-Location: ";

	public static void checkFogbowHeaders(Series<Header> headers) {
		checkOCCIContentType(headers);
		List<Category> listCategory = getListCategory(headers);
		validateRequestCategory(listCategory);
	}

	public static String generateResponseId(List<RequestUnit> requestUnits, HttpRequest req) {
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		String response = "";
		for (RequestUnit request : requestUnits) {
			response += X_OCCI_LOCATION + requestEndpoint + "/" + request.getId();
		}
		if (response.equals("")) {
			response = "Empty";
		}
		return response;
	}

	public static void checkOCCIContentType(Series<Header> headers) {
		String contentType = headers.getValues(OCCIHeaders.CONTENT_TYPE);
		if (!contentType.equals(OCCIHeaders.OCCI_CONTENT_TYPE)) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
		}
	}

	public static int getNumberOfInstances(Series<Header> headers) {
		Map<String, String> map = getXOCCIAtributes(headers);
		try {
			String instances = map.get(RequestAttribute.INSTANCE_COUNT.getValue());
			if (instances == null || instances.equals("")) {
				return FogbowResourceConstants.DEFAULT_INSTANCE_COUNT;
			} else {
				return Integer.parseInt(map.get(RequestAttribute.INSTANCE_COUNT.getValue()));
			}
		} catch (NumberFormatException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
		}
	}

	public static String getToken(Series<Header> headers) {
		String token = headers.getValues(OCCIHeaders.X_AUTH_TOKEN);
		if (token == null || token.equals("")) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, "Authentication required.");
		}
		return token;
	}

	// public static String getAttType(Series<Header> headers) {
	// Map<String, String> map = getXOCCIAtributes(headers);
	// String type =
	// map.get(FogbowResourceConstants.ATRIBUTE_TYPE_FOGBOW_REQUEST);
	// if (type == null || type.equals("")) {
	// return null;
	// } else {
	// for (int i = 0; i < RequestType.values().length; i++) {
	// if (type.equals(RequestType.values()[i].getValue())) {
	// return type;
	// }
	// }
	// throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
	// }
	//
	// }

	// public static Date getAttValidFrom(Series<Header> headers) {
	// Map<String, String> map = getXOCCIAtributes(headers);
	// try {
	// String dataString =
	// map.get(FogbowResourceConstants.ATRIBUTE_VALID_FROM_FOGBOW_REQUEST);
	// if (dataString != null && !dataString.equals("")) {
	// DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
	// return (Date) formatter.parse(dataString);
	// }
	// return null;
	// } catch (ParseException e) {
	// throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
	// }
	// }
	//
	// public static Date getAttValidUntil(Series<Header> headers) {
	// Map<String, String> map = getXOCCIAtributes(headers);
	// try {
	// String dataString = map
	// .get(FogbowResourceConstants.ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST);
	// if (dataString != null && !dataString.equals("")) {
	// DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
	// return (Date) formatter.parse(dataString);
	// }
	// return null;
	// } catch (ParseException e) {
	// throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
	// }
	// }

	public static Map<String, String> getXOCCIAtributes(Series<Header> headers) {
		String[] valuesAttributes = headers.getValuesArray(normalize(OCCIHeaders.X_OCCI_ATTRIBUTE));
		Map<String, String> mapAttributes = new HashMap<String, String>();
		for (int i = 0; i < valuesAttributes.length; i++) {
			String[] tokensAttribute = valuesAttributes[i].split("=");
			if (tokensAttribute.length != 2) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						"There are unsupported attributes in the request.");
			}
			// String name = checkFogBowAttributes(tokensAttribute[0].trim());
			String name = tokensAttribute[0].trim();
			String value = tokensAttribute[1].replace("\"", "").trim();
			mapAttributes.put(name, value);
		}
		return mapAttributes;
	}

	// public static String checkFogBowAttributes(String nameAttribute) {
	// FogbowRequestAttributes[] attributesFogbowResquest =
	// FogbowRequestAttributes.values();
	// for (int i = 0; i < attributesFogbowResquest.length; i++) {
	// if (nameAttribute.equals(attributesFogbowResquest[i].getValue())) {
	// return nameAttribute;
	// }
	// }
	// throw new OCCIException(ErrorType.BAD_REQUEST, "Attribute not found");
	// }

	public static void validateRequestCategory(List<Category> listCategory) {
		for (Category category : listCategory) {
			if (category.getTerm().equals(FogbowResourceConstants.TERM)) {
				if (validateCategory(category) == false) {
					throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
				}
				return;
			}
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
	}

	public static boolean validateCategory(Category category) {
		if (category.getTerm().equals(FogbowResourceConstants.TERM)
				&& category.getCatClass().equals(FogbowResourceConstants.CLASS)
				&& category.getScheme().equals(FogbowResourceConstants.SCHEME)) {
			return true;
		}
		return false;
	}

	public static List<Category> getListCategory(Series<Header> headers) {
		List<Category> listCategory = new ArrayList<Category>();
		String[] valuesCategory = headers.getValuesArray(normalize(OCCIHeaders.CATEGORY));
		String term = "";
		String scheme = "";
		String catClass = "";
		for (int i = 0; i < valuesCategory.length; i++) {
			String[] tokenValuesCAtegory = valuesCategory[i].split(";");
			if (tokenValuesCAtegory.length > 2) {
				Category category = null;
				for (int j = 0; j < tokenValuesCAtegory.length; j++) {
					String[] nameValue = tokenValuesCAtegory[j].split("=");
					if (nameValue.length == 1) {
						term = nameValue[0].trim();
					} else if (nameValue[0].trim().equals(OCCIHeaders.SCHEME_CATEGORY)) {
						scheme = nameValue[1].replace("\"", "").trim();
					} else if (nameValue[0].trim().equals(OCCIHeaders.CLASS_CATEGORY)) {
						catClass = nameValue[1].replace("\"", "").trim();
					} else {
						throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
					}
				}
				try {
					category = new Category(term, scheme, catClass);
				} catch (IllegalArgumentException e) {
					throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
				}
				listCategory.add(category);
			} else {
				throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
			}
		}
		return listCategory;
	}

	public static String normalize(String headerName) {
		String lowerHeader = headerName.toLowerCase();
		char[] lowerHeaderArray = lowerHeader.toCharArray();
		lowerHeaderArray[0] = Character.toUpperCase(lowerHeaderArray[0]);
		return new String(lowerHeaderArray);
	}

	public static List<FogbowResource> getPossibleResources() {
		FogbowResource fogbowRequest = new FogbowResource(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, FogbowResourceConstants.CLASS,
				RequestAttribute.getValues(), new ArrayList<String>(), "$EndPoint/request",
				"Request new Instances", "");

		List<FogbowResource> resources = new ArrayList<FogbowResource>();
		resources.add(fogbowRequest);
		return resources;
	}

	public static void checkXOCCIAtt(Map<String, String> xOCCIAtt) {

		for (String attName : xOCCIAtt.keySet()) {
			if (attName.equals(RequestAttribute.TYPE.getValue())) {
				checkTypeValue(xOCCIAtt.get(attName));
			} else if (attName.equals(RequestAttribute.VALID_FROM.getValue())
					|| attName.equals(RequestAttribute.VALID_UNTIL.getValue())) {
				checkDateValue(xOCCIAtt.get(attName));
			}

		}
	}

	private static void checkDateValue(String dataString) {
		// TODO Auto-generated method stub
		try {
			if (dataString != null && !dataString.equals("")) {
				DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
				formatter.parse(dataString);
			}
		} catch (ParseException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
		}
	}

	private static void checkTypeValue(String typeValue) {
		for (int i = 0; i < RequestType.values().length; i++) {
			if (typeValue.equals(RequestType.values()[i].getValue())) {
				return;
			}
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
	}

	public static Map<String, String> addDefaultValuesOnXOCCIAtt(Map<String, String> xOCCIAtt) {
		// TODO Auto-generated method stub
		return null;
	}

}
