package org.fogbowcloud.manager.occi;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.storage.StorageAttribute;
import org.fogbowcloud.manager.occi.storage.StorageLink;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestManagerDataStoreController {

	private static final String ID1 = "ID1";
	private static final String ID2 = "ID2";
	private static final String ID3 = "ID3";
	private static final String ID4 = "ID4";
	private static final String ID5 = "ID5";
	private static final String USER_ID = "user";
	
	private ManagerDataStoreController managerDataStoreController;
	private final String DEFAULT_USER_ID = "defaultUserId";
	private Token defaultToken;
	
	@Before
	public void setUp() {
		TestDataStorageHelper.removeDefaultFolderDataStore();
		// Create in default test path
		managerDataStoreController = new ManagerDataStoreController(new Properties());
		managerDataStoreController.addOrder(createOrder(ID1, USER_ID, true));
		managerDataStoreController.addOrder(createOrder(ID2, USER_ID, true));
		managerDataStoreController.addOrder(createOrder(ID3, USER_ID, true));
		managerDataStoreController.addOrder(createOrder(ID4, USER_ID, false));
		managerDataStoreController.addOrder(createOrder(ID5, USER_ID, false));
		this.defaultToken = new Token("id", new Token.User(DEFAULT_USER_ID, "username"), new Date(), null);
	}

	@After
	public void tearDown() {
		TestDataStorageHelper.removeDefaultFolderDataStore();
	}
 	
	@Test
	public void testGetLocalOrder() {	
		Assert.assertNotNull(managerDataStoreController.getOrder(ID1));
		Assert.assertNotNull(managerDataStoreController.getOrder(ID2));
		Assert.assertNotNull(managerDataStoreController.getOrder(ID3));
	}
	
	@Test
	public void testTryGetLocalOrder() {	
		Assert.assertNull(managerDataStoreController.getOrder(ID4));
		Assert.assertNull(managerDataStoreController.getOrder(ID5));
	}	

	@Test
	public void testGetServeredOrder() {	
		Assert.assertNotNull(managerDataStoreController.getOrder(ID4, false));
	}

	@Test
	public void testTryGetServeredOrder() {	
		Assert.assertNull(managerDataStoreController.getOrder(ID1, false));
	}	
	
	@Test
	public void testGetLocalOrderByUser() {	
		Assert.assertNotNull(managerDataStoreController.getOrder(USER_ID, ID1));
		Assert.assertNotNull(managerDataStoreController.getOrder(USER_ID, ID2));
		Assert.assertNotNull(managerDataStoreController.getOrder(USER_ID, ID3));
	}

	@Test
	public void testTryGetLocalOrderByUser() {	
		Assert.assertNull(managerDataStoreController.getOrder(USER_ID, ID4));
		Assert.assertNull(managerDataStoreController.getOrder(USER_ID, ID5));
	}	

	@Test
	public void testGetServeredOrderByUser() {	
		Assert.assertNotNull(managerDataStoreController.getOrder(USER_ID, ID4, false));
	}
	
	@Test
	public void testTryGetServeredOrderByUser() {	
		Assert.assertNull(managerDataStoreController.getOrder(USER_ID, ID1, false));
	}		
	
	@Test
	public void testGetByUser() {
		Assert.assertEquals(3, managerDataStoreController.getOrdersByUserId(USER_ID).size());
	}
	
	@Test
	public void testGetByUserServeredOrder() {
		Assert.assertEquals(2, managerDataStoreController.getOrdersByUserId(USER_ID, false).size());
	}	
	
	@Test
	public void testGetOrderInState() {
		String user = "user";
		managerDataStoreController.addOrder(createOrder("idThree", user, true, OrderConstants.STORAGE_TERM));
		managerDataStoreController.addOrder(createOrder("idFOur", user, true, OrderConstants.STORAGE_TERM));
		List<Order> ordersCompute = managerDataStoreController.getOrdersIn(
				OrderConstants.COMPUTE_TERM, OrderState.OPEN);
		List<Order> ordersStorage = managerDataStoreController.getOrdersIn(
				OrderConstants.STORAGE_TERM, OrderState.OPEN);		
		Assert.assertEquals(5, ordersCompute.size());
		Assert.assertEquals(2, ordersStorage.size());
	}
	
	@Test
	public void testAddStorageLink() {
		String id = "id";
		StorageLink storageLink = new StorageLink(id, "source", "target", "deviceId", null, defaultToken, true);
		this.managerDataStoreController.addStorageLink(storageLink);
		storageLink = new StorageLink(id + "Two", "source", "target", "deviceId", null, defaultToken, true);
		Assert.assertEquals(1, managerDataStoreController.getAllStorageLinks().size());		
		this.managerDataStoreController.addStorageLink(storageLink);
		Assert.assertEquals(2, managerDataStoreController.getAllStorageLinks().size());
		Assert.assertEquals(2, managerDataStoreController.getStorageLinksByUser(
				defaultToken.getUser().getId()).size());
		
		StorageLink storageLinkTwo = new StorageLink("x", "x", "x", "x", null, defaultToken, false);
		this.managerDataStoreController.addStorageLink(storageLinkTwo);
		Assert.assertEquals(3, managerDataStoreController.getAllStorageLinks().size());
	}
	
	@Test
	public void testGetStorageLink() {
		String id = "id";
		String source = "source";
		String target = "target";
		String deviceId = "deviceId";
		StorageLink storageLink = new StorageLink(id, source, target, deviceId, null, defaultToken, true);
		this.managerDataStoreController.addStorageLink(storageLink);
		Assert.assertEquals(1, managerDataStoreController.getAllStorageLinks().size());
		
		StorageLink storageLinkFound = this.managerDataStoreController.getStorageLink(id);
		Assert.assertNotNull(storageLinkFound);
		Assert.assertEquals(id, storageLinkFound.getId());
		Assert.assertEquals(source, storageLinkFound.getSource());
		Assert.assertEquals(target, storageLinkFound.getTarget());
		Assert.assertEquals(deviceId, storageLinkFound.getDeviceId());
		
		Assert.assertNull(this.managerDataStoreController.getStorageLink("wrong"));
	}
	
	@Test
	public void testGetAllByInstanceStorageLink() {
		String id = "id";
		String source = "source";
		String target = "target";
		String deviceId = "deviceId";
		StorageLink storageLink = new StorageLink(id, source, target, deviceId);
		this.managerDataStoreController.addStorageLink(storageLink);
		this.managerDataStoreController.addStorageLink(new StorageLink("idTwo", source, "targetTwo", "deviceIdTwo"));
		this.managerDataStoreController.addStorageLink(new StorageLink("idThree", source, "targetThree", "deviceIdThree"));
		this.managerDataStoreController.addStorageLink(new StorageLink("idFour", "sourceFour", "targetFour", "deviceIdFour"));
		
		Assert.assertEquals(4, managerDataStoreController.getAllStorageLinks().size());
		
		List<StorageLink> storageLinksFound = this.managerDataStoreController.getAllStorageLinkByInstance(source, OrderConstants.COMPUTE_TERM);
		Assert.assertEquals(3, storageLinksFound.size());
		Assert.assertNotNull(storageLinksFound);
		int firstValue = 0;
		Assert.assertEquals(id, storageLinksFound.get(firstValue).getId());
		Assert.assertEquals(source, storageLinksFound.get(firstValue).getSource());
		Assert.assertEquals(target, storageLinksFound.get(firstValue).getTarget());
		Assert.assertEquals(deviceId, storageLinksFound.get(firstValue).getDeviceId());
		
		storageLinksFound.clear();
		storageLinksFound = this.managerDataStoreController.getAllStorageLinkByInstance(target, OrderConstants.STORAGE_TERM);
		Assert.assertEquals(1, storageLinksFound.size());
		Assert.assertNotNull(storageLinksFound);
		Assert.assertEquals(id, storageLinksFound.get(0).getId());
		Assert.assertEquals(source, storageLinksFound.get(0).getSource());
		Assert.assertEquals(target, storageLinksFound.get(0).getTarget());
		Assert.assertEquals(deviceId, storageLinksFound.get(0).getDeviceId());		
				
		Assert.assertEquals(0, this.managerDataStoreController.getAllStorageLinkByInstance("", "").size());
	}	
	
	@Test
	public void testStorageLinksToString() {		
		String user = "user";
		String idTwo = "idTwo";
		Token token = new Token("accessId",new Token.User(user, user), new Date(), null);
		
		this.managerDataStoreController.addStorageLink(new StorageLink(idTwo, 
				"source", "targetTwo", "deviceIdTwo", null, token, true));
		String idThree = "idThree";
		this.managerDataStoreController.addStorageLink(new StorageLink(idThree, 
				"source", "targetThree", "deviceIdThree", null, token, true));
		String idFour = "idFour";
		this.managerDataStoreController.addStorageLink(new StorageLink(idFour, 
				"sourceFour", "targetFour", "deviceIdFour", null, token, true));
		String idFourTwo = "idFourTwo";
		this.managerDataStoreController.addStorageLink(new StorageLink(idFourTwo, 
				"sourceFourTwo", "targetFourTwo", "deviceIdFourTwo", null, token, true));
		
		Assert.assertEquals(idTwo + ", " + idThree + ", " + idFour + ", " + idFourTwo, 
				StorageLink.Util.storageLinksToString(managerDataStoreController.getStorageLinksByUser(user)));
	}
	
	@Test
	public void testRemoveStorageLink() {

		String storageLinkId = "id";
		String source = "source";
		String target = "target";
		String deviceId = "deviceId";
		StorageLink storageLink = new StorageLink(storageLinkId, source, target, deviceId, null, defaultToken, true);
		StorageLink storageLinkTwo = new StorageLink("idTwo", "source", "target", "deviceId", null, defaultToken, true);
		this.managerDataStoreController.addStorageLink(storageLink);
		this.managerDataStoreController.addStorageLink(storageLinkTwo);
		this.managerDataStoreController.addStorageLink(new StorageLink("", "", "", "", null, defaultToken, true));
		Assert.assertEquals(3, managerDataStoreController.getAllStorageLinks().size());
		Assert.assertEquals(3, this.managerDataStoreController.getStorageLinksByUser(defaultToken.getUser().getId()).size());
		
		StorageLink storageLinkFound = this.managerDataStoreController.getStorageLink(storageLinkId);
		Assert.assertNotNull(storageLinkFound);
		Assert.assertEquals(storageLinkId, storageLinkFound.getId());
		Assert.assertEquals(source, storageLinkFound.getSource());
		Assert.assertEquals(target, storageLinkFound.getTarget());
		Assert.assertEquals(deviceId, storageLinkFound.getDeviceId());
		
		this.managerDataStoreController.removeStorageLink(storageLinkId);
		
		Assert.assertNull(this.managerDataStoreController.getStorageLink(storageLinkId));
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
	
	@Test
	public void testGetAllStorageLink() {
		Token federationTokenOne = new Token("accessIdOne", new Token.User("userOne", ""), new Date(), null);
		Token federationTokenTwo = new Token("accessIdTwo", new Token.User("userTwo", ""), new Date(), null);
		StorageLink storageLinkOne = new StorageLink("idOne", "sourceOne", "targetOne",
				"deviceIdOne", "provadingMemberIdOne", federationTokenOne, true);
		StorageLink storageLinkTwo = new StorageLink("idTwo", "sourceTwo", "targetTwo",
				"deviceIdTwo", "provadingMemberIdTwo", federationTokenOne, true);
		StorageLink storageLinkThree = new StorageLink("idThree", "sourceThree", "targetThree",
				"deviceIdThree", "provadingMemberIdThree", federationTokenTwo, true);		
		managerDataStoreController.addStorageLink(storageLinkOne);
		managerDataStoreController.addStorageLink(storageLinkTwo);
		managerDataStoreController.addStorageLink(storageLinkThree);
		this.managerDataStoreController.getAllStorageLinks();
		
		Assert.assertEquals(3, this.managerDataStoreController.getAllStorageLinks().size());
	}
	
	@Test
	public void testRemoveAllByInstance() {
		String instanceId = "sourceOne";
		Token federationTokenOne = new Token("accessIdOne", new Token.User("userOne", ""), new Date(), null);
		StorageLink storageLinkOne = new StorageLink("idOne", instanceId, "targetOne",
				"deviceIdOne", "provadingMemberIdOne", federationTokenOne, true);
		StorageLink storageLinkTwo = new StorageLink("idTwo", instanceId, "targetTwo",
				"deviceIdTwo", "provadingMemberIdTwo", federationTokenOne, true);	
		managerDataStoreController.addStorageLink(storageLinkOne);
		managerDataStoreController.addStorageLink(storageLinkTwo);		
		this.managerDataStoreController.getAllStorageLinks();
		Assert.assertEquals(2, this.managerDataStoreController.getAllStorageLinks().size());
		
		this.managerDataStoreController.removeAllStorageLinksByInstance(instanceId, OrderConstants.COMPUTE_TERM);
		
		Assert.assertEquals(0, this.managerDataStoreController.getAllStorageLinks().size());
	}
	
	
	private Order createOrder(String id, String user, boolean isLocal) {
		return createOrder(id, user, isLocal, OrderConstants.COMPUTE_TERM);
	}
	
	private Order createOrder(String id, String user, boolean isLocal, String resourceKind) {
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(OrderAttribute.RESOURCE_KIND.getValue(), resourceKind);
		Token federationToken = new Token("1", new Token.User(user, ""), new Date(), attributes);
		Order order = new Order(id, federationToken, "", "", "", new Date().getTime(),
				isLocal, OrderState.OPEN, null, attributes);
		return order;
	}
	
}
