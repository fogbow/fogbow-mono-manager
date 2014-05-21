package org.fogbowcloud.manager.occi.request;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Token;

public class Request {

	private String id;
	private Token token;
	private String instanceId;
	private String memberId;
	private RequestState state;
	private List<Category> categories;
	private Map<String, String> xOCCIAtt;

	public Request(String id, Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		this.id = id;
		this.token = token;
		this.categories = categories;
		this.xOCCIAtt = xOCCIAtt;
		setState(RequestState.OPEN);
	}

	public List<Category> getCategories() {
		return categories;
	}

	public void addCategory(Category category) {
		if (categories == null) {
			categories = new LinkedList<Category>();
		}
		categories.add(category);
	}

	public String getInstanceId() {
		return instanceId;
	}

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	public RequestState getState() {
		return state;
	}

	public void setState(RequestState state) {
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

	public String toHttpMessageFormat() {
		return "RequestId=" + id + "; State=" + state.getValue() + "; InstanceId= " + instanceId;
	}

	public Token getToken() {		
		return this.token;
	}
	
	public void setToken(Token token) {
		this.token = token;		
	}

	public Map<String, String> getxOCCIAtt() {
		return new HashMap<String, String>(xOCCIAtt);
	}

	public String getMemberId() {
		return memberId;
	}

	public void setMemberId(String memberId) {
		this.memberId = memberId;
	}

	public String toString() {
		return "id: " + id + ", token: " + token + ", instanceId: " + instanceId + ", memberId: "
				+ memberId + ", state: " + state + ", categories: " + categories + ", xOCCIAtt: "
				+ xOCCIAtt;
	}

	public boolean isIntoValidPeriod() {
		String startDateStr = xOCCIAtt.get(RequestAttribute.VALID_FROM.getValue());		
		SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
				FederationMember.ISO_8601_DATE_FORMAT, Locale.ROOT);
		dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));		
		long startDateMillis;
		try {
			if (startDateStr != null){
				startDateMillis = dateFormatISO8601.parse(startDateStr).getTime();
			} else {
				startDateMillis = new DateUtils().currentTimeMillis();
			}
		} catch (Exception e) {
			return false;
		}
		long now = new DateUtils().currentTimeMillis();
		return startDateMillis <= now && !isExpired();
	}

	public boolean isExpired() {		
		String expirationDateSrt = xOCCIAtt.get(RequestAttribute.VALID_UNTIL.getValue());
		if (expirationDateSrt == null){
			return false;
		}
		//TODO Refactor! This code is repeated at many places
		SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
				FederationMember.ISO_8601_DATE_FORMAT, Locale.ROOT);
		dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));		
		long expirationDateMillis;		
		try {
			expirationDateMillis = dateFormatISO8601.parse(expirationDateSrt).getTime();
		} catch (Exception e) {
			return true;
		}
		long now = new DateUtils().currentTimeMillis();
		return expirationDateMillis < now;
	}
}