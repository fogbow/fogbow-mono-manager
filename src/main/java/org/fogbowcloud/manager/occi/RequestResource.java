package org.fogbowcloud.manager.occi;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.occi.core.FogbowUtils;
import org.fogbowcloud.manager.occi.core.RequestState;
import org.fogbowcloud.manager.occi.core.RequestType;
import org.fogbowcloud.manager.occi.core.RequestUnit;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class RequestResource extends ServerResource {

	public static final String X_OCCI_LOCATION = "X-OCCI-Location: ";

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();

		HttpRequest req = (HttpRequest) getRequest();
		String userToken = req.getHeaders().getValues(HeaderConstants.X_AUTH_TOKEN);
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();

		return generateResponseId(application.getRequestsFromUser(userToken), requestEndpoint);
	}

	@Delete
	public String remove() {
		// try {
		// OCCIApplication application = (OCCIApplication) getApplication();
		// Map<String, List<RequestUnit>> userToRequests =
		// application.getUserToRequest();
		//
		// HttpRequest req = (HttpRequest) getRequest();
		// String token =
		// req.getHeaders().getValues(HeaderConstants.X_AUTH_TOKEN);
		//
		// if (application.getIdentityPlugin().isValidToken(token)) {
		//
		// if (userToRequests.get(token) == null) {
		// userToRequests.put(token, new ArrayList<RequestUnit>());
		// }
		//
		// userToRequests.get(token).clear();
		//
		// return "removed";
		// } else {
		// setStatus(new Status(HttpStatus.SC_UNAUTHORIZED));
		// return "Without authorization";
		// }
		// } catch (Exception e) {
		// e.printStackTrace();
		// setStatus(new Status(HttpStatus.SC_BAD_REQUEST));
		// return "error";
		// }
		return null;
	}

	@Post
	public String post() {
		try {
			OCCIApplication application = (OCCIApplication) getApplication();

			// getting token
			HttpRequest req = (HttpRequest) getRequest();
			String userToken = req.getHeaders().getValues(HeaderConstants.X_AUTH_TOKEN);

			String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
			
			//TODO 
			FogbowUtils.checkFogbowHeaders(req.getHeaders());

						
			// checking fogbow headers
			validateContentType(req);

			List<Category> listCategory = getListCategory(req);

			validateRequestCategory(listCategory);
			Map<String, String> mapAttributes = getAtributes(req);
			int numberInstanceRequest = getAttributeInstances(mapAttributes);
			String typeResques = getAttributeType(mapAttributes);
			Date validFromRequest = getAttributeValidFrom(mapAttributes);
			Date validUntilRequest = getAttributeValidUntil(mapAttributes);

			List<RequestUnit> currentRequestUnits = new ArrayList<RequestUnit>();
			for (int i = 0; i < numberInstanceRequest; i++) {
				String requestId = String.valueOf(UUID.randomUUID());
				RequestUnit requestUnit = new RequestUnit(requestId, "", RequestState.OPEN);
				currentRequestUnits.add(requestUnit);

				application.newRequest(userToken, requestUnit, req);

			}
			return generateResponseId(currentRequestUnits, requestEndpoint);
		} catch (IrregularSyntaxOCCIExtException e) {
			setStatus(new Status(HttpStatus.SC_BAD_REQUEST));
			e.printStackTrace();
			return "Irregular Syntax";
		} catch (IllegalArgumentException e) {
			setStatus(new Status(HttpStatus.SC_BAD_REQUEST));
			e.printStackTrace();
			return "Irregular Syntax";
		}
	}

	private String generateResponseId(List<RequestUnit> requestUnits, String requestEndpoint) {

		String response = "";
		for (RequestUnit request : requestUnits) {
			response += X_OCCI_LOCATION + requestEndpoint + "/" + request.getId();
		}
		if (response.equals("")) {
			response = "Empty";
		}
		return response;
	}

	private void validateContentType(HttpRequest req) throws IrregularSyntaxOCCIExtException {
		String contentType = req.getHeaders().getValues(HeaderConstants.CONTENT_TYPE);
		if (!contentType.equals(HeaderConstants.OCCI_CONTENT_TYPE)) {
			throw new IrregularSyntaxOCCIExtException();
		}
	}

	protected int getAttributeInstances(Map<String, String> map)
			throws IrregularSyntaxOCCIExtException {
		try {
			String instances = map.get(FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST);
			if (instances == null || instances.equals("")) {
				return FogbowResourceConstants.DEFAULT_VALUE_INSTANCES_FOGBOW_REQUEST;
			} else {
				return Integer.parseInt(map
						.get(FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST));
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
			throw new IrregularSyntaxOCCIExtException();
		}
	}

	protected String getAttributeType(Map<String, String> map)
			throws IrregularSyntaxOCCIExtException {
		String type = map.get(FogbowResourceConstants.ATRIBUTE_TYPE_FOGBOW_REQUEST);
		if (type == null || type.equals("")) {
			return null;
		} else {
			for (int i = 0; i < RequestType.values().length; i++) {
				if (type.equals(RequestType.values()[i].getValue())) {
					return type;
				}
			}
			throw new IrregularSyntaxOCCIExtException();
		}

	}

	protected Date getAttributeValidFrom(Map<String, String> map)
			throws IrregularSyntaxOCCIExtException {
		try {
			String dataString = map.get(FogbowResourceConstants.ATRIBUTE_VALID_FROM_FOGBOW_REQUEST);
			if (dataString != null && !dataString.equals("")) {
				DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
				return (Date) formatter.parse(dataString);
			}
			return null;
		} catch (ParseException e) {
			e.printStackTrace();
			throw new IrregularSyntaxOCCIExtException();
		}
	}

	protected Date getAttributeValidUntil(Map<String, String> map)
			throws IrregularSyntaxOCCIExtException {
		try {
			String dataString = map
					.get(FogbowResourceConstants.ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST);
			if (dataString != null && !dataString.equals("")) {
				DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
				return (Date) formatter.parse(dataString);
			}
			return null;
		} catch (ParseException e) {
			throw new IrregularSyntaxOCCIExtException();
		}
	}

	private Map<String, String> getAtributes(HttpRequest req)
			throws IrregularSyntaxOCCIExtException {
		String[] valuesAttributes = req.getHeaders().getValuesArray(
				normalize(HeaderConstants.X_OCCI_ATTRIBUTE));
		Map<String, String> mapAttributes = new HashMap();
		for (int i = 0; i < valuesAttributes.length; i++) {
			String[] tokensAttribute = valuesAttributes[i].split("=");
			if (tokensAttribute.length != 2) {
				throw new IrregularSyntaxOCCIExtException();
			}
			String name = tokensAttribute[0].trim();
			String value = tokensAttribute[1].replace("\"", "").trim();
			mapAttributes.put(name, value);
		}
		return mapAttributes;
	}

	private void validateRequestCategory(List<Category> listCategory)
			throws IrregularSyntaxOCCIExtException {
		for (Category category : listCategory) {
			if (category.getTerm().equals(FogbowResourceConstants.TERM_FOGBOW_REQUEST)) {
				if (validateCategory(category) == false) {
					throw new IrregularSyntaxOCCIExtException();
				}
				return;
			}
		}
		throw new IrregularSyntaxOCCIExtException();
	}

	private boolean validateCategory(Category category) {
		if (category.getTerm().equals(FogbowResourceConstants.TERM_FOGBOW_REQUEST)
				&& category.getCatClass().equals(FogbowResourceConstants.CLASS_FOGBOW_REQUEST)
				&& category.getScheme().equals(FogbowResourceConstants.SCHEME_FOGBOW_REQUEST)) {
			return true;
		}
		return false;
	}

	private List<Category> getListCategory(HttpRequest req) throws IrregularSyntaxOCCIExtException {
		List<Category> listCategory = new ArrayList();
		String[] valuesCategory = req.getHeaders().getValuesArray(
				normalize(HeaderConstants.CATEGORY));
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
						throw new IrregularSyntaxOCCIExtException();
					}
				}
				category = new Category(term, scheme, catClass);
				listCategory.add(category);
			} else {
				throw new IrregularSyntaxOCCIExtException();
			}
		}
		return listCategory;
	}

	private static String normalize(String headerName) {
		String lowerHeader = headerName.toLowerCase();
		char[] lowerHeaderArray = lowerHeader.toCharArray();
		lowerHeaderArray[0] = Character.toUpperCase(lowerHeaderArray[0]);
		return new String(lowerHeaderArray);
	}
}
