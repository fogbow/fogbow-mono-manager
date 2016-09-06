package org.fogbowcloud.manager.core.plugins.benchmarking.ssh;

import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import net.schmizz.sshj.connection.channel.direct.Session.Command;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.util.SshHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestSSHBenchmarkingPlugin {

	private SSHBenchmarkingPlugin sshBenchmarkingPlugin;
	private Properties properties;
	
	@Before
	public void setUp() {
		removeSSHDataStoraDefault();
		this.properties = new Properties();
		this.properties.put(ConfigurationConstants.SSH_COMMON_USER, "");
		this.properties.put(SHHBenchmarkingDataStore.SHH_BENCHMARKING_DATASTORE_URL, 
				TestSHHBenchmarkingDataStore.DATASTORE_URL);
		this.sshBenchmarkingPlugin = new SSHBenchmarkingPlugin(this.properties);
	}
	
	@After
	public void tearDown() {	
		removeSSHDataStoraDefault();
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void testGetSHHCommonUser() {
		Properties properties = new Properties();
		String sshCommonUser = "ssh";
		properties.put(ConfigurationConstants.SSH_COMMON_USER, sshCommonUser);
		Assert.assertEquals(sshCommonUser, 
				this.sshBenchmarkingPlugin.getSSHCommonUser(properties));
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void testGetSHHCommonUserDefaultValue() {	 
		Assert.assertEquals(ManagerController.DEFAULT_COMMON_SSH_USER,
				this.sshBenchmarkingPlugin.getSSHCommonUser(new Properties()));
	}	
	
	@Test
	public void testGetFCUsFromOutput() {
		long milliseconds = 1250;
		double powerCalculated = SSHBenchmarkingPlugin.MAGIC_NUMBER / 
				(double) TimeUnit.MILLISECONDS.toSeconds(milliseconds); 
		Assert.assertEquals(powerCalculated, this.sshBenchmarkingPlugin
				.getFCUsFromOutput(milliseconds), 0L);
		
		milliseconds = 5000;
		powerCalculated = SSHBenchmarkingPlugin.MAGIC_NUMBER / 
				(double) TimeUnit.MILLISECONDS.toSeconds(milliseconds); 
		Assert.assertEquals(powerCalculated, this.sshBenchmarkingPlugin
				.getFCUsFromOutput(milliseconds), 0L);		
	}
	
	@Test
	public void testGetPower() {
		String globalInstanceId = "globalInstanceId";
		double valueGlobalInstanceId = 10.0;
		this.sshBenchmarkingPlugin.getDataStore().addInstancePower(globalInstanceId,
				valueGlobalInstanceId);
		
		double power = this.sshBenchmarkingPlugin.getPower(globalInstanceId);
		Assert.assertEquals(valueGlobalInstanceId, power, 0L);
	}
	
	@Test
	public void testGetPowerDefault() {
		String globalInstanceId = "nothing";
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER, 
				this.sshBenchmarkingPlugin.getPower(globalInstanceId), 0L);
	}
	
	@Test
	public void testRemove() {
		SHHBenchmarkingDataStore dataStore = this.sshBenchmarkingPlugin.getDataStore();
		
		String globalInstanceIdOne = "globalInstanceIdOne";
		Double powerOne = 10.0;
		dataStore.addInstancePower(globalInstanceIdOne, powerOne);
		String globalInstanceIdTwo = "globalInstanceIdTwo";
		Double powerTwo = 12.0;
		dataStore.addInstancePower(globalInstanceIdTwo, powerTwo);		
		
		Assert.assertEquals(powerOne, dataStore.getPower(globalInstanceIdOne));
		Assert.assertEquals(powerTwo, dataStore.getPower(globalInstanceIdTwo));
		// action
		this.sshBenchmarkingPlugin.remove(globalInstanceIdOne);		
		// check
		Assert.assertNull(dataStore.getPower(globalInstanceIdOne));
		Assert.assertEquals(powerTwo, dataStore.getPower(globalInstanceIdTwo));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testRunInstanceNull() {
		sshBenchmarkingPlugin.run("", null);
	}
	
	@Test
	public void testRun() {
		this.sshBenchmarkingPlugin = Mockito.spy(new SSHBenchmarkingPlugin(properties));
		SHHBenchmarkingDataStore dataStore = this.sshBenchmarkingPlugin.getDataStore();
		String globalInstanceId = "globalInstanceId";
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(Instance.SSH_PUBLIC_ADDRESS_ATT, "localhost:9000");
		
		Assert.assertNull(dataStore.getPower(globalInstanceId));
		
		long milliseconds = 1;
		double power = 1;
		Mockito.when(this.sshBenchmarkingPlugin.sshBenchmarking(Mockito.anyString(), 
				Mockito.anyString())).thenReturn(milliseconds);
		Mockito.when(this.sshBenchmarkingPlugin.getFCUsFromOutput(milliseconds)).thenReturn(power);
		this.sshBenchmarkingPlugin.run(globalInstanceId, 
				new Instance("", null, attributes, null, null));		
		Assert.assertEquals(new Double(power), dataStore.getPower(globalInstanceId));
	}
	
	@Test
	public void testRunAndCalculatePower() throws IOException {
		// mock for ssh execution
		HashMap<String, String> attributes = new HashMap<String, String>();		
		attributes.put(Instance.SSH_PUBLIC_ADDRESS_ATT, "localhost:9000");
		SshHelper sshHelper = Mockito.mock(SshHelper.class);
		Mockito.doNothing().when(sshHelper).connect(Mockito.anyString(), Mockito.anyInt(),
				Mockito.anyString(), Mockito.anyString());
		Command command = Mockito.mock(Command.class);
		Mockito.when(command.getExitStatus()).thenReturn(0);
		Mockito.when(sshHelper.doSshExecution(Mockito.anyString())).thenReturn(command);
		sshBenchmarkingPlugin.setSshHelper(sshHelper);		
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		long startScriptTime = 1000L;
		long endStriptTime = 2000L;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(startScriptTime, endStriptTime);
		sshBenchmarkingPlugin.setDateUtils(dateUtils);
		
		String globalInstanceId = "globalInstanceId";
		SHHBenchmarkingDataStore dataStore = this.sshBenchmarkingPlugin.getDataStore();
		Assert.assertNull(dataStore.getPower(globalInstanceId));
		// action
		sshBenchmarkingPlugin.run(globalInstanceId, new Instance("id", null, attributes, null, null));
		
		//check
		Double powerCalculated = this.sshBenchmarkingPlugin.getFCUsFromOutput(endStriptTime - startScriptTime);
		Assert.assertEquals(powerCalculated, dataStore.getPower(globalInstanceId));
	}
	
	private void removeSSHDataStoraDefault() {
		TestSHHBenchmarkingDataStore.removeBD();
	}
	
}
