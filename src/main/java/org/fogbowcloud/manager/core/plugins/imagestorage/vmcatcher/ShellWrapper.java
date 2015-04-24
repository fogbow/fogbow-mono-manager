package org.fogbowcloud.manager.core.plugins.imagestorage.vmcatcher;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

public class ShellWrapper {
	
	private static final Logger LOGGER = Logger.getLogger(ShellWrapper.class);

	public void execute(String... command) throws IOException,
			InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(command);
		Process process = builder.start();

		int exitValue = process.waitFor();
		String stdout = IOUtils.toString(process.getInputStream());
		String stderr = IOUtils.toString(process.getErrorStream());

		LOGGER.debug("Command " + Arrays.asList(command) + " has finished. "
				+ "Exit: " + exitValue + "; Stdout: " + stdout + "; Stderr: "
				+ stderr);
	}

}
