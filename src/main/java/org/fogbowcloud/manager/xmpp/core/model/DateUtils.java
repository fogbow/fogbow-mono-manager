package org.fogbowcloud.manager.xmpp.core.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.fogbowcloud.manager.core.model.FederationMember;

public class DateUtils {
	
	public DateUtils() {
	}

	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}

	public static String getDateISO8601Format(long dateMili) {
		SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
				FederationMember.ISO_8601_DATE_FORMAT, Locale.ROOT);
		dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));
		String expirationDate = dateFormatISO8601.format(new Date(dateMili));
		return expirationDate;
	}
}
