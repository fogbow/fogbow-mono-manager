package org.fogbowcloud.manager.occi.request;

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
import org.fogbowcloud.manager.core.plugins.accounting.TestDataStore;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestOrderDataStore {

	private static final Logger LOGGER = Logger.getLogger(TestDataStore.class);
	 
	private final String DATASTORE_PATH = "src/test/resources/testOrderDataStore.sqlite";
	private final String DATASTORE_URL = "jdbc:sqlite:" + DATASTORE_PATH;
	
	private Request orderOne;
	private Request orderTwo;
	private Request orderThree;
	private Request orderFour;	
	
	private Properties properties = null;
	private OrderDataStore database = null; 
	
	@Before
	public void initialize() {		
		LOGGER.debug("Creating data store.");
		properties = new Properties();
		properties.put(OrderDataStore.ORDER_DATASTORE_URL , DATASTORE_URL);
		database = new OrderDataStore(properties);
		initializeOrders();
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
		List<Request> orders = database.getOrders();
		Assert.assertEquals(1, orders.size());

		Assert.assertTrue(orderOne.equals(orders.get(0)));
	}
	
	@Test
	public void testGetOrders() throws SQLException, JSONException {
		List<Request> orders = new ArrayList<Request>();
		orders.add(orderOne);
		orders.add(orderTwo);
		orders.add(orderThree);
		
		for (Request order : orders) {
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
		List<Request> orders = database.getOrders();
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
		List<Request> orders = new ArrayList<Request>();
		orders.add(orderOne);
		orders.add(orderTwo);
		orders.add(orderThree);
		
		for (Request order : orders) {
			database.addOrder(order);
		}
		
		List<Request> ordersDB = database.getOrders();
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
		List<Request> orders = new ArrayList<Request>();
		orders.add(orderOne);
		orders.add(orderTwo);
		orders.add(orderThree);
		
		for (Request order : orders) {
			database.addOrder(order);
		}
		
		List<Request> ordersDB = database.getOrders();
		Assert.assertEquals(3, ordersDB.size());
		
		database.removeAllOrder();
		
		ordersDB = database.getOrders();
		Assert.assertEquals(0, ordersDB.size());		
	}	
	
	@Test
	public void testCountOrder() throws SQLException, JSONException {
		List<Request> orders = new ArrayList<Request>();
		orders.add(orderOne);
		orders.add(orderTwo);
		orders.add(orderThree);
		orders.add(orderFour);
		
		for (Request order : orders) {
			database.addOrder(order);
		}
		
		List<RequestState> requestStates = new ArrayList<RequestState>();
		int count = database.countOrder(requestStates);
		Assert.assertEquals(orders.size(), count);
		
		requestStates.add(RequestState.FULFILLED);
		
		count = database.countOrder(requestStates);
		Assert.assertEquals(1, count);
		
		requestStates.add(RequestState.DELETED);
		
		count = database.countOrder(requestStates);
		Assert.assertEquals(2, count);
		
		requestStates.clear();
		
		requestStates.add(RequestState.OPEN);
		
		count = database.countOrder(requestStates);
		Assert.assertEquals(2, count);
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
		orderOne =  new Request("requstIdOne", token , "instanceIdOne", "providerOne", "memberOne",
				new Date().getTime(), true, RequestState.OPEN, categories, xOCCIAttributes);
		orderTwo =  new Request("requstIdTwo", token , "instanceIdTwo", "providerTwo", "memberTwo",
				new Date().getTime(), true, RequestState.OPEN, categories, xOCCIAttributes);
		orderThree = new Request("requstIdThree", token, "instanceIdThree", "providerThree",
				"memberThree", new Date().getTime(), true, RequestState.FULFILLED,
				new ArrayList<Category>(), new HashMap<String, String>());
		HashMap<String, String> xOCCIAttributesTwo = new HashMap<String, String>();
		xOCCIAttributesTwo.put("1.22.3.5.1", "#@$#gv=.j0");
		orderFour = new Request("requstIdFour", token, "instanceIdThree", "providerThree",
				"memberThree", new Date().getTime(), true, RequestState.DELETED,
				new ArrayList<Category>(), xOCCIAttributesTwo);		
	}
	
}
