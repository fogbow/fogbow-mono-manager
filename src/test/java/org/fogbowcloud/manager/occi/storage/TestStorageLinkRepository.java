//package org.fogbowcloud.manager.occi.storage;
//
//import java.util.Date;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Properties;
//
//import org.fogbowcloud.manager.occi.TestDataStorageHelper;
//import org.fogbowcloud.manager.occi.model.Token;
//import org.fogbowcloud.manager.occi.order.ManagerDataStoreController;
//import org.fogbowcloud.manager.occi.order.OrderConstants;
//import org.junit.After;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//
//public class TestStorageLinkRepository {
//
//	private static final String DEFAULT_USER_ID = "defaultUserId";
//	private ManagerDataStoreController managerDataStoreController;
//	private Token defaultToken;
//	
//	@Before
//	public void setup() {
//		TestDataStorageHelper.removeDefaultFolderDataStore();
//		this.managerDataStoreController = new ManagerDataStoreController();
//		this.managerDataStoreController.createManagerDataStore(new Properties());
//		this.defaultToken = new Token("id", new Token.User(DEFAULT_USER_ID, "username"), new Date(), null);
//	}
//	
//	@After
//	public void tearDown() {
//		TestDataStorageHelper.removeDefaultFolderDataStore();
//	}
//	
//	@Test
//	public void testAddStorageLink() {
//		String user = "user";
//		String id = "id";
//		StorageLink storageLink = new StorageLink(id, "source", "target", "deviceId", null, defaultToken, true);
//		this.managerDataStoreController.addStorageLink(user, storageLink);
//		storageLink = new StorageLink(id + "Two", "source", "target", "deviceId", null, defaultToken, true);
//		Assert.assertEquals(1, managerDataStoreController.getStorageLinks().size());		
//		this.managerDataStoreController.addStorageLink(user, storageLink);
//		Assert.assertEquals(2, managerDataStoreController.getStorageLinks().size());
//		Assert.assertEquals(2, managerDataStoreController.getByUser(user).size());
//		
//		StorageLink storageLinkTwo = new StorageLink("x", "x", "x", "x", null, defaultToken, false);
//		this.managerDataStoreController.addStorageLink("Two", storageLinkTwo);
//		Assert.assertEquals(3, managerDataStoreController.getStorageLinks().size());
//	}
//	
//	@Test
//	public void testGetStorageLink() {
//		String user = "user";
//		String id = "id";
//		String source = "source";
//		String target = "target";
//		String deviceId = "deviceId";
//		StorageLink storageLink = new StorageLink(id, source, target, deviceId, null, defaultToken, true);
//		this.managerDataStoreController.addStorageLink(user, storageLink);
//		Assert.assertEquals(1, managerDataStoreController.getStorageLinks().size());
//		
//		StorageLink storageLinkFound = this.managerDataStoreController.getStorageLink(id);
//		Assert.assertNotNull(storageLinkFound);
//		Assert.assertEquals(id, storageLinkFound.getId());
//		Assert.assertEquals(source, storageLinkFound.getSource());
//		Assert.assertEquals(target, storageLinkFound.getTarget());
//		Assert.assertEquals(deviceId, storageLinkFound.getDeviceId());
//		
//		Assert.assertNull(this.managerDataStoreController.getStorageLink("wrong"));
//	}
//	
//	@Test
//	public void testGetAllByInstanceStorageLink() {
//		String user = "user";
//		String id = "id";
//		String source = "source";
//		String target = "target";
//		String deviceId = "deviceId";
//		StorageLink storageLink = new StorageLink(id, source, target, deviceId);
//		this.managerDataStoreController.addStorageLink(user, storageLink);
//		this.managerDataStoreController.addStorageLink("idTwo", new StorageLink("idTwo", source, "targetTwo", "deviceIdTwo"));
//		this.managerDataStoreController.addStorageLink("idThree", new StorageLink("idThree", source, "targetThree", "deviceIdThree"));
//		this.managerDataStoreController.addStorageLink("idFour", new StorageLink("idFour", "sourceFour", "targetFour", "deviceIdFour"));
//		
//		Assert.assertEquals(4, managerDataStoreController.getStorageLinks().size());
//		
//		List<StorageLink> storageLinksFound = this.managerDataStoreController.getAllStorageLinkByInstance(source, OrderConstants.COMPUTE_TERM);
//		Assert.assertEquals(3, storageLinksFound.size());
//		Assert.assertNotNull(storageLinksFound);
//		int firstValue = 0;
//		Assert.assertEquals(id, storageLinksFound.get(firstValue).getId());
//		Assert.assertEquals(source, storageLinksFound.get(firstValue).getSource());
//		Assert.assertEquals(target, storageLinksFound.get(firstValue).getTarget());
//		Assert.assertEquals(deviceId, storageLinksFound.get(firstValue).getDeviceId());
//		
//		storageLinksFound.clear();
//		storageLinksFound = this.managerDataStoreController.getAllStorageLinkByInstance(target, OrderConstants.STORAGE_TERM);
//		Assert.assertEquals(1, storageLinksFound.size());
//		Assert.assertNotNull(storageLinksFound);
//		Assert.assertEquals(id, storageLinksFound.get(0).getId());
//		Assert.assertEquals(source, storageLinksFound.get(0).getSource());
//		Assert.assertEquals(target, storageLinksFound.get(0).getTarget());
//		Assert.assertEquals(deviceId, storageLinksFound.get(0).getDeviceId());		
//				
//		Assert.assertEquals(0, this.managerDataStoreController.getAllStorageLinkByInstance("", "").size());
//	}	
//	
//	@Test
//	public void testStorageLinksToString() {
//		String user = "user";
//		String idTwo = "idTwo";
//		this.managerDataStoreController.addStorageLink(user, new StorageLink(idTwo, "source", "targetTwo", "deviceIdTwo"));
//		String idThree = "idThree";
//		this.managerDataStoreController.addStorageLink(user, new StorageLink(idThree, "source", "targetThree", "deviceIdThree"));
//		String idFour = "idFour";
//		this.managerDataStoreController.addStorageLink(user, new StorageLink(idFour, "sourceFour", "targetFour", "deviceIdFour"));
//		String idFourTwo = "idFourTwo";
//		this.managerDataStoreController.addStorageLink(user, new StorageLink(idFourTwo, "sourceFourTwo", "targetFourTwo", "deviceIdFourTwo"));
//		
//		Assert.assertEquals(idTwo + ", " + idThree + ", " + idFour + ", " + idFourTwo, 
//				StorageLink.Util.storageLinksToString(managerDataStoreController.getByUser(user)));
//	}
//	
//	@Test
//	public void testRemoveStorageLink() {
//		String user = "user";
//		String storageLinkId = "id";
//		String source = "source";
//		String target = "target";
//		String deviceId = "deviceId";
//		StorageLink storageLink = new StorageLink(storageLinkId, source, target, deviceId, null, defaultToken, true);
//		StorageLink storageLinkTwo = new StorageLink("idTwo", "source", "target", "deviceId", null, defaultToken, true);
//		this.managerDataStoreController.addStorageLink(user, storageLink);
//		this.managerDataStoreController.addStorageLink(user, storageLinkTwo);
//		this.managerDataStoreController.addStorageLink(user, new StorageLink("", "", "", "", null, defaultToken, true));
//		Assert.assertEquals(3, managerDataStoreController.getStorageLinks().size());
//		Assert.assertEquals(3, this.managerDataStoreController.getByUser(user).size());
//		
//		StorageLink storageLinkFound = this.managerDataStoreController.getStorageLink(storageLinkId);
//		Assert.assertNotNull(storageLinkFound);
//		Assert.assertEquals(storageLinkId, storageLinkFound.getId());
//		Assert.assertEquals(source, storageLinkFound.getSource());
//		Assert.assertEquals(target, storageLinkFound.getTarget());
//		Assert.assertEquals(deviceId, storageLinkFound.getDeviceId());
//		
//		this.managerDataStoreController.removeStorageLink(storageLinkId);
//		
//		Assert.assertNull(this.managerDataStoreController.getStorageLink(storageLinkId));
//	}
//	
//	@Test
//	public void testStorageLinkInitializeNull() {
//		Map<String, String> xOCCIAttributes = new HashMap<String, String>();
//		StorageLink storageLink = new StorageLink(xOCCIAttributes);
//		
//		Assert.assertNull(storageLink.getDeviceId());
//		Assert.assertNull(storageLink.getTarget());
//		Assert.assertNull(storageLink.getSource());
//	}
//	
//	@Test
//	public void testStorageLinkInitialize() {
//		String deviceId = "deviceId";
//		String target = "target";
//		String source = "source";
//		Map<String, String> xOCCIAttributes = new HashMap<String, String>();
//		xOCCIAttributes.put(StorageAttribute.SOURCE.getValue(), source);
//		xOCCIAttributes.put(StorageAttribute.TARGET.getValue(), target);
//		xOCCIAttributes.put(StorageAttribute.DEVICE_ID.getValue(), deviceId);
//		StorageLink storageLink = new StorageLink(xOCCIAttributes);
//		
//		Assert.assertEquals(deviceId, storageLink.getDeviceId());
//		Assert.assertEquals(target, storageLink.getTarget());
//		Assert.assertEquals(source, storageLink.getSource());
//	}	
//	
//	@Test
//	public void testGetAllStorageLink() {
//		Token federationTokenOne = new Token("accessIdOne", new Token.User("userOne", ""), new Date(), null);
//		Token federationTokenTwo = new Token("accessIdTwo", new Token.User("userTwo", ""), new Date(), null);
//		StorageLink storageLinkOne = new StorageLink("idOne", "sourceOne", "targetOne",
//				"deviceIdOne", "provadingMemberIdOne", federationTokenOne, true);
//		StorageLink storageLinkTwo = new StorageLink("idTwo", "sourceTwo", "targetTwo",
//				"deviceIdTwo", "provadingMemberIdTwo", federationTokenOne, true);
//		StorageLink storageLinkThree = new StorageLink("idThree", "sourceThree", "targetThree",
//				"deviceIdThree", "provadingMemberIdThree", federationTokenTwo, true);		
//		managerDataStoreController.addStorageLink(storageLinkOne.getFederationToken().getUser().getId(), storageLinkOne);
//		managerDataStoreController.addStorageLink(storageLinkTwo.getFederationToken().getUser().getId(), storageLinkTwo);
//		managerDataStoreController.addStorageLink(storageLinkThree.getFederationToken().getUser().getId(), storageLinkThree);
//		this.managerDataStoreController.getAllStorageLinks();
//		
//		Assert.assertEquals(3, this.managerDataStoreController.getAllStorageLinks().size());
//	}
//	
//	@Test
//	public void testRemoveAllByInstance() {
//		String instanceId = "sourceOne";
//		Token federationTokenOne = new Token("accessIdOne", new Token.User("userOne", ""), new Date(), null);
//		StorageLink storageLinkOne = new StorageLink("idOne", instanceId, "targetOne",
//				"deviceIdOne", "provadingMemberIdOne", federationTokenOne, true);
//		StorageLink storageLinkTwo = new StorageLink("idTwo", instanceId, "targetTwo",
//				"deviceIdTwo", "provadingMemberIdTwo", federationTokenOne, true);	
//		managerDataStoreController.addStorageLink(storageLinkOne.getFederationToken().getUser().getId(), storageLinkOne);
//		managerDataStoreController.addStorageLink(storageLinkTwo.getFederationToken().getUser().getId(), storageLinkTwo);		
//		this.managerDataStoreController.getAllStorageLinks();
//		Assert.assertEquals(2, this.managerDataStoreController.getAllStorageLinks().size());
//		
//		this.managerDataStoreController.removeAllStorageLinksByInstance(instanceId, OrderConstants.COMPUTE_TERM);
//		
//		Assert.assertEquals(0, this.managerDataStoreController.getAllStorageLinks().size());
//	}
//}
