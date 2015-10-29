package org.fogbowcloud.manager.occi.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestInstanceDataStore {

	//private static final String DATA_STORE_FILE = "~/src/test/resources/persistance/instanceOrder.db";
	private static final String DATA_STORE_URL = "jdbc:h2:mem:";
	private InstanceDataStore instanceDb;
	
	@Before
	public void setup() throws Exception {
		
		instanceDb = new InstanceDataStore(DATA_STORE_URL);
		instanceDb.deleteAll();
	}

	@After
	public void tearDown() throws Exception {
		instanceDb.deleteAll();
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
		
		FedInstanceState fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_B);
		
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
		
		FedInstanceState fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_B);
		
		assertNotNull(fedInstanceState);
		System.out.println(categories);
		System.out.println(fedInstanceState.getCategories());
		System.out.println(categories);
		System.out.println(fedInstanceState.getLinks());
		assertEquals(fedInstanceStateB, fedInstanceState);
		
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
		
		FedInstanceState fedInstanceState = instanceDb.getByInstanceId(fakeInstanceId_C);
		
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
		
		FedInstanceState fedInstanceState = instanceDb.getByOrderId(fakeOrderId_A);
		
		assertNotNull(fedInstanceState);
		assertEquals(fedInstanceStateA, fedInstanceState);
		
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
		instanceDb.deleteByIntanceId(fakeInstanceId_A);
		List<FedInstanceState> fedInstanceStateList = instanceDb.getAll();
		
		assertNotNull(fedInstanceStateList);
		assertEquals(1, fedInstanceStateList.size());
		assertFalse(fedInstanceStateList.contains(fedInstanceStateA));
		assertTrue(fedInstanceStateList.contains(fedInstanceStateB));
		
	}
	
	
}
