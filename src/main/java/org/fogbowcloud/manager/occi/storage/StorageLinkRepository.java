package org.fogbowcloud.manager.occi.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderConstants;

public class StorageLinkRepository {

	private static final Logger LOGGER = Logger.getLogger(StorageLinkRepository.class);
	
	// TODO refactor list to map
	private Map<String, List<StorageLink>> storageLinks = new HashMap<String, List<StorageLink>>();
		
	protected Map<String, List<StorageLink>> getStorageLinks() {
		return storageLinks;
	}
	
	private boolean storageLinkExists(List<StorageLink> storageLinks, StorageLink userStorageLink) {
		for (StorageLink storageLink : storageLinks) {
			if (storageLink.getId().equals(userStorageLink.getId())) {
				return true;
			}
		}
		return false;
	}
	
	public boolean addStorageLink(String userId, StorageLink storageLink) {
		LOGGER.debug("Adding storage link " + storageLink.getId() + " to user id " + userId);
		List<StorageLink> userStorageLinks = storageLinks.get(userId);
		if (userStorageLinks == null) {
			userStorageLinks = new LinkedList<StorageLink>();
			storageLinks.put(userId, userStorageLinks);
		}
		if (storageLinkExists(userStorageLinks, storageLink)) {
			return false;
		}
		userStorageLinks.add(storageLink);
		return true;
	}
		
	public List<StorageLink> getAllStorageLinks() {
		List<StorageLink> allStorageLinks = new LinkedList<StorageLink>();
		for (List<StorageLink> userStorageLinks : storageLinks.values()) {
			for (StorageLink storageLink : userStorageLinks) {
				allStorageLinks.add(storageLink);
			}
		}
		return allStorageLinks;
	}
	
	public StorageLink get(String storageLinkId) {
		return get(null, storageLinkId);
	}
	
	public StorageLink get(String userId, String storageLinkId) {
		Collection<List<StorageLink>> storageLinkColection = new ArrayList<List<StorageLink>>();
		if (userId != null) {
			List<StorageLink> storageLinkbyUser = getByUser(userId);
			storageLinkColection.add(storageLinkbyUser != null ? storageLinkbyUser
							: new ArrayList<StorageLinkRepository.StorageLink>());
		} else {
			storageLinkColection = storageLinks.values();
		}
		for (List<StorageLink> userStorageLinks : storageLinkColection) {
			for (StorageLink storageLink : userStorageLinks) {
				if (storageLink.getId().equals(storageLinkId)) {
					LOGGER.debug("Getting storage link id " + storageLink);
					return storageLink;
				}
			}
		}
		LOGGER.debug("Storage link id " + storageLinkId + " was not found.");
		return null;
	}
	
	public List<StorageLink> getAllByInstance(String instanceId, String type) {
		Collection<List<StorageLink>> storageLinkColection = new ArrayList<List<StorageLink>>(storageLinks.values());
		List<StorageLink> storageLinks = new ArrayList<StorageLinkRepository.StorageLink>();
		for (List<StorageLink> userStorageLinks : storageLinkColection) {
			for (StorageLink storageLink : userStorageLinks) {
				if (type.equals(OrderConstants.COMPUTE_TERM) && instanceId.equals(storageLink.getSource()) 
						|| type.equals(OrderConstants.STORAGE_TERM) && instanceId.equals(storageLink.getTarget())) {
					LOGGER.debug("Getting storage link id " + storageLink);
					storageLinks.add(storageLink);					
				} 
			}
		}
		LOGGER.debug("Storage link id, by instance id : (" + instanceId + "), was not found.");
		return storageLinks;
	}	
	
	public void removeAllByInstance(String instanceId, String type) {
		Collection<List<StorageLink>> storageLinkColection = new ArrayList<List<StorageLink>>(
				storageLinks.values());
		for (List<StorageLink> userStorageLinks : storageLinkColection) {
			for (StorageLink storageLink : new ArrayList<StorageLink>(
					userStorageLinks)) {
				if (type.equals(OrderConstants.COMPUTE_TERM) && instanceId.equals(storageLink.getSource()) 
						|| type.equals(OrderConstants.STORAGE_TERM) && instanceId.equals(storageLink.getTarget())) {				
					remove(storageLink.getId());
				}
			}
		}
		LOGGER.debug("Removing all storage link with id "
				+ "(" + instanceId + ") and type (" + type + ").");
	}
	
	public List<StorageLink> getByUser(String userId) {
		LOGGER.debug("Getting local storage links by user id " + userId);
		List<StorageLink> userStorageLinks = storageLinks.get(userId);
		if (userStorageLinks == null) {
			return new LinkedList<StorageLink>();
		}		
		return userStorageLinks;
	}
	
	public void remove(String storageLinkId) {
		LOGGER.debug("Removing storageLinkId " + storageLinkId);

		for (List<StorageLink> userStorageLinks : storageLinks.values()) {
			Iterator<StorageLink> iterator = userStorageLinks.iterator();
			while (iterator.hasNext()) {
				StorageLink storageLinks = (StorageLink) iterator.next();
				if (storageLinks.getId().equals(storageLinkId)) {
					iterator.remove();
					return;
				}
			}
		}
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
	
	public static class StorageLink {

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
}