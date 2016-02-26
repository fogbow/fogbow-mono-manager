package org.fogbowcloud.manager.occi.storage;

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
		
		StorageLink storageLinkTwo = new StorageLink("x", "x", "x", "x");
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
	
}
