package org.fogbowcloud.manager.occi.core;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.xmpp.core.model.DateUtils;

public class Token {

	private Map<String, String> attributes;

	public Token(Map<String, String> attributes) {
		this.attributes = attributes;
	}

	public String get(String attributeName) {
		try {
			return attributes.get(attributeName);
			//TODO Which exception could happen here?
		} catch (Exception e) {
			return null;
		}

	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public boolean isExpiredToken() {
		String expirationDateSrt = get(OCCIHeaders.X_TOKEN_EXPIRATION_DATE);
		SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
				FederationMember.ISO_8601_DATE_FORMAT, Locale.ROOT);
		dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));
		long expirationDateMillis;
		try {
			expirationDateMillis = dateFormatISO8601.parse(expirationDateSrt).getTime();
		} catch (Exception e) {
			return true;
		}
		return expirationDateMillis < new DateUtils().currentTimeMillis();
	}
}
