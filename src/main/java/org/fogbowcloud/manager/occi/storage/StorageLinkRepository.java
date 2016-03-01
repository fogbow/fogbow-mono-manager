package org.fogbowcloud.manager.occi.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.Token;

public class StorageLinkRepository {

	private static final Logger LOGGER = Logger.getLogger(StorageLinkRepository.class);
	
	private Map<String, List<StorageLink>> storageLinks = new HashMap<String, List<StorageLink>>();
		
	public Map<String, List<StorageLink>> getStorageLinks() {
		return storageLinks;
	}
	
	public void setStorageLinks(Map<String, List<StorageLink>> storageLinks) {
		this.storageLinks = storageLinks;
	}
	
	protected boolean storageLinkExists(List<StorageLink> storageLinks, StorageLink userStorageLink) {
		for (StorageLink storageLink : storageLinks) {
			if (storageLink.getId().equals(userStorageLink.getId())) {
				return true;
			}
		}
		return false;
	}
	
	public void addStorageLink(String user, StorageLink storageLink) {
		LOGGER.debug("Adding storage link " + storageLink.getId() + " to user " + user);
		List<StorageLink> userStorageLinks = storageLinks.get(user);
		if (userStorageLinks == null) {
			userStorageLinks = new LinkedList<StorageLink>();
			storageLinks.put(user, userStorageLinks);
		}
		if (storageLinkExists(userStorageLinks, storageLink)) {
			return;
		}
		userStorageLinks.add(storageLink);
	}
	
	public StorageLink get(String storageLinkId) {
		return get(storageLinkId, null);
	}
	
	public StorageLink get(String storageLinkId, String user) {
		Collection<List<StorageLink>> storageLinkColection = new ArrayList<List<StorageLink>>();
		if (user != null) {
			List<StorageLink> storageLinkbyUser = getByUser(user);
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
	
	public List<StorageLink> getByUser(String user) {
		LOGGER.debug("Getting local storage links by user " + user);
		List<StorageLink> userStorageLinks = storageLinks.get(user);
		if (userStorageLinks == null) {
			return new LinkedList<StorageLink>();
		}		
		LinkedList<StorageLink> userLocalStorageLinks = new LinkedList<StorageLink>();
		for (StorageLink storageLinks : userStorageLinks) {
			userLocalStorageLinks.add(storageLinks);
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
//					if (storageLinks.getState().equals(OrderState.CLOSED)) { 
//						LOGGER.debug("Order " + storageLinkId + " does not have an instance. Excluding order.");
//						iterator.remove();
//					} else {
//						storageLinks.setState(OrderState.DELETED);
//					}
					return;
				}
			}
		}
	}	
	
//	public void removeByUser(String user) {
//		List<StorageLink> storageLinksByUser = storageLinks.get(user);
//		if (storageLinksByUser != null) {
//			for (StorageLink storageLink : storageLinksByUser) {
//				remove(storageLink.getId());
//			}
//		}
//	}	
	
	public static class StorageLink {

		private String id;
		private String source;
		private String target;
		private String deviceId;
		private String provadingMemberId;
		private Token federationToken;
		private boolean isLocal;

		public StorageLink(Map<String, String> xOCCIAttributes) {
			this.id = String.valueOf(UUID.randomUUID());
			this.source = xOCCIAttributes.get(StorageAttribute.SOURCE.getValue());
			this.target = xOCCIAttributes.get(StorageAttribute.TARGET.getValue());
			this.deviceId = xOCCIAttributes.get(StorageAttribute.DEVICE_ID.getValue());
		}

		public StorageLink(String id, String source, String target, String deviceId) {
			this(id, source, target, deviceId, false);
		}
		
		public StorageLink(String id, String source, String target, String deviceId, boolean isLocal) {
			super();
			this.id = id;
			this.source = source;
			this.target = target;
			this.deviceId = deviceId;
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

		public String getProvadingMemberId() {
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
								
	}
}