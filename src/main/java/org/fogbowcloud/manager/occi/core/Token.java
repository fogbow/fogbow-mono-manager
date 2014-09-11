package org.fogbowcloud.manager.occi.core;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.core.model.DateUtils;

public class Token {

	public static final String BREAK_LINE_REPLACE = "\n";

	private static final String DATE_EXPIRATION = "dateExpiration";
	 
	private Map<String, String> attributes;
	private String accessId;
	private String user;
	private DateUtils dateUtils = new DateUtils();

	// TODO Check invalid values
	public Token(String accessId, String user, Date expirationTime, Map<String, String> attributes) {
		this.accessId = accessId;
		this.user = user;		
		this.attributes = attributes;
		setExpirationDate(expirationTime);
	}

	public String get(String attributeName) {
		return attributes.get(attributeName);
	}

	public String getAccessId() {
		return this.accessId;
	}

	public void setExpirationDate(Date expirationDate) {
		if (expirationDate == null){
			return;
		}
		if (attributes == null) {
			attributes = new HashMap<String, String>();
		}
		attributes.put(DATE_EXPIRATION,
				String.valueOf(expirationDate.getTime()));
	}
	
	public Date getExpirationDate() {
		String dataExpiration = attributes.get(DATE_EXPIRATION);
		if (dataExpiration  != null){
			return new Date(Long.parseLong(dataExpiration));
		}else {
			return null;
		}
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
		return "AccessId: " + accessId + ", User: " + user + ", expirationDate: "
				+ getExpirationDate() + ", attributes: " + attributes;
	}

	public String getUser() {
		return this.user;
	}
}
