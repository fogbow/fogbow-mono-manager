package org.fogbowcloud.manager.occi;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestStorageLinkDataStore {

	private final String DATASTORE_PATH = "src/test/resources/testStorageLinkDataStore.sqlite";
	private final String DATASTORE_URL = "jdbc:sqlite:" + DATASTORE_PATH;
	
	private StorageLink storageLinkOne;
	private StorageLink storageLinkTwo;
	private StorageLink storageLinkThree;
	
	private Properties properties = null;
	private StorageLinkDataStore database = null; 
	
	@Before
	public void setUp() {		
		properties = new Properties();
		properties.put(StorageLinkDataStore.STORAGELINK_DATASTORE_URL , DATASTORE_URL);
		database = new StorageLinkDataStore(properties);
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
		Token federationToken = new Token("accessId", "user", new Date(), attributes);
		this.storageLinkOne = new StorageLink("one", "sourceOne", "targetOne", "deviceIdOne", "provadingMemberIdOne", federationToken, true);
		this.storageLinkTwo = new StorageLink("two", "sourceTwo", "targetTwo", "deviceIdTwo", "provadingMemberIdTwo", federationToken, true);
		this.storageLinkThree = new StorageLink("three", "sourceThree", "targetThree", "deviceIdThree", "provadingMemberIdThree", federationToken, true);
	}	
	
}
