package org.fogbowcloud.manager.core.plugins.opennebula;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.template.TemplatePool;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;

public class OpenNebulaClientFactory {
	
	private final static Logger LOGGER = Logger.getLogger(OpenNebulaClientFactory.class);

	public Client createClient(String accessId, String openNebulaEndpoint)
			throws ClientConfigurationException {
		return new Client(accessId, openNebulaEndpoint);
	}

	public VirtualMachinePool createVirtualMachinePool(Client oneClient) {
		VirtualMachinePool vmPool = new VirtualMachinePool(oneClient);
		OneResponse response = vmPool.info();

		if (response.isError()) {
			LOGGER.error(response.getErrorMessage());
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		return vmPool;
	}

	public VirtualMachine createVirtualMachine(Client oneClient, String instanceId) {
		VirtualMachine vm = new VirtualMachine(Integer.parseInt(instanceId), oneClient);
		OneResponse response = vm.info();
		
		if (response.isError()){
			LOGGER.error(response.getErrorMessage());
			throw new OCCIException(ErrorType.NOT_FOUND, response.getErrorMessage());
		}
		return vm;
	}

	public TemplatePool createTemplatePool(Client oneClient) {
		TemplatePool templatePool = new TemplatePool(oneClient);

		OneResponse response = templatePool.info();
		if (response.isError()) {
			LOGGER.error("Error while getting info about templates: "
					+ response.getErrorMessage());
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		LOGGER.debug("Template pool length: " + templatePool.getLength());
		return templatePool;
	}
}
