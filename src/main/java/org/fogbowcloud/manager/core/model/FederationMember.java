package org.fogbowcloud.manager.core.model;


public class FederationMember {

	public static final String ISO_8601_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

	private long lastTime;
	private ResourcesInfo resourcesInfo;
	private String id;

	public FederationMember() {
		lastTime = new DateUtils().currentTimeMillis();
	}
	
	public FederationMember(ResourcesInfo resourcesInfo) {
		this();
		if (resourcesInfo == null) {
			throw new IllegalArgumentException();
		}
		setId(resourcesInfo.getId());
		this.resourcesInfo = resourcesInfo;
	}
	
	public FederationMember(String id) {
		this();
		setId(id);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof FederationMember)) {
			return false;
		}
		FederationMember otherMember = (FederationMember) obj;
		return getId().equals(otherMember.getId());
	}
	
	@Override
	public String toString() {
		return getId();
	}
}