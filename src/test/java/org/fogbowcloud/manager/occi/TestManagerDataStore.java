package org.fogbowcloud.manager.occi;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.ManagerDataStore;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestManagerDataStore {

	private static final Logger LOGGER = Logger.getLogger(TestManagerDataStore.class);
	 
	private final String DATASTORE_PATH = "src/test/resources/testManagerDataStore.sqlite";
	private final String DATASTORE_URL = "jdbc:sqlite:" + DATASTORE_PATH;
	
	private Order orderOne;
	private Order orderTwo;
	private Order orderThree;
	private Order orderFour;
	
	private StorageLink storageLinkOne;
	private StorageLink storageLinkTwo;
	private StorageLink storageLinkThree;
	
	private Properties properties = null;
	private ManagerDataStore database = null; 
	
	@Before
	public void initialize() {		
		LOGGER.debug("Creating data store.");
		properties = new Properties();
		properties.put(ManagerDataStore.MANAGER_DATASTORE_URL , DATASTORE_URL);
		database = new ManagerDataStore(properties);
		initializeOrders();
		initializeStorageLinks();
	}
	
	@After
	public void tearDown() throws IOException{
		File dbFile = new File(DATASTORE_PATH);
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}
	
	@Test
	public void testAddOrder() throws SQLException, JSONException {
		database.addOrder(orderOne);
		List<Order> orders = database.getOrders();
		Assert.assertEquals(1, orders.size());

		Assert.assertTrue(orderOne.equals(orders.get(0)));
	}
	
	@Test
	public void testGetOrders() throws SQLException, JSONException {
		List<Order> orders = new ArrayList<Order>();
		orders.add(orderOne);
		orders.add(orderTwo);
		orders.add(orderThree);
		
		for (Order order : orders) {
			database.addOrder(order);
		}
		
		orders = database.getOrders();
		
		Assert.assertEquals(3, orders.get(0).getFederationToken().getAttributes().size());
		Assert.assertEquals(3, orders.get(0).getxOCCIAtt().size());
		Assert.assertEquals(2, orders.get(0).getCategories().size());
	}
	
	@Test
	public void testUpdateOrder() throws SQLException, JSONException {
		database.addOrder(orderOne);
		List<Order> orders = database.getOrders();
		Assert.assertEquals(1, orders.size());
		Assert.assertTrue(orderOne.equals(orders.get(0)));
		
		orderOne.setProvidingMemberId("ProvidingMemberId");
		orderOne.getCategories().add(new Category("@@@", "@@@", "@@@"));
		orderOne.getxOCCIAtt().put("@@", "@@");
		orderOne.getFederationToken().setExpirationDate(new Date());
		
		database.updateOrder(orderOne);
		
		orders = database.getOrders();
		Assert.assertEquals(1, orders.size());
		Assert.assertEquals(orderOne, orders.get(0));	
	}

	
	@Test
	public void testRemoveOrder() throws SQLException, JSONException {
		List<Order> orders = new ArrayList<Order>();
		orders.add(orderOne);
		orders.add(orderTwo);
		orders.add(orderThree);
		
		for (Order order : orders) {
			database.addOrder(order);
		}
		
		List<Order> ordersDB = database.getOrders();
		Assert.assertEquals(3, ordersDB.size());
		
		database.removeOrder(orderOne);
		
		ordersDB = database.getOrders();
		Assert.assertEquals(2, ordersDB.size());
		
		database.removeOrder(orderTwo);
		database.removeOrder(orderThree);
		
		ordersDB = database.getOrders();
		Assert.assertEquals(0, ordersDB.size());		
	}
	
	@Test
	public void testRemoveAllOrder() throws SQLException, JSONException {
		List<Order> orders = new ArrayList<Order>();
		orders.add(orderOne);
		orders.add(orderTwo);
		orders.add(orderThree);
		
		for (Order order : orders) {
			database.addOrder(order);
		}
		
		List<Order> ordersDB = database.getOrders();
		Assert.assertEquals(3, ordersDB.size());
		
		database.removeAllOrder();
		
		ordersDB = database.getOrders();
		Assert.assertEquals(0, ordersDB.size());		
	}	
	
	@Test
	public void testCountOrder() throws SQLException, JSONException {
		List<Order> orders = new ArrayList<Order>();
		orders.add(orderOne);
		orders.add(orderTwo);
		orders.add(orderThree);
		orders.add(orderFour);
		
		for (Order order : orders) {
			database.addOrder(order);
		}
		
		List<OrderState> orderStates = new ArrayList<OrderState>();
		int count = database.countOrder(orderStates);
		Assert.assertEquals(orders.size(), count);
		
		orderStates.add(OrderState.FULFILLED);
		
		count = database.countOrder(orderStates);
		Assert.assertEquals(1, count);
		
		orderStates.add(OrderState.DELETED);
		
		count = database.countOrder(orderStates);
		Assert.assertEquals(2, count);
		
		orderStates.clear();
		
		orderStates.add(OrderState.OPEN);
		
		count = database.countOrder(orderStates);
		Assert.assertEquals(2, count);
	}
	
	@Test
	public void getStorageLinks() throws SQLException, JSONException {
		List<StorageLink> storageLinks = new ArrayList<StorageLinkRepository.StorageLink>();
		storageLinks.add(storageLinkOne);
		storageLinks.add(storageLinkTwo);
		
		for (StorageLink storageLink : storageLinks) {
			database.addStorageLink(storageLink);
		}
		
		Assert.assertEquals(storageLinks.size(), database.getStorageLinks().size());
	}
	
	@Test
	public void addStorageLink() throws SQLException, JSONException {
		database.addStorageLink(storageLinkOne);
		List<StorageLink> storageLinks = database.getStorageLinks();
		
		Assert.assertEquals(1, storageLinks.size());
		Assert.assertTrue(storageLinks.get(0).equals(storageLinkOne));
	}	
	
	@Test
	public void updateStorageLink() throws SQLException, JSONException {
		database.addStorageLink(storageLinkOne);
		List<StorageLink> storageLinks = database.getStorageLinks();
		
		Assert.assertEquals(1, storageLinks.size());
		Assert.assertTrue(storageLinks.get(0).equals(storageLinkOne));
		
		Token federationToken = new Token("accessId", "user", new Date(), null);
		storageLinkOne = new StorageLink(storageLinkOne.getId(), "source", "target", "deviceId", "provadingMemberId", federationToken, true);
		
		database.updateStorageLink(storageLinkOne);
		
		storageLinks.clear();
		storageLinks = database.getStorageLinks();
		Assert.assertTrue(storageLinks.get(0).equals(storageLinkOne));
	}		
	
	@Test
	public void removeStorageLink() throws SQLException, JSONException {
		List<StorageLink> storageLinks = new ArrayList<StorageLinkRepository.StorageLink>();
		storageLinks.add(storageLinkOne);
		storageLinks.add(storageLinkTwo);
		storageLinks.add(storageLinkThree);
		
		for (StorageLink storageLink : storageLinks) {
			database.addStorageLink(storageLink);
		}
		
		Assert.assertEquals(storageLinks.size(), database.getStorageLinks().size());
		
		database.removeStorageLink(storageLinkOne);
		
		Assert.assertEquals(2, database.getStorageLinks().size());
	}
	
	private void initializeStorageLinks() {
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put("key", "value");
		Token federationToken = new Token("accessId", "user", new Date(),
				attributes);
		this.storageLinkOne = new StorageLink("one", "sourceOne", "targetOne",
				"deviceIdOne", "provadingMemberIdOne", federationToken, true);
		this.storageLinkTwo = new StorageLink("two", "sourceTwo", "targetTwo",
				"deviceIdTwo", "provadingMemberIdTwo", federationToken, true);
		this.storageLinkThree = new StorageLink("three", "sourceThree",
				"targetThree", "deviceIdThree", "provadingMemberIdThree",
				federationToken, true);
	}		
	
	private void initializeOrders() {
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("attrOne", "valueOne");
		attributes.put("attrTwo", "valueTwo");
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category("term", "schem", "class"));
		categories.add(new Category("termTwo", "schemTwo", "classTwo"));
		Map<String, String> xOCCIAttributes = new HashMap<String, String>();
		xOCCIAttributes.put("occiAttr1.occi", "occiValue1");
		xOCCIAttributes.put("occiAttr2.occi", "occiValue2=");
		xOCCIAttributes.put("occiAttr3.occi", "x>=1 && y=1");
		Token token = new Token("accessIdToken", "user", new Date(), attributes);
		orderOne =  new Order("requstIdOne", token , "instanceIdOne", "providerOne", "memberOne",
				new Date().getTime(), true, OrderState.OPEN, categories, xOCCIAttributes);
		orderTwo =  new Order("requstIdTwo", token , "instanceIdTwo", "providerTwo", "memberTwo",
				new Date().getTime(), true, OrderState.OPEN, categories, xOCCIAttributes);
		orderThree = new Order("requstIdThree", token, "instanceIdThree", "providerThree",
				"memberThree", new Date().getTime(), true, OrderState.FULFILLED,
				new ArrayList<Category>(), new HashMap<String, String>());
		HashMap<String, String> xOCCIAttributesTwo = new HashMap<String, String>();
		xOCCIAttributesTwo.put("1.22.3.5.1", "#@$#gv=.j0");
		orderFour = new Order("requstIdFour", token, "instanceIdThree", "providerThree",
				"memberThree", new Date().getTime(), true, OrderState.DELETED,
				new ArrayList<Category>(), xOCCIAttributesTwo);		
	}
	
}
