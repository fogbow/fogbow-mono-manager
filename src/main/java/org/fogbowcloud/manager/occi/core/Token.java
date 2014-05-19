package org.fogbowcloud.manager.occi.core;

import java.util.Date;
import java.util.Map;

import org.fogbowcloud.manager.xmpp.core.model.DateUtils;

public class Token {

	private Map<String, String> attributes;
	private String accessId;
	private Date expirationDate;
	private DateUtils dateUtils = new DateUtils();

	//TODO Check invalid values
	public Token(String accessId, Date expirationTime, Map<String, String> attributes) {
		this.accessId = accessId;
		this.expirationDate = expirationTime;
		this.attributes = attributes;
	}

	public String get(String attributeName) {
		return attributes.get(attributeName);
	}

	public String getAccessId() {
		return this.accessId;
	}
	
	public void setAccessId(String accessId) {
		this.accessId = accessId;
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
		return "AccessId: " + accessId + ", expirationDate: " + expirationDate + " attributes: "
				+ attributes;
	}
}
