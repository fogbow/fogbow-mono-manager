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
	private static final String SSH_BENCHMARKING_SCRIPT_PATH = "ssh_benchmarking_script_path";
	
	private String scriptUrl;
	private String managerPrivateKeyFilePath;
	
	public SSHBenchmarkingPlugin(Properties properties) {
		this.scriptUrl = properties
				.getProperty(SSH_BENCHMARKING_SCRIPT_PATH);
		this.managerPrivateKeyFilePath = properties.getProperty(ConfigurationConstants.SSH_PRIVATE_KEY_PATH);
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
			String[] sshAddressData = ipAndPort.split(":");
			long startTimeInMillis = System.currentTimeMillis();
			Command output = sshBenchmarking(sshAddressData[0], sshAddressData[1], 
					ManagerController.MANAGER_BENCHMARKING_SSH_USER);
			long endTimeInMillis = System.currentTimeMillis();
			if (output != null && output.getExitStatus() == 0) {
				power = getFCUsFromOutput(endTimeInMillis - startTimeInMillis);
			}
		}
		
		LOGGER.debug("Putting instanceId " + globalInstanceId + " and power " + power);
		instanceToPower.put(globalInstanceId, power);
	}
	
	private Command sshBenchmarking(String ip, String port, String user) {
		SshHelper ssh = new SshHelper();
		try {
			ssh.connect(ip, Integer.parseInt(port), user,
					this.managerPrivateKeyFilePath);
			String shellCmd = "curl " + this.scriptUrl
					+ " -o script.sh -s && " + "chmod +x /home/" + user
					+ "/script.sh &&" + "bash /home/" + user + "/script.sh &&"
					+ "rm /home/" + user + "/script.sh && rm /home/" + user + "/bench";
			return ssh.doSshExecution(shellCmd);
		} catch (Exception e) {
		}
		return null;
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
