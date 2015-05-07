package org.fogbowcloud.manager.occi.request;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;

public class Request {

	public static String SEPARATOR_GLOBAL_ID = "@";
	
	private String id;
	private Token federationToken;
	private Token localToken;
	private String instanceId;
	private String providingMemberId;
	private final String requestingMemberId;
	private long fulfilledTime = 0;
	private boolean fulfilledByFederationUser;
	private final boolean isLocal;
	private RequestState state;
	private List<Category> categories;
	private Map<String, String> xOCCIAtt;
	
	private DateUtils dateUtils = new DateUtils();
	
	public Request(String id, Token federationToken, Token localToken, 
			List<Category> categories, Map<String, String> xOCCIAtt, boolean isLocal, String requestingMemberId) {
		this(id, federationToken, localToken, categories, xOCCIAtt, isLocal, requestingMemberId, new DateUtils());
	}
	
	public Request(String id, Token federationToken, Token localToken, 
			List<Category> categories, Map<String, String> xOCCIAtt, boolean isLocal, String requestingMemberId, DateUtils dateUtils) {
		this.id = id;
		this.federationToken = federationToken;
		this.localToken = localToken;
		this.categories = categories;
		this.xOCCIAtt = xOCCIAtt;
		this.fulfilledByFederationUser = false;
		this.isLocal = isLocal;
		this.requestingMemberId = requestingMemberId;
		this.dateUtils = dateUtils;
		setState(RequestState.OPEN);		
	}
	
	public Request(Request request) {
		this(request.getId(), request.getFederationToken(), request.getLocalToken(), request.getCategories(), 
				request.getxOCCIAtt(), request.isLocal, request.getRequestingMemberId());
	}

	public List<Category> getCategories() {
		if (categories == null) {
			return null;
		}
		return new ArrayList<Category>(categories);
	}

	public void addCategory(Category category) {
		if (categories == null) {
			categories = new LinkedList<Category>();
		}
		if (!categories.contains(category)) {
			categories.add(category);
		}
	}

	public String getRequirements() {
		return xOCCIAtt.get(RequestAttribute.REQUIREMENTS.getValue());
	}
	
	public String getInstanceId() {
		return instanceId;
	}
	
	public boolean isFulfilledByFederationUser() {
		return fulfilledByFederationUser;
	}

	public void setFulfilledByFederationUser(boolean fulfilledByFederationUser) {
		this.fulfilledByFederationUser = fulfilledByFederationUser;
	}

	public String getGlobalInstanceId() {
		if (instanceId != null) {
			return instanceId + SEPARATOR_GLOBAL_ID + providingMemberId;
		}
		return instanceId;
	}

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}
	
	public void setDateUtils(DateUtils dateUtils) {
		this.dateUtils = dateUtils;
	}
	
	public boolean isLocal(){
		return isLocal;
	}

	public RequestState getState() {
		return state;
	}

	public void setState(RequestState state) {
		if (state.in(RequestState.FULFILLED)) {
			fulfilledTime = dateUtils.currentTimeMillis();
		} else if (state.in(RequestState.OPEN)) {
			fulfilledTime = 0;
		}
		this.state = state;
	}

	public String getId() {
		return id;
	}

	public String getAttValue(String attributeName) {
		if (xOCCIAtt == null) {
			return null;
		}
		return xOCCIAtt.get(attributeName);
	}

	public void putAttValue(String attributeName, String attributeValue) {
		if (xOCCIAtt == null) {
			xOCCIAtt = new HashMap<String, String>();
		}
		xOCCIAtt.put(attributeName, attributeValue);
	}

	public Token getFederationToken() {
		return this.federationToken;
	}

	public void setFederationToken(Token token) {
		this.federationToken = token;
	}
	
	public long getFulfilledTime() {
		return fulfilledTime;
	}

	public Token getLocalToken() {
		return localToken;
	}

	public void setLocalToken(Token localToken) {
		this.localToken = localToken;
	}

	public Map<String, String> getxOCCIAtt() {
		return new HashMap<String, String>(xOCCIAtt);
	}
	
	public String getRequestingMemberId(){
		return requestingMemberId;
	}

	public String getProvidingMemberId() {
		return providingMemberId;
	}

	public void setProvidingMemberId(String providingMemberId) {
		this.providingMemberId = providingMemberId;
	}

	public String toString() {
		return "id: " + id + ", token: " + federationToken + ", instanceId: " + instanceId
				+ ", providingMemberId: " + providingMemberId + ", requestingMemberId: "
				+ requestingMemberId + ", state: " + state + ", isLocal " + isLocal
				+ ", categories: " + categories + ", xOCCIAtt: " + xOCCIAtt;
	}

	public boolean isIntoValidPeriod() {
		String startDateStr = xOCCIAtt.get(RequestAttribute.VALID_FROM.getValue());
		Date startDate = DateUtils.getDateFromISO8601Format(startDateStr);
		if (startDate == null) {
			if (startDateStr != null) {
				return false;
			}
			startDate = new Date();
		}
		long now = new DateUtils().currentTimeMillis();
		return startDate.getTime() <= now && !isExpired();
	}

	public boolean isExpired() {
		String expirationDateStr = xOCCIAtt.get(RequestAttribute.VALID_UNTIL.getValue());
		Date expirationDate = DateUtils.getDateFromISO8601Format(expirationDateStr);
		if (expirationDateStr == null) {
			return false;
		} else if (expirationDate == null) {
			return true;
		}

		long now = new DateUtils().currentTimeMillis();
		return expirationDate.getTime() < now;
	}
	
}