package org.fogbowcloud.manager.occi.core;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class FogbowUtils {

	public static final String X_OCCI_LOCATION = "X-OCCI-Location: ";

	public static void checkFogbowHeaders(Series<Header> headers) {
		validateContentType(headers);
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

	public static void validateContentType(Series<Header> headers) {
		String contentType = headers.getValues(HeaderConstants.CONTENT_TYPE);
		if (!contentType.equals(HeaderConstants.OCCI_CONTENT_TYPE)) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
		}
	}

	public static int getAttributeInstances(Series<Header> headers) {
		Map<String, String> map = getAtributes(headers);
		try {
			String instances = map.get(FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST);
			if (instances == null || instances.equals("")) {
				return FogbowResourceConstants.DEFAULT_VALUE_INSTANCES_FOGBOW_REQUEST;
			} else {
				return Integer.parseInt(map
						.get(FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST));
			}
		} catch (NumberFormatException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
		}
	}

	public static String getToken(Series<Header> headers) {
		String token = headers.getValues(HeaderConstants.X_AUTH_TOKEN);
		if (token == null || token.equals("")) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, "Authentication required.");
		}
		return token;
	}

	public static String getAttType(Series<Header> headers) {
		Map<String, String> map = getAtributes(headers);
		String type = map.get(FogbowResourceConstants.ATRIBUTE_TYPE_FOGBOW_REQUEST);
		if (type == null || type.equals("")) {
			return null;
		} else {
			for (int i = 0; i < RequestType.values().length; i++) {
				if (type.equals(RequestType.values()[i].getValue())) {
					return type;
				}
			}
			throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
		}

	}

	public static Date getAttValidFrom(Series<Header> headers) {
		Map<String, String> map = getAtributes(headers);
		try {
			String dataString = map.get(FogbowResourceConstants.ATRIBUTE_VALID_FROM_FOGBOW_REQUEST);
			if (dataString != null && !dataString.equals("")) {
				DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
				return (Date) formatter.parse(dataString);
			}
			return null;
		} catch (ParseException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
		}
	}

	public static Date getAttValidUntil(Series<Header> headers) {
		Map<String, String> map = getAtributes(headers);
		try {
			String dataString = map
					.get(FogbowResourceConstants.ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST);
			if (dataString != null && !dataString.equals("")) {
				DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
				return (Date) formatter.parse(dataString);
			}
			return null;
		} catch (ParseException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
		}
	}

	public static Map<String, String> getAtributes(Series<Header> headers) {
		String[] valuesAttributes = headers
				.getValuesArray(normalize(HeaderConstants.X_OCCI_ATTRIBUTE));
		Map<String, String> mapAttributes = new HashMap();
		for (int i = 0; i < valuesAttributes.length; i++) {
			String[] tokensAttribute = valuesAttributes[i].split("=");
			if (tokensAttribute.length != 2) {
				throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
			}
			String name = checkFogBowAttributes(tokensAttribute[0].trim());
			String value = tokensAttribute[1].replace("\"", "").trim();
			mapAttributes.put(name, value);
		}
		return mapAttributes;
	}

	public static String checkFogBowAttributes(String nameAttribute) {
		FogbowRequestAttributes[] attributesFogbowResquest = FogbowRequestAttributes.values();
		for (int i = 0; i < attributesFogbowResquest.length; i++) {
			if (nameAttribute.equals(attributesFogbowResquest[i].getValue())) {
				return nameAttribute;
			}
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, "Attribute not found");
	}

	public static void validateRequestCategory(List<Category> listCategory) {
		for (Category category : listCategory) {
			if (category.getTerm().equals(FogbowResourceConstants.TERM_FOGBOW_REQUEST)) {
				if (validateCategory(category) == false) {
					throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
				}
				return;
			}
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, "Irregular Syntax.");
	}

	public static boolean validateCategory(Category category) {
		if (category.getTerm().equals(FogbowResourceConstants.TERM_FOGBOW_REQUEST)
				&& category.getCatClass().equals(FogbowResourceConstants.CLASS_FOGBOW_REQUEST)
				&& category.getScheme().equals(FogbowResourceConstants.SCHEME_FOGBOW_REQUEST)) {
			return true;
		}
		return false;
	}

	public static List<Category> getListCategory(Series<Header> headers) {
		List<Category> listCategory = new ArrayList();
		String[] valuesCategory = headers.getValuesArray(normalize(HeaderConstants.CATEGORY));
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
					} else if (nameValue[0].trim().equals(HeaderConstants.SCHEME_CATEGORY)) {
						scheme = nameValue[1].replace("\"", "").trim();
					} else if (nameValue[0].trim().equals(HeaderConstants.CLASS_CATEGORY)) {
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

}
