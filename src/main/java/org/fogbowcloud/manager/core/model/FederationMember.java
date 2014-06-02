package org.fogbowcloud.manager.core.model;


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
		return DateUtils.getDateISO8601Format(lastTime);
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