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
import java.util.concurrent.ConcurrentHashMap;

import org.fogbowcloud.manager.occi.core.RequestState;
import org.fogbowcloud.manager.occi.core.RequestType;
import org.fogbowcloud.manager.occi.core.RequestUnit;
import org.fogbowcloud.manager.occi.exception.IrregularSyntaxOCCIExtException;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class Request extends ServerResource {

	public static final String TERM_FOGBOW_REQUEST = "fogbow-request";
	public static final String SCHEME_FOGBOW_REQUEST = "http://schemas.fogbowcloud.org/request#";
	public static final String CLASS_FOGBOW_REQUEST = "kind";
	public static final String ATRIBUTE_INSTANCE_FOGBOW_REQUEST = "org.fogbowcloud.request.instance";
	public static final String ATRIBUTE_TYPE_FOGBOW_REQUEST = "org.fogbowcloud.request.type";
	public static final String ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST = "org.fogbowcloud.request.valid-until";
	public static final String ATRIBUTE_VALID_FROM_FOGBOW_REQUEST = "org.fogbowcloud.request.valid-from";
	private final int DEFAULT_INSTANCES = 1;

	private Map<String, List<RequestUnit>> userToRequests;

	public Request() {
		// TODO get from BD
		this.userToRequests = new ConcurrentHashMap<String, List<RequestUnit>>();
	}

	@Get
	public String fetch() {
		return null;
	}

	@Delete
	public String remove() {
		return null;
	}

	@Post
	public String post() {
		try {
			HttpRequest req = (HttpRequest) getRequest();
			String token = req.getHeaders().getValues(HeaderConstants.X_AUTH_TOKEN);
			if (isValidToken(token)) {
				validateContentType(req);
				List<Category> listCategory = getListCategory(req);
				validateRequestCategory(listCategory);
				Map<String, String> mapAttributes = getAtributes(req);
				int numberInstanceRequest = getAttributeInstances(mapAttributes);
				String typeResques = getAttributeType(mapAttributes);
				Date validFromRequest = getAttributeValidFrom(mapAttributes);
				Date validUntilRequest = getAttributeValidUntil(mapAttributes);

				if (userToRequests.get(token) == null) {
					userToRequests.put(token, new ArrayList<RequestUnit>());
				}
				List<RequestUnit> currentRequestUnits = new ArrayList<RequestUnit>();
				for (int i = 0; i < numberInstanceRequest; i++) {
					String requestId = String.valueOf(UUID.randomUUID());
					RequestUnit requestUnit = new RequestUnit(requestId, "", RequestState.OPEN);
					currentRequestUnits.add(requestUnit);
				}
				userToRequests.get(token).addAll(currentRequestUnits);
				return generateResponseId(currentRequestUnits);
			} else {
				setStatus(new Status(HeaderConstants.NOT_FOUND_RESPONSE));
				return "Without authorization";
			}
		} catch (IrregularSyntaxOCCIExtException e) {
			setStatus(new Status(HeaderConstants.BAD_REQUEST_RESPONSE));
			e.printStackTrace();
			return "Irregular Syntax";
		} catch (IllegalArgumentException e) {
			setStatus(new Status(HeaderConstants.BAD_REQUEST_RESPONSE));
			e.printStackTrace();
			return "Irregular Syntax";
		} catch (Exception e) {
			e.printStackTrace();
			return "Other error";
		}
	}

	private String generateResponseId(List<RequestUnit> requestUnits) {
		String response = "";
		for (RequestUnit request : requestUnits) {
			response += "X-OCCI-RequestId:" + request.getId();
		}
		return response;
	}

	private void validateContentType(HttpRequest req) throws IrregularSyntaxOCCIExtException {
		String contentType = req.getHeaders().getValues(HeaderConstants.CONTENT_TYPE);
		if (!contentType.equals(HeaderConstants.OCCI_CONTENT_TYPE)) {
			throw new IrregularSyntaxOCCIExtException();
		}
	}

	private int getAttributeInstances(Map<String, String> map)
			throws IrregularSyntaxOCCIExtException {
		try {
			String instances = map.get(ATRIBUTE_INSTANCE_FOGBOW_REQUEST);
			if (instances == null || instances.equals("")) {
				return DEFAULT_INSTANCES;
			} else {
				return Integer.parseInt(map.get(ATRIBUTE_INSTANCE_FOGBOW_REQUEST));
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
			throw new IrregularSyntaxOCCIExtException();
		}
	}

	private String getAttributeType(Map<String, String> map) throws IrregularSyntaxOCCIExtException {
		String type = map.get(ATRIBUTE_TYPE_FOGBOW_REQUEST);
		if (type == null || type.equals("")) {
			return null;
		} else {
			for (int i = 0; i < RequestType.values().length; i++) {
				System.out.println(RequestType.values()[i]);
				if (type.equals(RequestType.values()[i].getValue())) {
					return type;
				}
			}
			throw new IrregularSyntaxOCCIExtException();
		}

	}

	private Date getAttributeValidFrom(Map<String, String> map)
			throws IrregularSyntaxOCCIExtException {
		try {
			String dataString = map.get(ATRIBUTE_VALID_FROM_FOGBOW_REQUEST);
			if (dataString != null && !dataString.equals("")) {
				DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
				System.out.println("dATA :" + dataString);
				return (Date) formatter.parse(dataString);
			}
			return null;
		} catch (ParseException e) {
			e.printStackTrace();
			throw new IrregularSyntaxOCCIExtException();
		}
	}

	private Date getAttributeValidUntil(Map<String, String> map)
			throws IrregularSyntaxOCCIExtException {
		try {
			String dataString = map.get(ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST);
			if (dataString != null && !dataString.equals("")) {
				DateFormat formatter = new SimpleDateFormat("yy-MM-dd");
				System.out.println("dATA :" + dataString);
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
			if (category.getTerm().equals(TERM_FOGBOW_REQUEST)) {
				if (validateCategory(category) == false) {
					throw new IrregularSyntaxOCCIExtException();
				}
				return;
			}
		}
		throw new IrregularSyntaxOCCIExtException();
	}

	private boolean validateCategory(Category category) {
		System.out.println(category.getTerm().equals(TERM_FOGBOW_REQUEST));
		System.out.println(category.getCatClass().equals(CLASS_FOGBOW_REQUEST));
		System.out.println(category.getScheme().equals(SCHEME_FOGBOW_REQUEST));

		if (category.getTerm().equals(TERM_FOGBOW_REQUEST)
				&& category.getCatClass().equals(CLASS_FOGBOW_REQUEST)
				&& category.getScheme().equals(SCHEME_FOGBOW_REQUEST)) {
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
		}
		return listCategory;
	}

	private boolean isValidToken(String token) {
		// TODO check token and user
		if (token == null || token.equals("")) {
			return false;
		}
		return true;
	}

	private static String normalize(String headerName) {
		String lowerHeader = headerName.toLowerCase();
		char[] lowerHeaderArray = lowerHeader.toCharArray();
		lowerHeaderArray[0] = Character.toUpperCase(lowerHeaderArray[0]);
		return new String(lowerHeaderArray);
	}
}
