package org.fogbowcloud.manager.core.plugins.benchmarking.ssh;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.fogbowcloud.manager.occi.model.OCCIException;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSHHBenchmarkingDataStore {
	
	private static final String DATASTORE_PATH = "src/test/resources/SHHBenchmarkingDataStore.sqlite";
	protected static final String DATASTORE_URL = "jdbc:sqlite:" + DATASTORE_PATH;
	private SHHBenchmarkingDataStore sshDataStore;
	private Properties properties;
	
	@Before
	public void setUp() {		
		removeBD();
		this.properties = new Properties();
		this.properties.put(SHHBenchmarkingDataStore.SHH_BENCHMARKING_DATASTORE_URL , DATASTORE_URL);
		this.sshDataStore = new SHHBenchmarkingDataStore(this.properties);
	}
	
	@After
	public void tearDown() throws IOException{
		removeBD();
	}

	@Test(expected=OCCIException.class)
	public void testInitializeSHHBenchmarkingDataStoreWithoutPath() {
		new SHHBenchmarkingDataStore(new Properties());
	}
	
	@Test
	public void testCreateAndGetPower() throws SQLException, JSONException {
		String instanceId = "instanceId";
		Double power = 10.0;
		this.sshDataStore.addInstancePower(instanceId, power);
		
		Assert.assertEquals(power, this.sshDataStore.getPower(instanceId));
	}
	
	@Test
	public void testGetPowerNotFound() throws SQLException, JSONException {
		String instanceId = "instanceId";
		Double power = 10.0;
		this.sshDataStore.addInstancePower(instanceId, power);
		
		Assert.assertNull(this.sshDataStore.getPower("worng_instance_id"));
	}
	
	@Test
	public void testRemoveInstancePower() throws SQLException, JSONException {
		String instanceIdOne = "instance_id_one";
		Double power = 10.0;
		this.sshDataStore.addInstancePower(instanceIdOne, power);
		String instanceIdTwo = "instance_id_two";
		this.sshDataStore.addInstancePower(instanceIdTwo, power);
		
		Assert.assertEquals(power, this.sshDataStore.getPower(instanceIdOne));
		Assert.assertEquals(power, this.sshDataStore.getPower(instanceIdTwo));
		
		// removing instance One
		this.sshDataStore.removeInstancePower(instanceIdOne);
		Assert.assertNull(this.sshDataStore.getPower(instanceIdOne));
		Assert.assertEquals(power, this.sshDataStore.getPower(instanceIdTwo));

		// removing instance Two
		this.sshDataStore.removeInstancePower(instanceIdTwo);
		Assert.assertNull(this.sshDataStore.getPower(instanceIdOne));
		Assert.assertNull(this.sshDataStore.getPower(instanceIdTwo));		
	}
	
	protected static void removeBD() {
		File dbFile = new File(DATASTORE_PATH);
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}
	
}
