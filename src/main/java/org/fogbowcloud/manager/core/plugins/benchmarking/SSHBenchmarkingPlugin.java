package org.fogbowcloud.manager.core.plugins.benchmarking;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import net.schmizz.sshj.connection.channel.direct.Session.Command;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.util.SshHelper;
import org.fogbowcloud.manager.occi.instance.Instance;

public class SSHBenchmarkingPlugin implements BenchmarkingPlugin {
	
	Map<String, Double> instanceToPower = new HashMap<String, Double>();

	private static final Logger LOGGER = Logger.getLogger(SSHBenchmarkingPlugin.class);
	private static final String SSH_BENCHMARKING_SCRIPT_URL = "ssh_benchmarking_script_url";
	
	private static final String STAGE_COMMAND = "curl %s -o exec;chmod +x exec;";
	private static final String EXEC_COMMAND = "./exec; rm ./exec";
	
	private String scriptUrl;
	private String managerPrivateKeyFilePath;
	private String sshCommonUser;
	
	public SSHBenchmarkingPlugin(Properties properties) {
		this.scriptUrl = properties.getProperty(
				SSH_BENCHMARKING_SCRIPT_URL);
		this.managerPrivateKeyFilePath = properties.getProperty(
				ConfigurationConstants.SSH_PRIVATE_KEY_PATH);
		this.sshCommonUser = getSSHCommonUser(properties);
	}
	
	private static String getSSHCommonUser(Properties properties) {
		String sshCommonUser = properties.getProperty(ConfigurationConstants.SSH_COMMON_USER);
		return sshCommonUser != null ? sshCommonUser : ManagerController.DEFAULT_COMMON_SSH_USER;
	}
	
	@Override
	public void run(String globalInstanceId, Instance instance) {
		if (instance == null) {
			throw new IllegalArgumentException("Instance must not be null.");
		}
		LOGGER.info("Running benchmarking on instance: " + globalInstanceId);

		double power = UNDEFINED_POWER;
		String ipAndPort = instance.getAttributes().get(Instance.SSH_PUBLIC_ADDRESS_ATT);
		if (ipAndPort != null) {
			
			long benchmarkingTime = sshBenchmarking(ipAndPort, sshCommonUser);
			if (benchmarkingTime > 0) {
				power = getFCUsFromOutput(benchmarkingTime);
			}
		}
		
		LOGGER.debug("Putting instanceId " + globalInstanceId + " and power " + power);
		instanceToPower.put(globalInstanceId, power);
	}
	
	private long sshBenchmarking(String ipAndPort, String user) {
		SshHelper ssh = new SshHelper();
		long millis = 0;
		try {
			String[] sshAddressRaw = ipAndPort.split(":");
			ssh.connect(sshAddressRaw[0], Integer.parseInt(sshAddressRaw[1]), user,
					this.managerPrivateKeyFilePath);
			Command stagingCmd = ssh.doSshExecution(String.format(STAGE_COMMAND, scriptUrl));
			if (stagingCmd.getExitStatus() == 0) {
				long startTimestamp = System.currentTimeMillis();
				Command executeBenchcmd = ssh.doSshExecution(EXEC_COMMAND);
				long endTimestamp = System.currentTimeMillis();
				if (executeBenchcmd.getExitStatus() == 0) {
					return endTimestamp - startTimestamp;
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Couldn't run benchmark.", e);
		}
		return millis;
	}
	
	/**
	 * TESTED!
	 * 
	 * Converts the time that the bench run into FCUs.
	 * 
	 * @param output
	 *            The output from benchmark in ssh.
	 * @return The amount of FCUs benchmarked.
	 */
	protected double getFCUsFromOutput(long milliseconds) {
		System.out.println(milliseconds + " milliseconds");

		double seconds = (double) TimeUnit.MILLISECONDS.toSeconds(milliseconds);
		return (10.0 / seconds);

	}

	@Override
	public double getPower(String globalInstanceId) {
		LOGGER.debug("Getting power of instance " + globalInstanceId);
		LOGGER.debug("Current instanceToPower=" + instanceToPower);
		if (instanceToPower.get(globalInstanceId) == null) {
			return UNDEFINED_POWER;
		}
		return instanceToPower.get(globalInstanceId);
	}

	@Override
	public void remove(String globalInstanceId) {
		LOGGER.debug("Removing instance: " + globalInstanceId + " from benchmarking map.");
		instanceToPower.remove(globalInstanceId);		
	}
}
