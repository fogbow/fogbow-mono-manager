package org.fogbowcloud.manager.occi.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.fogbowcloud.manager.occi.ManagerDataStore;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestInstanceDataStore {

	private static final String DATA_STORE_FILE = "src/test/resources/testInstanceOrder.sqlite";
	private static final String DATA_STORE_URL = "jdbc:sqlite:" + DATA_STORE_FILE;
	private InstanceDataStore instanceDb;
	
	@Before
	public void setup() throws Exception {
		TestDataStorageHelper.removeDefaultFolderDataStore();
		instanceDb = new InstanceDataStore(DATA_STORE_URL);
		instanceDb.deleteAll();
	}

	@After
	public void tearDown() throws Exception {
		TestDataStorageHelper.removeDefaultFolderDataStore();
		instanceDb.deleteAll();
		File dbFile = new File(DATA_STORE_FILE);
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}
	
	@Test
	public void testInitializeWithError() {
		try {
			Properties properties = new Properties();
			// to force error with "/dev/null"
			properties.put(ManagerDataStore.MANAGER_DATASTORE_URL, "/dev/null");
			String urlImpossible = "/dev/null";
			new InstanceDataStore(urlImpossible);
			Assert.fail();
		} catch (Error e) {
			Assert.assertEquals(InstanceDataStore.ERROR_WHILE_INITIALIZING_THE_DATA_STORE, 
					e.getMessage());
		}		
	}
	
	@Test
	public void testInsert(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeOrderId_A = "OrderA";
		String fakeUserA = "UserA";
		
		FedInstanceState fedInstanceState = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		
		assertTrue(instanceDb.insert(fedInstanceState));
		List<FedInstanceState> fedInstanceStateList = instanceDb.getAll();
		
		assertNotNull(fedInstanceStateList);
		assertEquals(1, fedInstanceStateList.size());
		assertEquals(fakeOrderId_A, fedInstanceStateList.get(0).getOrderId());
	}

	@Test
	public void testMultipleInsert(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		List<FedInstanceState> fedInstanceStateList = instanceDb.getAll();
		
		assertNotNull(fedInstanceStateList);
		assertEquals(2, fedInstanceStateList.size());
		assertTrue(fedInstanceStateList.contains(fedInstanceStateA));
		assertTrue(fedInstanceStateList.contains(fedInstanceStateB));
		
	}
	
	@Test
	public void testUpdate(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeOrderId_A = "OrderA";
		String fakeGlobalInstanceId_A = "GlobalInstanceIdA";
		String fakeUserA = "UserA";
		
		FedInstanceState fedInstanceState = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		
		instanceDb.insert(fedInstanceState);
		List<FedInstanceState> fedInstanceStateList = instanceDb.getAll();
		
		assertNotNull(fedInstanceStateList);
		assertEquals(1, fedInstanceStateList.size());
		assertEquals("", fedInstanceStateList.get(0).getGlobalInstanceId());
		
		fedInstanceState.setGlobalInstanceId(fakeGlobalInstanceId_A);
		
		instanceDb.update(fedInstanceState);
		
		fedInstanceStateList = instanceDb.getAll();
		assertEquals(fakeGlobalInstanceId_A, fedInstanceStateList.get(0).getGlobalInstanceId());
	}
	
	@Test
	public void testGetByUser(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeInstanceId_C = "InstanceC";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeOrderId_C = "OrderC";
		String fakeGlobalInstanceId_A = "GlobalInstanceIdA";
		String fakeGlobalInstanceId_B = "GlobalInstanceIdB";
		String fakeGlobalInstanceId_C = "GlobalInstanceIdC";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), fakeGlobalInstanceId_A, fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), fakeGlobalInstanceId_B, fakeUserB);
		FedInstanceState fedInstanceStateC = new FedInstanceState(fakeInstanceId_C, fakeOrderId_C, new ArrayList<Category>(), new ArrayList<Link>(), fakeGlobalInstanceId_C, fakeUserA);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		fakeFedInstanceStateList.add(fedInstanceStateC);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		List<FedInstanceState> fedInstanceStateListA = instanceDb.getAllByUser(fakeUserA);
		List<FedInstanceState> fedInstanceStateListB = instanceDb.getAllByUser(fakeUserB);
		
		assertNotNull(fedInstanceStateListA);
		assertEquals(2, fedInstanceStateListA.size());
		assertTrue(fedInstanceStateListA.contains(fedInstanceStateA));
		assertFalse(fedInstanceStateListA.contains(fedInstanceStateB));
		assertTrue(fedInstanceStateListA.contains(fedInstanceStateC));
		
		assertNotNull(fedInstanceStateListB);
		assertEquals(1, fedInstanceStateListB.size());
		assertFalse(fedInstanceStateListB.contains(fedInstanceStateA));
		assertTrue(fedInstanceStateListB.contains(fedInstanceStateB));
		assertFalse(fedInstanceStateListB.contains(fedInstanceStateC));
		
	}
	
	@Test
	public void testGetByInstanceId(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeGlobalInstanceId_A = "GlobalInstanceIdA";
		String fakeGlobalInstanceId_B = "GlobalInstanceIdB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), fakeGlobalInstanceId_A, fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), fakeGlobalInstanceId_B, fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		FedInstanceState fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_B, fakeUserB);
		
		assertNotNull(fedInstanceState);
		assertEquals(fedInstanceStateB, fedInstanceState);
		
	}
	
	@Test
	public void testGetByInstanceIdWithCategories(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeGlobalInstanceId_A = "GlobalInstanceIdA";
		String fakeGlobalInstanceId_B = "GlobalInstanceIdB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category("term1", "scheme1", "class1"));
		categories.add(new Category("term2", "scheme2", "class2"));
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, categories, new ArrayList<Link>(), fakeGlobalInstanceId_A, fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, categories, new ArrayList<Link>(), fakeGlobalInstanceId_B, fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		FedInstanceState fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_B, fakeUserB);
		
		assertNotNull(fedInstanceState);
		assertEquals(fedInstanceStateB, fedInstanceState);
		
	}
	
	@Test
	public void testGetByInstanceIdWithCategoriesAndLinks(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeGlobalInstanceId_A = "GlobalInstanceIdA";
		String fakeGlobalInstanceId_B = "GlobalInstanceIdB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category("term1", "scheme1", "class1"));
		categories.add(new Category("term2", "scheme2", "class2"));
		
		Map<String, String> linkAttrsForInstanceA = new HashMap<String, String>();
		linkAttrsForInstanceA.put("occi.networkinterface.gateway", "Not defined");
		linkAttrsForInstanceA.put("occi.networkinterface.mac", "Not defined");
		linkAttrsForInstanceA.put("occi.networkinterface.interface", "eth0");
		linkAttrsForInstanceA.put("occi.networkinterface.state", "active");
		linkAttrsForInstanceA.put("occi.networkinterface.allocation", "static");
		linkAttrsForInstanceA.put("occi.networkinterface.address", "host");
		linkAttrsForInstanceA.put("occi.core.source", "/compute/" + fakeInstanceId_A);
		linkAttrsForInstanceA.put("occi.core.target", "/network/public");
		String fakeLinkIdForA = "/network/interface/" + UUID.randomUUID().toString();
		linkAttrsForInstanceA.put("occi.core.id", fakeLinkIdForA);		
		ArrayList<Link> linksForInstanceA = new ArrayList<Link>();
		linksForInstanceA.add(new Link(fakeLinkIdForA, linkAttrsForInstanceA));

		Map<String, String> linkAttrsForInstanceB = new HashMap<String, String>();
		linkAttrsForInstanceB.put("occi.networkinterface.gateway", "Not defined");
		linkAttrsForInstanceB.put("occi.networkinterface.mac", "Not defined");
		linkAttrsForInstanceB.put("occi.networkinterface.interface", "eth0");
		linkAttrsForInstanceB.put("occi.networkinterface.state", "active");
		linkAttrsForInstanceB.put("occi.networkinterface.allocation", "static");
		linkAttrsForInstanceB.put("occi.networkinterface.address", "host");
		linkAttrsForInstanceB.put("occi.core.source", "/compute/" + fakeInstanceId_B);
		linkAttrsForInstanceB.put("occi.core.target", "/network/public");
		String fakeLinkIdForB = "/network/interface/" + UUID.randomUUID().toString();
		linkAttrsForInstanceB.put("occi.core.id", fakeLinkIdForB);		
		ArrayList<Link> linksForInstanceB = new ArrayList<Link>();
		linksForInstanceB.add(new Link(fakeLinkIdForB, linkAttrsForInstanceB));
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, categories, linksForInstanceA, fakeGlobalInstanceId_A, fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, categories, linksForInstanceB, fakeGlobalInstanceId_B, fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		FedInstanceState fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_B, fakeUserB);
		
		assertNotNull(fedInstanceState);
		assertEquals(fakeInstanceId_B, fedInstanceState.getFedInstanceId());
		assertEquals(fakeOrderId_B, fedInstanceState.getOrderId());
		assertEquals(fakeUserB, fedInstanceState.getUserId());
		assertEquals(categories, fedInstanceState.getCategories());
		assertEquals(linksForInstanceB.size(), fedInstanceState.getLinks().size());
		assertEquals(linksForInstanceB.get(0).getAttributes(), fedInstanceState.getLinks().get(0).getAttributes());
		assertEquals(linksForInstanceB.get(0).getName(), fedInstanceState.getLinks().get(0).getName());
		
		fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_A, fakeUserA);
		
		assertNotNull(fedInstanceState);
		assertEquals(fakeInstanceId_A, fedInstanceState.getFedInstanceId());
		assertEquals(fakeOrderId_A, fedInstanceState.getOrderId());
		assertEquals(fakeUserA, fedInstanceState.getUserId());
		assertEquals(categories, fedInstanceState.getCategories());
		assertEquals(linksForInstanceA.size(), fedInstanceState.getLinks().size());
		assertEquals(linksForInstanceA.get(0).getAttributes(), fedInstanceState.getLinks().get(0).getAttributes());
		assertEquals(linksForInstanceA.get(0).getName(), fedInstanceState.getLinks().get(0).getName());		
	}
	
	@Test
	public void testGetByInstanceIdWrongID(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeInstanceId_C = "InstanceC";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		FedInstanceState fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_C, fakeUserB);
		
		assertNull(fedInstanceState);
		
	}
	
	@Test
	public void testGetByOrderId(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		FedInstanceState fedInstanceState = instanceDb.getByOrderId(fakeOrderId_A, fakeUserA);
		
		assertNotNull(fedInstanceState);
		assertEquals(fedInstanceStateA, fedInstanceState);
		
	}
	
	@Test
	public void testGetByOrderIdWithWrongUser(){		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		FedInstanceState fedInstanceState = instanceDb.getByOrderId(fakeOrderId_A, fakeUserB);

		assertNull(fedInstanceState);
		assertEquals(2, instanceDb.getAll().size());
	}
	
	@Test
	public void testGetByInstanceIdWithWrongUser(){		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		FedInstanceState fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_A, fakeUserB);

		assertNull(fedInstanceState);
		assertEquals(2, instanceDb.getAll().size());
	}
	
	@Test
	public void testDeleteByOrderIdWithWrongUser(){		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		
		assertFalse(instanceDb.deleteByIntanceId(fakeInstanceId_A, fakeUserB));
		assertEquals(2, instanceDb.getAll().size());
	}
	
	@Test
	public void testDeleteAll(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A,new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		instanceDb.deleteAll();
		List<FedInstanceState> fedInstanceStateList = instanceDb.getAll();
		
		assertNotNull(fedInstanceStateList);
		assertEquals(0, fedInstanceStateList.size());
		
	}
	
	@Test
	public void testDeleteAllByUser(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		instanceDb.deleteAllFromUser(fakeUserA);
		List<FedInstanceState> fedInstanceStateList = instanceDb.getAll();
		
		assertNotNull(fedInstanceStateList);
		assertEquals(1, fedInstanceStateList.size());
		assertFalse(fedInstanceStateList.contains(fedInstanceStateA));
		assertTrue(fedInstanceStateList.contains(fedInstanceStateB));
		
	}
	
	@Test
	public void testDeleteByIntanceId(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		String fakeUserA = "UserA";
		String fakeUserB = "UserB";
		
		FedInstanceState fedInstanceStateA = new FedInstanceState(fakeInstanceId_A, fakeOrderId_A, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserA);
		FedInstanceState fedInstanceStateB = new FedInstanceState(fakeInstanceId_B, fakeOrderId_B, new ArrayList<Category>(), new ArrayList<Link>(), "", fakeUserB);
		
		List<FedInstanceState> fakeFedInstanceStateList =  new ArrayList<FedInstanceState>();
		fakeFedInstanceStateList.add(fedInstanceStateA);
		fakeFedInstanceStateList.add(fedInstanceStateB);
		
		instanceDb.insert(fakeFedInstanceStateList);
		instanceDb.deleteByIntanceId(fakeInstanceId_A, fakeUserA);
		List<FedInstanceState> fedInstanceStateList = instanceDb.getAll();
		
		assertNotNull(fedInstanceStateList);
		assertEquals(1, fedInstanceStateList.size());
		assertFalse(fedInstanceStateList.contains(fedInstanceStateA));
		assertTrue(fedInstanceStateList.contains(fedInstanceStateB));
		
	}
	
	
}
