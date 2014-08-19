package org.fogbowcloud.manager.core.plugins.opennebula;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.template.Template;
import org.opennebula.client.template.TemplatePool;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;
import org.restlet.Request;
import org.restlet.Response;

public class OpenNebulaComputePlugin implements ComputePlugin {

	private OpenNebulaClientFactory clientFactory;
	private String openNebulaEndpoint;
	private Map<String, String> fogbowTermToOpenNebula; 
	
	private static final Logger LOGGER = Logger.getLogger(OpenNebulaComputePlugin.class);


	public OpenNebulaComputePlugin(Properties properties){
		this(properties, new OpenNebulaClientFactory());
	}
		
	public OpenNebulaComputePlugin(Properties properties, OpenNebulaClientFactory clientFactory) {
		this.openNebulaEndpoint = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_URL);
		this.clientFactory = clientFactory;
		fogbowTermToOpenNebula = new HashMap<String, String>();
		
		// templates
		fogbowTermToOpenNebula.put(RequestConstants.SMALL_TERM,
				String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_SMALL_TPL)));
		fogbowTermToOpenNebula.put(RequestConstants.MEDIUM_TERM,
				String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_MEDIUM_TPL)));
		fogbowTermToOpenNebula.put(RequestConstants.LARGE_TERM,
				String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_LARGE_TPL)));
		
		// The images are already specified at templates
	}

	@Override
	public String requestInstance(String accessId, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String tplId = null;
		// checking if all categories are valid ones	
		for (Category category : categories) {
			if (fogbowTermToOpenNebula.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			} else if (RequestConstants.SMALL_TERM.equals(category.getTerm())
					|| RequestConstants.MEDIUM_TERM.equals(category.getTerm())
					|| RequestConstants.LARGE_TERM.equals(category.getTerm())) {
				tplId = fogbowTermToOpenNebula.get(category.getTerm()); 
			}
		}
		
		Client oneClient;
		try {
			oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
			TemplatePool templatePool = clientFactory.createTemplatePool(oneClient); 

			// TODO how to send userdata ?
			
			// Instantiating VM
			Template tempĺate = templatePool.getById(Integer.parseInt(tplId));
			OneResponse response = tempĺate.instantiate();
					
			if (response.isError()) {
				LOGGER.error("Error while instatiating an instance from template: " + tplId);
				throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
			}

			return response.getMessage();
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}
	

	@Override
	public List<Instance> getInstances(String accessId) {
		List<Instance> instances = new ArrayList<Instance>();
		Client oneClient;
		try {
			oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
			VirtualMachinePool vmPool = clientFactory.createVirtualMachinePool(oneClient);
			vmPool.info();
			System.out.println("vmpool == null" + (vmPool == null));
			System.out.println(vmPool.getLength());
			for (VirtualMachine virtualMachine : vmPool) {
				System.out.println("DENTRO");
				instances.add(mountInstance(virtualMachine));
			}
			return instances;
		} catch (ClientConfigurationException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public Instance getInstance(String accessId, String instanceId) {
		try {
			Client oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
			VirtualMachine vm = clientFactory.createVirtualMachine(oneClient, instanceId);
			return mountInstance(vm);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}		
	}

	//FIXME Mount instance fake
	private Instance mountInstance(VirtualMachine vm) {
		// TODO Auto-generated method stub		
		return new Instance(vm.getId());
	}

	@Override
	public void removeInstance(String accessId, String instanceId) {
		LOGGER.debug("Removing instanceId " + instanceId + " with accessId " + accessId);
		Client oneClient;
		try {
			oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
			VirtualMachine vm = clientFactory.createVirtualMachine(oneClient, instanceId);
			OneResponse response = vm.delete();
			if (response.isError()){
				LOGGER.error("Error while removing vm: " + response.getErrorMessage());
			}	
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public void removeInstances(String accessId) {
		Client oneClient;
		try {
			oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
			VirtualMachinePool vmPool = clientFactory.createVirtualMachinePool(oneClient);
			for (VirtualMachine virtualMachine : vmPool) {
				OneResponse response = virtualMachine.delete();
				if (response.isError()){
					LOGGER.error("Error while removing vm: " + response.getErrorMessage());
				}
			}
		} catch (ClientConfigurationException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void bypass(Request request, Response response) {
		throw new OCCIException(ErrorType.BAD_REQUEST,
				ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
	}

}
