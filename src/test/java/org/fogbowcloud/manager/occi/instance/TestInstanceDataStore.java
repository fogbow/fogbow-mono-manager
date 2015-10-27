package org.fogbowcloud.manager.occi.instance;

import static org.junit.Assert.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
		
		assertTrue(instanceDb.insert(fakeInstanceId_A, fakeOrderId_A));
		Map<String, String> returnedMap = instanceDb.getAll();
		
		assertNotNull(returnedMap);
		assertEquals(1, returnedMap.keySet().size());
		assertEquals(fakeOrderId_A, returnedMap.get(fakeInstanceId_A));
	}

	@Test
	public void testMultipleInsert(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		
		Map<String, String> fakeInstanceOrdersMap = new HashMap<String, String>();
		fakeInstanceOrdersMap.put(fakeInstanceId_A, fakeOrderId_A);
		fakeInstanceOrdersMap.put(fakeInstanceId_B, fakeOrderId_B);
		
		instanceDb.insert(fakeInstanceOrdersMap);
		Map<String, String> returnedMap = instanceDb.getAll();
		
		assertNotNull(returnedMap);
		assertEquals(2, returnedMap.keySet().size());
		assertEquals(fakeOrderId_A, returnedMap.get(fakeInstanceId_A));
		assertEquals(fakeOrderId_B, returnedMap.get(fakeInstanceId_B));
		
	}
	
	@Test
	public void testUpdate(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		
		instanceDb.insert(fakeInstanceId_A, fakeOrderId_A);
		Map<String, String> returnedMap = instanceDb.getAll();
		
		assertNotNull(returnedMap);
		assertEquals(1, returnedMap.keySet().size());
		assertEquals(fakeOrderId_A, returnedMap.get(fakeInstanceId_A));
		
		instanceDb.update(fakeInstanceId_A, fakeOrderId_B);
		
		returnedMap = instanceDb.getAll();
		assertEquals(fakeOrderId_B, returnedMap.get(fakeInstanceId_A));
	}
	
	@Test
	public void testGetByInstanceId(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		
		Map<String, String> fakeInstanceOrdersMap = new HashMap<String, String>();
		fakeInstanceOrdersMap.put(fakeInstanceId_A, fakeOrderId_A);
		fakeInstanceOrdersMap.put(fakeInstanceId_B, fakeOrderId_B);
		
		instanceDb.insert(fakeInstanceOrdersMap);
		Map<String, String> returnedMap = instanceDb.getByInstanceId(fakeInstanceId_B);
		
		assertNotNull(returnedMap);
		assertEquals(1, returnedMap.keySet().size());
		assertEquals(null, returnedMap.get(fakeInstanceId_A));
		assertEquals(fakeOrderId_B, returnedMap.get(fakeInstanceId_B));
		
	}
	
	@Test
	public void testGetByOrderId(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		
		Map<String, String> fakeInstanceOrdersMap = new HashMap<String, String>();
		fakeInstanceOrdersMap.put(fakeInstanceId_A, fakeOrderId_A);
		fakeInstanceOrdersMap.put(fakeInstanceId_B, fakeOrderId_B);
		
		instanceDb.insert(fakeInstanceOrdersMap);
		Map<String, String> returnedMap = instanceDb.getByOrderId(fakeOrderId_A);
		
		assertNotNull(returnedMap);
		assertEquals(1, returnedMap.keySet().size());
		assertEquals(fakeOrderId_A, returnedMap.get(fakeInstanceId_A));
		assertEquals(null, returnedMap.get(fakeInstanceId_B));
		
	}
	
	@Test
	public void testDeleteAll(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		
		Map<String, String> fakeInstanceOrdersMap = new HashMap<String, String>();
		fakeInstanceOrdersMap.put(fakeInstanceId_A, fakeOrderId_A);
		fakeInstanceOrdersMap.put(fakeInstanceId_B, fakeOrderId_B);
		
		instanceDb.insert(fakeInstanceOrdersMap);
		instanceDb.deleteAll();
		Map<String, String> returnedMap = instanceDb.getAll();
		
		assertNotNull(returnedMap);
		assertEquals(0, returnedMap.keySet().size());
		assertEquals(null, returnedMap.get(fakeInstanceId_A));
		assertEquals(null, returnedMap.get(fakeInstanceId_B));
		
	}
	
	@Test
	public void testDeleteByIntanceId(){
		
		String fakeInstanceId_A = "InstanceA";
		String fakeInstanceId_B = "InstanceB";
		String fakeOrderId_A = "OrderA";
		String fakeOrderId_B = "OrderB";
		
		Map<String, String> fakeInstanceOrdersMap = new HashMap<String, String>();
		fakeInstanceOrdersMap.put(fakeInstanceId_A, fakeOrderId_A);
		fakeInstanceOrdersMap.put(fakeInstanceId_B, fakeOrderId_B);
		
		instanceDb.insert(fakeInstanceOrdersMap);
		instanceDb.deleteByIntanceId(fakeInstanceId_A);
		Map<String, String> returnedMap = instanceDb.getAll();
		
		assertNotNull(returnedMap);
		assertEquals(1, returnedMap.keySet().size());
		assertEquals(null, returnedMap.get(fakeInstanceId_A));
		assertEquals(fakeOrderId_B, returnedMap.get(fakeInstanceId_B));
		
	}
}
