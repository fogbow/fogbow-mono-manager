package org.fogbowcloud.manager.occi.core;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class HeaderUtils {

	public static final String REQUEST_DATE_FORMAT = "yyyy-MM-dd";
	public static final String X_OCCI_LOCATION = "X-OCCI-Location: ";
	public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

	public static void checkOCCIContentType(Series<Header> headers) {
		String contentType = headers.getValues(OCCIHeaders.CONTENT_TYPE);
		if (!contentType.equals(OCCIHeaders.OCCI_CONTENT_TYPE)) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	public static String getAuthToken(Series<Header> headers, Response response) {
		return getAuthToken(headers, response, false);
	}
	
	public static String getAuthToken(Series<Header> headers, Response response, boolean head) {
		String token = headers.getValues(OCCIHeaders.X_AUTH_TOKEN);
		if (token == null || token.equals("")) {
			if (response != null) {
				Series<Header> responseHeaders = (Series<Header>) response.getAttributes().get("org.restlet.http.headers");
				if (responseHeaders == null) {
					responseHeaders = new Series(Header.class);
					response.getAttributes().put("org.restlet.http.headers", responseHeaders);
				}
				//FIXME keystone URI hard coded
				responseHeaders.add(new Header(HeaderUtils.WWW_AUTHENTICATE, "Keystone uri='http://localhost:5000/'"));
				MediaType textPlainType = new MediaType("text/plain");				
				response.setEntity(ResponseConstants.UNAUTHORIZED, textPlainType);
			}			
			throw new OCCIException(ErrorType.UNAUTHORIZED, 
					head ? "" : ResponseConstants.UNAUTHORIZED);
		}
		return token;
	}

	public static String getLink(Series<Header> headers) {
		return headers.getValues(OCCIHeaders.LINK);		
	}
	
	public static Map<String, String> getXOCCIAtributes(Series<Header> headers) {
		String[] valuesAttributes = headers.getValuesArray(normalize(OCCIHeaders.X_OCCI_ATTRIBUTE));
		Map<String, String> mapAttributes = new HashMap<String, String>();
		for (int i = 0; i < valuesAttributes.length; i++) {
			String[] tokensAttribute = valuesAttributes[i].split("=");
			if (tokensAttribute.length != 2) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.UNSUPPORTED_ATTRIBUTES);
			}
			String name = tokensAttribute[0].trim();
			String value = tokensAttribute[1].replace("\"", "").trim();
			mapAttributes.put(name, value);
		}
		return mapAttributes;
	}

	public static List<Category> getCategories(Series<Header> headers) {
		List<Category> listCategory = new ArrayList<Category>();
		String[] valuesCategory = headers.getValuesArray(normalize(OCCIHeaders.CATEGORY));
		String term = "";
		String scheme = "";
		String catClass = "";
		for (int i = 0; i < valuesCategory.length; i++) {
			String[] tokenValuesCAtegory = valuesCategory[i].split(";");
			if (tokenValuesCAtegory.length == 3) {
				Category category = null;
				for (int j = 0; j < tokenValuesCAtegory.length; j++) {
					String[] nameValue = tokenValuesCAtegory[j].split("=");
					if (j == 0 && nameValue.length == 1) {
						term = nameValue[0].trim();
					} else if (nameValue[0].trim().equals(OCCIHeaders.SCHEME_CATEGORY)) {
						scheme = nameValue[1].replace("\"", "").trim();
					} else if (nameValue[0].trim().equals(OCCIHeaders.CLASS_CATEGORY)) {
						catClass = nameValue[1].replace("\"", "").trim();
					} else {
						throw new OCCIException(ErrorType.BAD_REQUEST,
								ResponseConstants.IRREGULAR_SYNTAX);
					}
				}
				try {
					category = new Category(term, scheme, catClass);
				} catch (IllegalArgumentException e) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.IRREGULAR_SYNTAX);
				}
				listCategory.add(category);
			} else {
				throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
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

	public static void checkCategories(List<Category> categories, String mandatoryTerm) {
		List<Resource> resources = ResourceRepository.getInstance().get(categories);

		if (resources.size() != categories.size()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		for (Category category : categories) {
			if (category.getTerm().equals(mandatoryTerm)) {
				Resource resource = ResourceRepository.getInstance().get(mandatoryTerm);
				if (resource == null || !resource.matches(category)) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.IRREGULAR_SYNTAX);
				}
				return;
			}
		}
		throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
	}

	public static void checkDateValue(String dataString) {
		try {
			if (dataString != null && !dataString.equals("")) {
				DateFormat formatter = new SimpleDateFormat(REQUEST_DATE_FORMAT);
				formatter.parse(dataString);
			}
		} catch (ParseException e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	public static void checkIntegerValue(String intString) {
		try {
			Integer.parseInt(intString);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

}
