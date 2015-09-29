package org.fogbowcloud.manager.core.plugins.compute.opennebula;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.group.Group;
import org.opennebula.client.group.GroupPool;
import org.opennebula.client.image.ImagePool;
import org.opennebula.client.template.TemplatePool;
import org.opennebula.client.user.User;
import org.opennebula.client.user.UserPool;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;

public class OpenNebulaClientFactory {
	
	private final static Logger LOGGER = Logger.getLogger(OpenNebulaClientFactory.class);

	public Client createClient(String accessId, String openNebulaEndpoint) {
		try {
			return new Client(accessId, openNebulaEndpoint);
		} catch (ClientConfigurationException e) {
			LOGGER.error("Exception while creating oneClient.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
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

	public VirtualMachine createVirtualMachine(Client oneClient, String instanceIdStr) {
		int instanceId;
		try {
			instanceId = Integer.parseInt(instanceIdStr);
		} catch (Exception e) {
			LOGGER.error("Error while converting instanceid " + instanceIdStr + " to integer.");
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		VirtualMachine vm = new VirtualMachine(instanceId, oneClient);
		OneResponse response = vm.info();
		
		if (response.isError()){
			String errorMessage = response.getErrorMessage(); 
			LOGGER.error(errorMessage);
			//Not authorized to perform
			if (errorMessage.contains("Not authorized")){
				throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER);
			}
			//Error getting virtual machine
			throw new OCCIException(ErrorType.NOT_FOUND, errorMessage);
		} else if ("DONE".equals(vm.stateStr())){
			//The instance is not active anymore
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
		return vm;
	}

	public TemplatePool createTemplatePool(Client oneClient) {
		TemplatePool templatePool = new TemplatePool(oneClient);

		OneResponse response = templatePool.infoAll();
		if (response.isError()) {
			LOGGER.error("Error while getting info about templates: "
					+ response.getErrorMessage());
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		LOGGER.debug("Template pool length: " + templatePool.getLength());
		return templatePool;
	}
	
	public ImagePool createImagePool(Client oneClient) {
		ImagePool imagePool = new ImagePool(oneClient);

		OneResponse response = imagePool.infoAll();
		if (response.isError()) {
			LOGGER.error("Error while getting info about templates: "
					+ response.getErrorMessage());
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		LOGGER.debug("Template pool length: " + imagePool.getLength());
		return imagePool;
	}

	public String allocateVirtualMachine(Client oneClient, String vmTemplate) {
		OneResponse response = VirtualMachine.allocate(oneClient, vmTemplate);
		if (response.isError()) {
			String errorMessage = response.getErrorMessage();
			LOGGER.error("Error while instatiating an instance from template: " + vmTemplate);
			LOGGER.error("Error message is: " + errorMessage);

			if (errorMessage.contains("limit") && errorMessage.contains("quota")) {
				throw new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
			}
			
			if ((errorMessage.contains("Not enough free memory")) ||
					(errorMessage.contains("No space left on device"))) {
				throw new OCCIException(ErrorType.NO_VALID_HOST_FOUND,
						ResponseConstants.NO_VALID_HOST_FOUND);
			}

			throw new OCCIException(ErrorType.BAD_REQUEST, errorMessage);
		}

		return response.getMessage();
	}

	public User createUser(Client oneClient, String username) {
		UserPool userpool = new UserPool(oneClient);
		userpool.info();
		String userId = "";
		for (User user : userpool) {
			if (username.equals(user.getName())){
				userId = user.getId();
				break;
			}
		}
		if (userId.isEmpty()){
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		User user = userpool.getById(Integer.parseInt(userId));
		user.info();		
		return user;
	}
	
	public Group createGroup(Client oneClient, int groupId) {
		GroupPool groupPool = new GroupPool(oneClient);
		groupPool.info();
		Group group = groupPool.getById(groupId);
		if (group == null){
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		group.info();		
		return group;
	}
}
