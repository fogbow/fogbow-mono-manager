package org.fogbowcloud.manager.occi.core;

import java.util.Date;
import java.util.Map;

import org.fogbowcloud.manager.core.model.DateUtils;

public class Token {

	private Map<String, String> attributes;
	private String accessId;
	private String user;
	private Date expirationDate;
	private DateUtils dateUtils = new DateUtils();

	// TODO Check invalid values
	public Token(String accessId, String user, Date expirationTime, Map<String, String> attributes) {
		this.accessId = accessId;
		this.user = user;
		this.expirationDate = expirationTime;
		this.attributes = attributes;
	}

	public String get(String attributeName) {
		return attributes.get(attributeName);
	}

	public String getAccessId() {
		return this.accessId;
	}

	public Date getExpirationDate() {
		return this.expirationDate;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public boolean isExpiredToken() {
		long expirationDateMillis = getExpirationDate().getTime();
		return expirationDateMillis < dateUtils.currentTimeMillis();
	}

	public void setDateUtils(DateUtils dateUtils) {
		this.dateUtils = dateUtils;
	}

	public String toString() {
		return "AccessId: " + accessId + ", User: " + user + ", expirationDate: " + expirationDate
				+ " attributes: " + attributes;
	}

	public String getUser() {
		return this.user;
	}
}
