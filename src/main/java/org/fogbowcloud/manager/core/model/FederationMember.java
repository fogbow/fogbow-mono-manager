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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FederationMember other = (FederationMember) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return getId();
	}
}