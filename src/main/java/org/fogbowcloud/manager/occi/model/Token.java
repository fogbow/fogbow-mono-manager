package org.fogbowcloud.manager.occi.model;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.occi.OrderDataStoreHelper;
import org.json.JSONException;
import org.json.JSONObject;

public class Token {
	
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
		if (attributes == null) {
			return null;
		}
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

	public JSONObject toJSON() throws JSONException {	
		return new JSONObject().put("access_id", accessId).put("user", user)
				.put("attributes", attributes != null ? attributes.toString() : null);			
	}

	public static Token fromJSON(String jsonStr) throws JSONException {
		JSONObject jsonObject = new JSONObject(jsonStr);
		String accessId = jsonObject.optString("access_id");
		String user = jsonObject.optString("user");
		return new Token(!accessId.isEmpty() ? accessId : null, !user.isEmpty() ? user : null,
				null, OrderDataStoreHelper.toMap(jsonObject.optString("attributes")));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Token other = (Token) obj;
		if (accessId == null) {
			if (other.accessId != null)
				return false;
		} else if (!accessId.equals(other.accessId))
			return false;
		if (attributes == null) {
			if (other.attributes != null)
				return false;
		} else if (attributes != null
				&& !new HashSet(attributes.values()).equals(new HashSet(other.attributes.values())))
			return false;
		if (dateUtils == null) {
			if (other.dateUtils != null)
				return false;
		}
		if (user == null) {
			if (other.user != null)
				return false;
		} else if (!user.equals(other.user))
			return false;
		return true;
	}
		
}
