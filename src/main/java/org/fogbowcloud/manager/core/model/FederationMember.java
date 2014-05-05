package org.fogbowcloud.manager.core.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.fogbowcloud.manager.xmpp.core.model.DateUtils;

public class FederationMember {

	public static final String ISO_8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
	
	private long lastTime;
	private ResourcesInfo resourcesInfo;

	public FederationMember(ResourcesInfo resourcesInfo) {
		if (resourcesInfo == null) {
			throw new IllegalArgumentException();
		}
		lastTime = new DateUtils().currentTimeMillis();
		this.resourcesInfo = resourcesInfo;
	}

	public ResourcesInfo getResourcesInfo() {
		return resourcesInfo;
	}

	public long getLastTime() {
		return lastTime;
	}

	public String getFormattedTime() {
		SimpleDateFormat dateFormatISO8601 = new SimpleDateFormat(
				ISO_8601_DATE_FORMAT, Locale.ROOT);
		dateFormatISO8601.setTimeZone(TimeZone.getTimeZone("GMT"));
		return dateFormatISO8601.format(new Date(lastTime));
	}

	/**
	 * This method was implemented just for unit test.
	 * 
	 * @param lastTime
	 */
	public void setLastTime(long lastTime) {
		this.lastTime = lastTime;
	}
}