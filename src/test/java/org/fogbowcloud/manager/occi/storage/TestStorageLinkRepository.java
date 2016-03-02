package org.fogbowcloud.manager.occi.storage;

import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestStorageLinkRepository {

	private StorageLinkRepository storageLinkRepository;
	
	@Before
	public void setup() {
		this.storageLinkRepository = new StorageLinkRepository();
	}
	
	@Test
	public void testAddStorageLink() {
		String user = "user";
		String id = "id";
		StorageLink storageLink = new StorageLink(id, "source", "target", "deviceId");
		this.storageLinkRepository.addStorageLink(user, storageLink);
		Assert.assertEquals(1, storageLinkRepository.getStorageLinks().size());		
		this.storageLinkRepository.addStorageLink(user, storageLink);
		Assert.assertEquals(1, storageLinkRepository.getStorageLinks().size());
		Assert.assertEquals(1, storageLinkRepository.getStorageLinks().get(user).size());
		
		StorageLink storageLinkTwo = new StorageLink("x", "x", "x", "x", false);
		this.storageLinkRepository.addStorageLink("Two", storageLinkTwo);
		Assert.assertEquals(2, storageLinkRepository.getStorageLinks().size());
	}
	
	@Test
	public void testGetStorageLink() {
		String user = "user";
		String id = "id";
		String source = "source";
		String target = "target";
		String deviceId = "deviceId";
		StorageLink storageLink = new StorageLink(id, source, target, deviceId);
		this.storageLinkRepository.addStorageLink(user, storageLink);
		Assert.assertEquals(1, storageLinkRepository.getStorageLinks().size());
		
		StorageLink storageLinkFound = this.storageLinkRepository.get(id);
		Assert.assertNotNull(storageLinkFound);
		Assert.assertEquals(id, storageLinkFound.getId());
		Assert.assertEquals(source, storageLinkFound.getSource());
		Assert.assertEquals(target, storageLinkFound.getTarget());
		Assert.assertEquals(deviceId, storageLinkFound.getDeviceId());
		
		Assert.assertNull(this.storageLinkRepository.get("wrong"));
	}
	
	@Test
	public void testRemoveStorageLink() {
		String user = "user";
		String storageLinkId = "id";
		String source = "source";
		String target = "target";
		String deviceId = "deviceId";
		StorageLink storageLink = new StorageLink(storageLinkId, source, target, deviceId);
		StorageLink storageLinkTwo = new StorageLink("idTwo", "source", "target", "deviceId");
		this.storageLinkRepository.addStorageLink(user, storageLink);
		this.storageLinkRepository.addStorageLink(user, storageLinkTwo);
		this.storageLinkRepository.addStorageLink(user, new StorageLink(new HashMap<String, String>()));
		Assert.assertEquals(1, storageLinkRepository.getStorageLinks().size());
		Assert.assertEquals(3, this.storageLinkRepository.getByUser(user).size());
		
		StorageLink storageLinkFound = this.storageLinkRepository.get(storageLinkId);
		Assert.assertNotNull(storageLinkFound);
		Assert.assertEquals(storageLinkId, storageLinkFound.getId());
		Assert.assertEquals(source, storageLinkFound.getSource());
		Assert.assertEquals(target, storageLinkFound.getTarget());
		Assert.assertEquals(deviceId, storageLinkFound.getDeviceId());
		
		this.storageLinkRepository.remove(storageLinkId);
		
		Assert.assertNull(this.storageLinkRepository.get(storageLinkId));
	}
	
	@Test
	public void testStorageLinkInitializeNull() {
		Map<String, String> xOCCIAttributes = new HashMap<String, String>();
		StorageLink storageLink = new StorageLink(xOCCIAttributes);
		
		Assert.assertNull(storageLink.getDeviceId());
		Assert.assertNull(storageLink.getTarget());
		Assert.assertNull(storageLink.getSource());
	}
	
	@Test
	public void testStorageLinkInitialize() {
		String deviceId = "deviceId";
		String target = "target";
		String source = "source";
		Map<String, String> xOCCIAttributes = new HashMap<String, String>();
		xOCCIAttributes.put(StorageAttribute.SOURCE.getValue(), source);
		xOCCIAttributes.put(StorageAttribute.TARGET.getValue(), target);
		xOCCIAttributes.put(StorageAttribute.DEVICE_ID.getValue(), deviceId);
		StorageLink storageLink = new StorageLink(xOCCIAttributes);
		
		Assert.assertEquals(deviceId, storageLink.getDeviceId());
		Assert.assertEquals(target, storageLink.getTarget());
		Assert.assertEquals(source, storageLink.getSource());
	}	
	
}