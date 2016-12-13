package org.fogbowcloud.manager.occi.storage;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.model.Token;

public class StorageLink {

	private String id;
	private String source;
	private String target;
	private String deviceId;
	private String provadingMemberId;
	private Token federationToken;
	private boolean isLocal;

	public StorageLink(Map<String, String> xOCCIAttributes) {
		this.source = xOCCIAttributes.get(StorageAttribute.SOURCE.getValue());
		this.target = xOCCIAttributes.get(StorageAttribute.TARGET.getValue());
		this.deviceId = xOCCIAttributes.get(StorageAttribute.DEVICE_ID.getValue());
	}

	public StorageLink(String id, String source, String target, String deviceId) {
		this(id, source, target, deviceId, null, null, false);
	}		
			
	public StorageLink(String id, String source, String target,
			String deviceId, String provadingMemberId,
			Token federationToken, boolean isLocal) {
		this.id = id;
		this.source = source;
		this.target = target;
		this.deviceId = deviceId;
		this.provadingMemberId = provadingMemberId;
		this.federationToken = federationToken;
		this.isLocal = isLocal;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProvidingMemberId() {
		return provadingMemberId;
	}

	public void setProvadingMemberId(String provadingMemberId) {
		this.provadingMemberId = provadingMemberId;
	}

	public Token getFederationToken() {
		return federationToken;
	}

	public void setFederationToken(Token federationToken) {
		this.federationToken = federationToken;
	}

	public boolean isLocal() {
		return isLocal;
	}

	public void setLocal(boolean isLocal) {
		this.isLocal = isLocal;
	}
	
	public static class Util {
		
		public static String storageLinksToString(List<StorageLink> storageLinks) {
			StringBuilder stringBuilder = new StringBuilder();
			for (StorageLink storageLink : storageLinks) {
				if (stringBuilder.length() != 0) {
					stringBuilder.append(", ");
				}
				stringBuilder.append(storageLink.getId());
			}
			return stringBuilder.toString();
		}
		
	}	

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StorageLink other = (StorageLink) obj;
		if (deviceId == null) {
			if (other.deviceId != null)
				return false;
		} else if (!deviceId.equals(other.deviceId))
			return false;
		if (federationToken == null) {
			if (other.federationToken != null)
				return false;
		} else if (!federationToken.equals(other.federationToken))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (isLocal != other.isLocal)
			return false;
		if (provadingMemberId == null) {
			if (other.provadingMemberId != null)
				return false;
		} else if (!provadingMemberId.equals(other.provadingMemberId))
			return false;
		if (source == null) {
			if (other.source != null)
				return false;
		} else if (!source.equals(other.source))
			return false;
		if (target == null) {
			if (other.target != null)
				return false;
		} else if (!target.equals(other.target))
			return false;
		return true;
	}			
				
}