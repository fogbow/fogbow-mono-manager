package org.fogbowcloud.manager.occi.core;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.engine.header.Header;
import org.restlet.util.Series;

public class HeaderUtils {

	private static final Logger LOGGER = Logger.getLogger(HeaderUtils.class);

	public static final String REQUEST_DATE_FORMAT = "yyyy-MM-dd";
	public static final String X_OCCI_LOCATION_PREFIX = "X-OCCI-Location: ";
	public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

	public static void checkOCCIContentType(Series<Header> headers) {
		String contentType = headers.getValues(OCCIHeaders.CONTENT_TYPE);
		if (contentType == null || !contentType.equals(OCCIHeaders.OCCI_CONTENT_TYPE)) {
			LOGGER.debug("Content-type " + contentType + " was not occi.");
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}
	
	public static void checkJsonContentType(Series<Header> headers) {
		String contentType = headers.getValues(OCCIHeaders.CONTENT_TYPE);
		if (contentType == null || !contentType.equals(OCCIHeaders.JSON_CONTENT_TYPE)) {
			LOGGER.debug("Content-type " + contentType + " was not json.");
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static String getAuthToken(Series<Header> headers, Response response, String authenticationURI) {
		String token = headers.getValues(OCCIHeaders.X_AUTH_TOKEN);
		if (token == null || token.equals("")) {
			if (response != null && authenticationURI != null) {
				Series<Header> responseHeaders = (Series<Header>) response.getAttributes().get("org.restlet.http.headers");
				if (responseHeaders == null) {
					responseHeaders = new Series(Header.class);
					response.getAttributes().put("org.restlet.http.headers", responseHeaders);
				}
				responseHeaders.add(new Header(HeaderUtils.WWW_AUTHENTICATE, authenticationURI));
				MediaType textPlainType = new MediaType("text/plain");				
				response.setEntity(ResponseConstants.UNAUTHORIZED, textPlainType);
			}
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		return token;
	}

	public static String getLink(Series<Header> headers) {
		return headers.getValues(OCCIHeaders.LINK);		
	}
	
	public static Map<String, String> getXOCCIAtributes(Series<Header> headers) {
		String[] headerValues = headers.getValuesArray(normalize(OCCIHeaders.X_OCCI_ATTRIBUTE));		
		Map<String, String> mapAttributes = new HashMap<String, String>();
		for (int i = 0; i < headerValues.length; i++) {
			String[] eachHeaderValue = headerValues[i].split(",");			
			for (int j = 0; j < eachHeaderValue.length; j++) {
				String line = eachHeaderValue[j].trim();
				if (!line.contains("=")){
					LOGGER.debug("Attribute not supported or irregular expression. It will be thrown BAD REQUEST error type.");
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.UNSUPPORTED_ATTRIBUTES);
				}
				
				String attName = line.substring(0, line.indexOf("=")).trim();
				String attValue = line.substring(line.indexOf("=") + 1).replace("\"", "").trim();
				mapAttributes.put(attName, attValue);
			}
		}
		LOGGER.debug("OCCI Attributes received: " + mapAttributes);
		return mapAttributes;
	}

	public static List<Category> getCategories(Series<Header> headers) {
		List<Category> categories = new ArrayList<Category>();
		String[] headerValues = headers.getValuesArray(normalize(OCCIHeaders.CATEGORY));
		for (int i = 0; i < headerValues.length; i++) {
			String[] eachHeaderValue = headerValues[i].split(",");
			for (int j = 0; j < eachHeaderValue.length; j++){	
				try {
					categories.add(new Category(eachHeaderValue[j].trim()));
				} catch (IllegalArgumentException e) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.IRREGULAR_SYNTAX);
				}				
			}
		}
		return categories;
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
			LOGGER.debug("Some categories was not found in available resources! Resources "
					+ resources.size() + " and categories " + categories.size());
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		for (Category category : categories) {
			if (category.getTerm().equals(mandatoryTerm)) {
				Resource resource = ResourceRepository.getInstance().get(mandatoryTerm);
				if (resource == null || !resource.matches(category)) {
					LOGGER.debug("There was not a matched resource to term " + mandatoryTerm);
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

	public static List<String> getAccept(Series<Header> headers) {
		String[] headerValues = headers.getValuesArray(normalize(OCCIHeaders.ACCEPT));		
		List<String> acceptContents = new ArrayList<String>();
		for (int i = 0; i < headerValues.length; i++) {
			String[] eachHeaderValue = headerValues[i].split(",");			
			for (int j = 0; j < eachHeaderValue.length; j++) {
				/*
				 * This string represents any value, when any value we'll
				 * consider that accept header was not specified
				 */ 
				if (!"*/*".equals(eachHeaderValue[j].trim())){
					acceptContents.add(eachHeaderValue[j].trim());			
				}
			}
		}		
		return acceptContents;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void setResponseHeader(Response response, String header, String value) {
		Series<Header> responseHeaders = (Series<Header>) response.getAttributes().get("org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Series(Header.class);
			response.getAttributes().put("org.restlet.http.headers", responseHeaders);
		}		
		responseHeaders.add(new Header(normalize(header), value));
	}

	public static List<String> getValueHeaderPerName(String nameHeader,Series<Header> headers) {
		List<String> listValuesHeaders = new ArrayList<String>();
		for (Header header : headers) {
			if (header.getName().equals(HeaderUtils.normalize(nameHeader))) {
				listValuesHeaders.add(header.getValue());
			}
		}
		return listValuesHeaders;
	}	
}
