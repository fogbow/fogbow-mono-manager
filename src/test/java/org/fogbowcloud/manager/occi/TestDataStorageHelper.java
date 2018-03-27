package org.fogbowcloud.manager.occi;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDataStorageHelper {
	
	@Before
	public void setUp() {
		removeDefaultFolderDataStore();
	}
	
	@After
	public void tearDown() {
		removeDefaultFolderDataStore();
		DataStoreHelper.dataStoreFolderExecution = null;
	}
	
	public static void clearManagerDataStore(ManagerDataStore managerDataStore) {
		if (managerDataStore == null) {
			return;
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			;
		}
		managerDataStore.removeAllValuesInAllTable();
	}
	
	public static void removeDefaultFolderDataStore() {
		String pathFolderDataStores = DataStoreHelper.getPathFolderDataStoresFogbowManager();
		File folder = new File(pathFolderDataStores);
		File[] listFiles = folder.listFiles();
		if (listFiles != null) {
			for (File file : listFiles) {
				if (file != null) {
					file.delete();
					file.deleteOnExit();
				}
			}			
		}
		if (folder != null) {
			folder.delete();
			folder.deleteOnExit();
		}
	}
	
	@Test
	public void testGetDataStoreUrlOk() {
		String dataStoreUrl = "/path";
		String dataStoreUrlReturned = DataStoreHelper.getDataStoreUrl(dataStoreUrl, "");
		
		Assert.assertEquals(dataStoreUrl, dataStoreUrlReturned);
	}
	
	@Test
	public void testGetDataStoreUrlWithDefaultPathTest() {
		String pathDefaultFolderDataStores = DataStoreHelper.getPathFolderDataStoresFogbowManager();
		Assert.assertTrue(pathDefaultFolderDataStores.endsWith(DataStoreHelper.DATASTORES_TEST_FOLDER));
		String dataStoreUrl = null;
		String dataStoreName = "db_defaul.sqlite";
		String dataStoreUrlReturned = DataStoreHelper.getDataStoreUrl(dataStoreUrl, dataStoreName);			
		String dataStoreUrlExpected = DataStoreHelper.PREFIX_DATASTORE_URL + 
				pathDefaultFolderDataStores + "/" + dataStoreName;		
		
		Assert.assertEquals(dataStoreUrlExpected, dataStoreUrlReturned);
		Assert.assertTrue(new File(pathDefaultFolderDataStores).exists());
	}	
	
	@Test
	public void testGetDataStoreUrlWithDefaultPathExecution() {
		String dataStoreFolderExecution = "/datastore";
		DataStoreHelper.setDataStoreFolderExecution(dataStoreFolderExecution);
		
		String pathDefaultFolderDataStores = DataStoreHelper.getPathFolderDataStoresFogbowManager();
		Assert.assertTrue(pathDefaultFolderDataStores.endsWith(dataStoreFolderExecution));
		String dataStoreUrl = null;
		String dataStoreName = "db_defaul.sqlite";
		String dataStoreUrlReturned = DataStoreHelper.getDataStoreUrl(dataStoreUrl, dataStoreName);			
		String dataStoreUrlExpected = DataStoreHelper.PREFIX_DATASTORE_URL + 
				pathDefaultFolderDataStores + "/" + dataStoreName;		
		
		Assert.assertEquals(dataStoreUrlExpected, dataStoreUrlReturned);
		Assert.assertTrue(new File(pathDefaultFolderDataStores).exists());
	}	
		
}
