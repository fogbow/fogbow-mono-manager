package org.fogbowcloud.manager.core.plugins.opennebula;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.ssh.DefaultSSHTunnel;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.user.User;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;
import org.restlet.Request;
import org.restlet.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OpenNebulaComputePlugin implements ComputePlugin {

	private OpenNebulaClientFactory clientFactory;
	private String openNebulaEndpoint;
	private Map<String, String> fogbowTermToOpenNebula; 
	private String networkId;
	List<String> idleImages;
	
	private static final Logger LOGGER = Logger.getLogger(OpenNebulaComputePlugin.class);


	public OpenNebulaComputePlugin(Properties properties){
		this(properties, new OpenNebulaClientFactory());
	}
		
	public OpenNebulaComputePlugin(Properties properties, OpenNebulaClientFactory clientFactory) {
		this.openNebulaEndpoint = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_URL);
		this.clientFactory = clientFactory;
		fogbowTermToOpenNebula = new HashMap<String, String>();
		
		if (properties.get(OneConfigurationConstants.COMPUTE_ONE_NETWORK_KEY) == null){
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.NETWORK_NOT_SPECIFIED);			
		}		
		networkId = String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_NETWORK_KEY));	
		
		// images
		Map<String, String> imageProperties = getImageProperties(properties);
		
		if (imageProperties == null || imageProperties.isEmpty()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IMAGES_NOT_SPECIFIED);
		}
				
		for (String imageName : imageProperties.keySet()) {
			fogbowTermToOpenNebula.put(imageName, imageProperties.get(imageName));
			ResourceRepository.getInstance().addImageResource(imageName);
		}
		idleImages = new ArrayList<String>(); 
		idleImages.addAll(imageProperties.keySet());
		
		// flavors
		checkFlavor(String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_SMALL_KEY)));
		checkFlavor(String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_MEDIUM_KEY)));
		checkFlavor(String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_LARGE_KEY)));
		
		fogbowTermToOpenNebula.put(RequestConstants.SMALL_TERM,
				String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_SMALL_KEY)));
		fogbowTermToOpenNebula.put(RequestConstants.MEDIUM_TERM,
				String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_MEDIUM_KEY)));
		fogbowTermToOpenNebula.put(RequestConstants.LARGE_TERM,
				String.valueOf(properties.get(OneConfigurationConstants.COMPUTE_ONE_LARGE_KEY)));
		
		// userdata
		fogbowTermToOpenNebula.put(RequestConstants.USER_DATA_TERM, "user_data");
	}

	/*
	 * flavor format example: {mem=128, cpu=1}
	 */
	private void checkFlavor(String flavorValue) {		
		if (flavorValue == null || !flavorValue.startsWith("{") || !flavorValue.endsWith("}")
				|| getFlavorValueForAtt("mem", flavorValue) < 0 || getFlavorValueForAtt("cpu", flavorValue) < 0) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.INVALID_FLAVOR_SPECIFIED);
		}
	}

	private int getFlavorValueForAtt(String attName, String flavorValue) {
		try {
			String attAndValues = flavorValue.substring(1 , flavorValue.length() - 1);
			String[] attTokens = attAndValues.split(",");
			for (int i = 0; i < attTokens.length; i++) {
				String[] attAndAvalueTokens = attTokens[i].trim().split("=");
				String att = attAndAvalueTokens[0];
				if (attName.equals(att)){
					return Integer.parseInt(attAndAvalueTokens[1]);
				}
			}
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_FLAVOR_SPECIFIED);
		}
		return -1;
	}

	private static Map<String, String> getImageProperties(Properties properties) {
		Map<String, String> imageProperties = new HashMap<String, String>();

		for (Object propName : properties.keySet()) {
			String propNameStr = (String) propName;
			if (propNameStr.startsWith(OneConfigurationConstants.COMPUTE_ONE_IMAGE_PREFIX_KEY)) {
				imageProperties.put(
						propNameStr
								.substring(OneConfigurationConstants.COMPUTE_ONE_IMAGE_PREFIX_KEY
										.length()), properties.getProperty(propNameStr));
			}
		}
		LOGGER.debug("Image properties: " + imageProperties);
		return imageProperties;
	}
	
	@Override
	public String requestInstance(String accessId, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		
		LOGGER.debug("Requesting instance with accessId=" + accessId + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt);
		
		Map<String, String> templateProperties = new HashMap<String, String>();
		String choosenFlavor = null;
		String choosenImage = null;
		
		// checking categories are valid	
		for (Category category : categories) {
			if (fogbowTermToOpenNebula.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			} else if (category.getTerm().equals(RequestConstants.SMALL_TERM)
					|| category.getTerm().equals(RequestConstants.MEDIUM_TERM)
					|| category.getTerm().equals(RequestConstants.LARGE_TERM)) {				
				// There are more than one flavor category
				if (choosenFlavor != null) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.IRREGULAR_SYNTAX);					
				}
				choosenFlavor = fogbowTermToOpenNebula.get(category.getTerm());
			} else if (idleImages.contains(category.getTerm())){
				// There are more than one image category
				if (choosenImage != null) {
					throw new OCCIException(ErrorType.BAD_REQUEST,
							ResponseConstants.IRREGULAR_SYNTAX);					
				}
				choosenImage = fogbowTermToOpenNebula.get(category.getTerm());
			}
		}
		
		// image or flavor was not specified
		if (choosenFlavor == null || choosenImage == null){
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		templateProperties.put("mem", String.valueOf(getFlavorValueForAtt("mem", choosenFlavor)));
		templateProperties.put("cpu", String.valueOf(getFlavorValueForAtt("cpu", choosenFlavor)));
		templateProperties.put("userdata", xOCCIAtt.get(DefaultSSHTunnel.USER_DATA_ATT));
		templateProperties.put("image-id", choosenImage);

		Client oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
		String vmTemplate = generateTemplate(templateProperties);
		
		LOGGER.debug("The instance will be allocated according to template: " + vmTemplate);
		return clientFactory.allocateVirtualMachine(oneClient, vmTemplate);
	}
	
	private String generateTemplate(Map<String, String> templateProperties) {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();

			// template elements
			Document doc = docBuilder.newDocument();
			Element templateElement = doc.createElement("TEMPLATE");
			doc.appendChild(templateElement);

			// context elements
			Element contextElement = doc.createElement("CONTEXT");
			templateElement.appendChild(contextElement);

			// userdata
			Element userdataElement = doc.createElement("USERDATA");
			userdataElement.appendChild(doc.createTextNode(templateProperties.get("userdata")));
			contextElement.appendChild(userdataElement);

			// cpu
			Element cpuElement = doc.createElement("CPU");
			cpuElement.appendChild(doc.createTextNode(templateProperties.get("cpu")));
			templateElement.appendChild(cpuElement);

			// disk
			Element diskElement = doc.createElement("DISK");
			templateElement.appendChild(diskElement);

			// image
			Element imageElement = doc.createElement("IMAGE_ID");
			imageElement.appendChild(doc.createTextNode(templateProperties.get("image-id")));
			diskElement.appendChild(imageElement);

			// memory
			Element memoryElement = doc.createElement("MEMORY");
			memoryElement.appendChild(doc.createTextNode(templateProperties.get("mem")));
			templateElement.appendChild(memoryElement);

			// nic
			Element nicElement = doc.createElement("NIC");
			templateElement.appendChild(nicElement);

			// network
			Element networkElement = doc.createElement("NETWORK_ID");
			networkElement.appendChild(doc.createTextNode(networkId));
			nicElement.appendChild(networkElement);
			
			// getting xml template 
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			DOMSource source = new DOMSource(doc);
			
			StringWriter writer = new StringWriter();
			StreamResult result = new StreamResult(writer);
			transformer.transform(source, result);
			return writer.toString();
		} catch (ParserConfigurationException e) {
			LOGGER.error("", e);
		} catch (TransformerConfigurationException e) {
			LOGGER.error("", e);
		} catch (TransformerException e) {
			e.printStackTrace();
			LOGGER.error("", e);
		}
		return "";
	}

	@Override
	public List<Instance> getInstances(String accessId) {
		List<Instance> instances = new ArrayList<Instance>();
		Client oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
		VirtualMachinePool vmPool = clientFactory.createVirtualMachinePool(oneClient);
		for (VirtualMachine virtualMachine : vmPool) {
			instances.add(mountInstance(virtualMachine));
		}
		return instances;
	}

	@Override
	public Instance getInstance(String accessId, String instanceId) {
		Client oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
		VirtualMachine vm = clientFactory.createVirtualMachine(oneClient, instanceId);
		return mountInstance(vm);
	}

	//FIXME Mount instance fake
	private Instance mountInstance(VirtualMachine vm) {
		// TODO Auto-generated method stub		
		return new Instance(vm.getId());
	}

	@Override
	public void removeInstance(String accessId, String instanceId) {
		LOGGER.debug("Removing instanceId " + instanceId + " with accessId " + accessId);
		Client oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
		VirtualMachine vm = clientFactory.createVirtualMachine(oneClient, instanceId);
		OneResponse response = vm.delete();
		if (response.isError()) {			
			LOGGER.error("Error while removing vm: " + response.getErrorMessage());
		}
	}

	@Override
	public void removeInstances(String accessId) {
		Client oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);
		VirtualMachinePool vmPool = clientFactory.createVirtualMachinePool(oneClient);
		for (VirtualMachine virtualMachine : vmPool) {
			OneResponse response = virtualMachine.delete();
			if (response.isError()) {
				LOGGER.error("Error while removing vm: " + response.getErrorMessage());
			}
		}
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);				
		User user = clientFactory.createUser(oneClient);

		String maxCpuStr = user.xpath("VM_QUOTA/VM/CPU");
		String cpuInUseStr = user.xpath("VM_QUOTA/VM/CPU_USED");
		String maxMemStr = user.xpath("VM_QUOTA/VM/MEMORY");
		String memInUseStr = user.xpath("VM_QUOTA/VM/MEMORY_USED");
		
		// default values is used when quota is not specified
		int maxCpu = 100;
		int cpuInUse = 0;
		int maxMem = 20480; //20Gb
		int memInUse = 0;
		
		// getting quota values
		if (isValidInt(maxCpuStr)) {
			maxCpu = Integer.parseInt(maxCpuStr);
		}		
		if (isValidInt(cpuInUseStr)) {
			cpuInUse = Integer.parseInt(cpuInUseStr);
		}		
		if (isValidInt(maxMemStr)) {
			maxMem = Integer.parseInt(maxMemStr);
		}		
		if (isValidInt(memInUseStr)) {
			memInUse = Integer.parseInt(memInUseStr);
		}
		
		int cpuIdle = maxCpu - cpuInUse;
		int memIdle = maxMem - memInUse;
	
		return new ResourcesInfo(String.valueOf(cpuIdle), String.valueOf(cpuInUse),
				String.valueOf(memIdle), String.valueOf(memInUse), getFlavors(cpuIdle, memIdle),
				null);
	}
	
	private boolean isValidInt(String integer) {
		try {
			Integer.parseInt(integer);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private List<Flavor> getFlavors(int cpuIdle, int memIdle) {
		List<Flavor> flavors = new ArrayList<Flavor>();
		// small		
		int memFlavor = getFlavorValueForAtt("mem", fogbowTermToOpenNebula.get(RequestConstants.SMALL_TERM));
		int cpuFlavor = getFlavorValueForAtt("cpu", fogbowTermToOpenNebula.get(RequestConstants.SMALL_TERM));		
		int capacity = Math.min(cpuIdle / cpuFlavor, memIdle / memFlavor);
		Flavor smallFlavor = new Flavor(RequestConstants.SMALL_TERM, String.valueOf(cpuFlavor),
				String.valueOf(memFlavor), capacity);
		// medium
		memFlavor = getFlavorValueForAtt("mem", fogbowTermToOpenNebula.get(RequestConstants.MEDIUM_TERM));
		cpuFlavor = getFlavorValueForAtt("cpu", fogbowTermToOpenNebula.get(RequestConstants.MEDIUM_TERM));
		capacity = Math.min(cpuIdle / cpuFlavor, memIdle / memFlavor);
		Flavor mediumFlavor = new Flavor(RequestConstants.MEDIUM_TERM, String.valueOf(cpuFlavor),
				String.valueOf(memFlavor), capacity);
		// large
		memFlavor = getFlavorValueForAtt("mem", fogbowTermToOpenNebula.get(RequestConstants.LARGE_TERM));
		cpuFlavor = getFlavorValueForAtt("cpu", fogbowTermToOpenNebula.get(RequestConstants.LARGE_TERM));
		capacity = Math.min(cpuIdle / cpuFlavor, memIdle / memFlavor);
		Flavor largeFlavor = new Flavor(RequestConstants.LARGE_TERM, String.valueOf(cpuFlavor),
				String.valueOf(memFlavor), capacity);
		flavors.add(smallFlavor);
		flavors.add(mediumFlavor);
		flavors.add(largeFlavor);
		return flavors;
	}

	@Override
	public void bypass(Request request, Response response) {
		throw new OCCIException(ErrorType.BAD_REQUEST,
				ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
	}

}
