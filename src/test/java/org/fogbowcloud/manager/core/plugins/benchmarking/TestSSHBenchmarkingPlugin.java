package org.fogbowcloud.manager.core.plugins.benchmarking;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.schmizz.sshj.connection.channel.direct.Session.Command;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.util.SshHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestSSHBenchmarkingPlugin {

	private SSHBenchmarkingPlugin sshBenchmarkingPlugin;
	
	@Before
	public void setUp() {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.SSH_COMMON_USER, "");
		sshBenchmarkingPlugin = new SSHBenchmarkingPlugin(properties );
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void testGetSHHCommonUser() {
		Properties properties = new Properties();
		String sshCommonUser = "ssh";
		properties.put(ConfigurationConstants.SSH_COMMON_USER, sshCommonUser);
		Assert.assertEquals(sshCommonUser, sshBenchmarkingPlugin.getSSHCommonUser(properties));
	}
	
	@SuppressWarnings("static-access")
	@Test
	public void testGetSHHCommonUserDefaultValue() {	 
		Assert.assertEquals(ManagerController.DEFAULT_COMMON_SSH_USER,
				sshBenchmarkingPlugin.getSSHCommonUser(new Properties()));
	}	
	
	@Test
	public void testGetFCUsFromOutput() {
		long milliseconds = 1250;
		Assert.assertEquals(10, sshBenchmarkingPlugin.getFCUsFromOutput(milliseconds), 0L);
		
		milliseconds = 5000;
		Assert.assertEquals(2, sshBenchmarkingPlugin.getFCUsFromOutput(milliseconds), 0L);		
	}
	
	@Test
	public void testGetPower() {
		String globalInstanceId = "globalInstanceId";
		Map<String, Double> instanceToPower = new HashMap<String, Double>();
		double valueGlobalInstanceId = 10.0;
		instanceToPower.put(globalInstanceId, valueGlobalInstanceId);
		sshBenchmarkingPlugin.setInstanceToPower(instanceToPower );
		Assert.assertEquals(valueGlobalInstanceId, sshBenchmarkingPlugin.getPower(globalInstanceId), 0L);
	}
	
	@Test
	public void testGetPowerDefault() {
		String globalInstanceId = "nothing";
		Assert.assertEquals(BenchmarkingPlugin.UNDEFINED_POWER, sshBenchmarkingPlugin.getPower(globalInstanceId), 0L);
	}
	
	@Test
	public void testRemove() {
		Map<String, Double> instanceToPower = new HashMap<String, Double>();
		String globalInstanceIdOne = "globalInstanceIdOne";
		instanceToPower.put(globalInstanceIdOne, 10.0);
		instanceToPower.put("globalInstanceIdTwo", 12.0);
		sshBenchmarkingPlugin.setInstanceToPower(instanceToPower);
		Assert.assertEquals(2, instanceToPower.size());
		sshBenchmarkingPlugin.remove(globalInstanceIdOne);
		Assert.assertEquals(1, instanceToPower.size());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testRunInstanceNull() {
		sshBenchmarkingPlugin.run("", null);
	}
	
	@Test
	public void testRunWithIpAndPort() {
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(Instance.SSH_PUBLIC_ADDRESS_ATT, "localhost:9000");
		Assert.assertEquals(0, sshBenchmarkingPlugin.getInstanceToPower().size());		
		sshBenchmarkingPlugin.run("", new Instance("id", null, attributes, null, null));		
		Assert.assertEquals(1, sshBenchmarkingPlugin.getInstanceToPower().size());
	}
	
	@Test
	public void testRun() throws IOException {
		HashMap<String, String> attributes = new HashMap<String, String>();
		attributes.put(Instance.SSH_PUBLIC_ADDRESS_ATT, "localhost:9000");
		SshHelper sshHelper = Mockito.mock(SshHelper.class);
		Mockito.doNothing().when(sshHelper).connect(Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyString());
		Command command = Mockito.mock(Command.class);
		Mockito.when(command.getExitStatus()).thenReturn(0);
		Mockito.when(sshHelper.doSshExecution(Mockito.anyString())).thenReturn(command, command);
		sshBenchmarkingPlugin.setSshHelper(sshHelper);
		
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(1000L, 2000L);
		sshBenchmarkingPlugin.setDateUtils(dateUtils);
		
		Assert.assertEquals(0, sshBenchmarkingPlugin.getInstanceToPower().size());		
		String globalInstanceId = "globalInstanceId";
		sshBenchmarkingPlugin.run(globalInstanceId, new Instance("id", null, attributes, null, null));
		
		Assert.assertEquals(1, sshBenchmarkingPlugin.getInstanceToPower().size());
		Assert.assertTrue(sshBenchmarkingPlugin.getInstanceToPower().get(globalInstanceId) == 10);
	}	
}
