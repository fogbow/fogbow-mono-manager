package org.fogbowcloud.manager.core.plugins.opennebula;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

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

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.json.JSONObject;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.group.Group;
import org.opennebula.client.image.Image;
import org.opennebula.client.image.ImagePool;
import org.opennebula.client.user.User;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OpenNebulaComputePlugin implements ComputePlugin {

	public static final int VALUE_DEFAULT_QUOTA_OPENNEBULA = -1;
	public static final int VALUE_UNLIMITED_QUOTA_OPENNEBULA = -2;
	
	public static final int DEFAULT_RESOURCE_MAX_VALUE = Integer.MAX_VALUE;
	private OpenNebulaClientFactory clientFactory;
	private String openNebulaEndpoint;
	private Map<String, String> fogbowTermToOpenNebula; 
	private String networkId;
	
	private String sshHost;
	private Integer sshPort;
	private String sshUsername;
	private String sshKeyFile;
	private String sshTargetTempFolder;
	private Integer dataStoreId;
	
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
		
		sshHost = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_SSH_HOST);
		String sshPortStr = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_SSH_PORT);
		sshPort = sshPortStr == null ? null : Integer.valueOf(sshPortStr);
		
		sshUsername = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_SSH_USERNAME);
		sshKeyFile = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_SSH_KEY_FILE);
		sshTargetTempFolder = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_SSH_TARGET_TEMP_FOLDER);
		
		String dataStoreIdStr = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_DATASTORE_ID);
		dataStoreId = dataStoreIdStr == null ? null: Integer.valueOf(dataStoreIdStr);
		
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
		
		//ssh public key
		fogbowTermToOpenNebula.put(RequestConstants.PUBLIC_KEY_TERM, "ssh-public-key");
	}

	/*
	 * flavor format example: {mem=128, cpu=1}
	 */
	private void checkFlavor(String flavorValue) {		
		if (getAttValue("mem", flavorValue) < 0 || getAttValue("cpu", flavorValue) < 0) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.INVALID_FLAVOR_SPECIFIED);
		}
	}

	private double getAttValue(String attName, String flavorSpec) {
		JSONObject root;
		try {
			root = new JSONObject(flavorSpec);
			String attValue = root.getString(attName);
			return Double.parseDouble(attValue);
		} catch (Exception e) {
			LOGGER.error("", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_FLAVOR_SPECIFIED);
		}
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String localImageId) {
		
		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt + "; localImageId=" + localImageId);
		
		Map<String, String> templateProperties = new HashMap<String, String>();
		String choosenFlavor = null;
		
		// removing fogbow-request category
		categories.remove(new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				RequestConstants.KIND_CLASS));
		
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
			} else if (category.getTerm().equals(RequestConstants.PUBLIC_KEY_TERM)) {
				templateProperties.put("ssh-public-key",
						xOCCIAtt.get(RequestAttribute.DATA_PUBLIC_KEY.getValue()));
			}
		}
		
		// image or flavor was not specified
		if (choosenFlavor == null || localImageId == null){
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		String userdata = xOCCIAtt.get(RequestAttribute.USER_DATA_ATT.getValue());
		if (userdata != null){
			userdata = normalizeUserdata(userdata);
		}
		templateProperties.put("mem", String.valueOf((int)getAttValue("mem", choosenFlavor)));
		templateProperties.put("cpu", String.valueOf(getAttValue("cpu", choosenFlavor)));
		templateProperties.put("userdata", userdata);
		templateProperties.put("image-id", localImageId);

		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		String vmTemplate = generateTemplate(templateProperties);

		LOGGER.debug("The instance will be allocated according to template: " + vmTemplate);
		return clientFactory.allocateVirtualMachine(oneClient, vmTemplate);
	}

	public static String normalizeUserdata(String userdata) {
		userdata = new String(Base64.decodeBase64(userdata), Charsets.UTF_8);
		userdata = userdata.replaceAll("\n", "\\\\n");
		userdata = new String(Base64.encodeBase64(userdata.getBytes(Charsets.UTF_8), false, false),
				Charsets.UTF_8);
		return userdata;
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
			// ssh public key
			if (templateProperties.get("ssh-public-key") != null) {
				Element sshPublicKeyElement = doc.createElement("SSH_PUBLIC_KEY");
				sshPublicKeyElement.appendChild(doc.createTextNode(templateProperties
						.get("ssh-public-key")));
				contextElement.appendChild(sshPublicKeyElement);
			}
			// userdata
			String userdata = templateProperties.get("userdata");
			if (userdata != null) {
				Element userdataEncodingEl = doc.createElement("USERDATA_ENCODING");
				userdataEncodingEl.appendChild(doc.createTextNode("base64"));
				contextElement.appendChild(userdataEncodingEl);
				Element userdataElement = doc.createElement("USERDATA");
				userdataElement.appendChild(doc.createTextNode(userdata));
				contextElement.appendChild(userdataElement);
			}
			// cpu
			Element cpuElement = doc.createElement("CPU");
			cpuElement.appendChild(doc.createTextNode(templateProperties.get("cpu")));
			templateElement.appendChild(cpuElement);
			//graphics
			Element graphicsElement = doc.createElement("GRAPHICS");
			Element listenElement = doc.createElement("LISTEN");
			listenElement.appendChild(doc.createTextNode("0.0.0.0"));
			Element typeElement = doc.createElement("TYPE");
			typeElement.appendChild(doc.createTextNode("vnc"));
			graphicsElement.appendChild(listenElement);
			graphicsElement.appendChild(typeElement);
			templateElement.appendChild(graphicsElement);
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
	public List<Instance> getInstances(Token token) {
		LOGGER.debug("Getting instances of token: " + token);

		List<Instance> instances = new ArrayList<Instance>();
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		VirtualMachinePool vmPool = clientFactory.createVirtualMachinePool(oneClient);
		for (VirtualMachine virtualMachine : vmPool) {
			instances.add(mountInstance(virtualMachine));
		}
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.debug("Getting instance " + instanceId + " of token: " + token);

		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		VirtualMachine vm = clientFactory.createVirtualMachine(oneClient, instanceId);
		return mountInstance(vm);
	}

	private Instance mountInstance(VirtualMachine vm) {
		LOGGER.debug("Mounting instance structure of instanceId: " + vm.getId());

		String mem = vm.xpath("TEMPLATE/MEMORY");
		String cpu = vm.xpath("TEMPLATE/CPU");
		String image = vm.xpath("TEMPLATE/DISK/IMAGE");
		String arch = vm.xpath("TEMPLATE/OS/ARCH");
		
		LOGGER.debug("mem=" + mem + ", cpu=" + cpu + ", image=" + image + ", arch=" + arch);

		// TODO To get information about network when it'll be necessary
		// vm.xpath("TEMPLATE/NIC/NETWORK");
		// vm.xpath("TEMPLATE/NIC/NETWORK_ID");

		Map<String, String> attributes = new HashMap<String, String>();
		// CPU Architecture of the instance
		attributes.put("occi.compute.architecture", getArch(arch));
		attributes.put("occi.compute.state", getOCCIState(vm.lcmStateStr()));
		// CPU Clock frequency (speed) in gigahertz
		attributes.put("occi.compute.speed", "Not defined");
		attributes.put("occi.compute.memory", String.valueOf(Double.parseDouble(mem) / 1024)); // Gb
		attributes.put("occi.compute.cores", cpu);
		attributes.put("occi.compute.hostname", vm.getName());
		attributes.put("occi.core.id", vm.getId());

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.getInstance().get(
				getUsedFlavor(Double.parseDouble(cpu), Double.parseDouble(mem))));
		
		return new Instance(vm.getId(), resources, attributes, new ArrayList<Link>());
	}

	private String getArch(String arch) {		
		// x86 is default
		return !arch.isEmpty() ? arch : "x86";
	}

	private String getOCCIState(String oneVMState) {
		if ("Running".equalsIgnoreCase(oneVMState)) {
			return "active";
		} else if ("Suspended".equalsIgnoreCase(oneVMState)){
			return "suspended";
		}
		return "inactive";
	}

	private String getUsedFlavor(double cpu, double mem) {
		double flavorMem = getAttValue("mem",
				fogbowTermToOpenNebula.get(RequestConstants.SMALL_TERM));
		double flavorCpu = getAttValue("cpu",
				fogbowTermToOpenNebula.get(RequestConstants.SMALL_TERM));		
		if ((Math.abs(cpu - flavorCpu) < 0.1) && (Math.abs(mem - flavorMem) < 0.1)) {
			return RequestConstants.SMALL_TERM;
		}		
		flavorMem = getAttValue("mem",
				fogbowTermToOpenNebula.get(RequestConstants.MEDIUM_TERM));
		flavorCpu = getAttValue("cpu",
				fogbowTermToOpenNebula.get(RequestConstants.MEDIUM_TERM));
		if ((Math.abs(cpu - flavorCpu) < 0.1) && (Math.abs(mem - flavorMem) < 0.1)) {
			return RequestConstants.MEDIUM_TERM;
		}		
		return RequestConstants.LARGE_TERM;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		LOGGER.debug("Removing instanceId " + instanceId + " with token " + token);
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		VirtualMachine vm = clientFactory.createVirtualMachine(oneClient, instanceId);
		OneResponse response = vm.delete();
		if (response.isError()) {			
			LOGGER.error("Error while removing vm: " + response.getErrorMessage());
		}
	}

	@Override
	public void removeInstances(Token token) {
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
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
		User user = clientFactory.createUser(oneClient, token.getUser());
		
		String maxUserCpuStr = user.xpath("VM_QUOTA/VM/CPU");
		String cpuUserInUseStr = user.xpath("VM_QUOTA/VM/CPU_USED");
		String maxUserMemStr = user.xpath("VM_QUOTA/VM/MEMORY");
		String memUserInUseStr = user.xpath("VM_QUOTA/VM/MEMORY_USED");
		String maxUserVMsStr = user.xpath("VM_QUOTA/VM/VMS");
		String vmsUserInUseStr = user.xpath("VM_QUOTA/VM/VMS_USED");
		
		String groupId = user.xpath("GROUPS/ID");
		Group group = clientFactory.createGroup(oneClient, Integer.parseInt(groupId));

		String maxGroupCpuStr = group.xpath("VM_QUOTA/VM/CPU");
		String cpuGroupInUseStr = group.xpath("VM_QUOTA/VM/CPU_USED");
		String maxGroupMemStr = group.xpath("VM_QUOTA/VM/MEMORY");
		String memGroupInUseStr = group.xpath("VM_QUOTA/VM/MEMORY_USED");
		String maxGroupVMsStr = group.xpath("VM_QUOTA/VM/VMS");
		String vmsGroupInUseStr = group.xpath("VM_QUOTA/VM/VMS_USED");
		
		ResourceQuota resourceQuota = getQuota(maxUserCpuStr, cpuUserInUseStr, maxGroupCpuStr, cpuGroupInUseStr);
		double maxCpu = resourceQuota.getMax();
		double cpuInUse = resourceQuota.getInUse();
	
		resourceQuota = getQuota(maxUserMemStr, memUserInUseStr, maxGroupMemStr, memGroupInUseStr);
		double maxMem = resourceQuota.getMax();
		double memInUse = resourceQuota.getInUse();
		
		resourceQuota = getQuota(maxUserVMsStr, vmsUserInUseStr, maxGroupVMsStr, vmsGroupInUseStr);
		double maxVMs = resourceQuota.getMax();
		double vmsInUse = resourceQuota.getInUse();

		double cpuIdle = maxCpu - cpuInUse;
		double memIdle = maxMem - memInUse;
		double instancesIdle = maxVMs - vmsInUse;
	
		return new ResourcesInfo(String.valueOf(cpuIdle), String.valueOf(cpuInUse),
				String.valueOf(memIdle), String.valueOf(memInUse), getFlavors(cpuIdle, memIdle, instancesIdle),
				null);
	}
	
	private ResourceQuota getQuota(String maxUserResource, String resourceUserInUse, String maxGroupResource, String resourceGroupInUse) {
		if (isValidNumber(maxUserResource) && isValidNumber(maxGroupResource)) {	
			if (isUnlimitedOrDefaultQuota(maxUserResource)){
				maxUserResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			}
			
			if (isUnlimitedOrDefaultQuota(maxGroupResource)){
				maxGroupResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			}
			
			if (isUserSmallerQuota(maxUserResource, maxGroupResource)) {
				return new ResourceQuota(maxUserResource, resourceUserInUse);
			} else {
				return new ResourceQuota(maxGroupResource, resourceGroupInUse);
			}
		} else if (isValidNumber(maxUserResource)) {
			if (isUnlimitedOrDefaultQuota(maxUserResource)){
				maxUserResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			}
			return new ResourceQuota(maxUserResource, resourceUserInUse);
		} else if (isValidNumber(maxGroupResource)) {
			if (isUnlimitedOrDefaultQuota(maxGroupResource)){
				maxGroupResource = String.valueOf(DEFAULT_RESOURCE_MAX_VALUE);
			}
			return new ResourceQuota(maxGroupResource, resourceGroupInUse);
		} else {
			return new ResourceQuota(String.valueOf(DEFAULT_RESOURCE_MAX_VALUE),
					String.valueOf(getBiggerValue(resourceUserInUse, resourceGroupInUse)));
		}		
	}

	private boolean isUnlimitedOrDefaultQuota(String maxResourceStr) {
		int maxResource = Integer.parseInt(maxResourceStr);
		return maxResource == VALUE_DEFAULT_QUOTA_OPENNEBULA
				|| maxResource == VALUE_UNLIMITED_QUOTA_OPENNEBULA;
	}

	private int getBiggerValue(String resourceUserInUse, String resourceGroupInUse) {
		return Math.max(Integer.parseInt(resourceUserInUse), Integer.parseInt(resourceGroupInUse));
	}

	private boolean isUserSmallerQuota(String maxUserStr, String maxGroupStr) {		
		return Integer.parseInt(maxUserStr) < Integer.parseInt(maxGroupStr) ? true : false;
		
	}

	private boolean isValidNumber(String number) {
		try {
			Double.parseDouble(number);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private List<Flavor> getFlavors(double cpuIdle, double memIdle, double instancesIdle) {
		List<Flavor> flavors = new ArrayList<Flavor>();
		// small		
		double memFlavor = getAttValue("mem", fogbowTermToOpenNebula.get(RequestConstants.SMALL_TERM));
		double cpuFlavor = getAttValue("cpu", fogbowTermToOpenNebula.get(RequestConstants.SMALL_TERM));
		int capacity = Math.min((int) Math.min(cpuIdle / cpuFlavor, memIdle / memFlavor), (int) instancesIdle);
		Flavor smallFlavor = new Flavor(RequestConstants.SMALL_TERM, String.valueOf(cpuFlavor),
				String.valueOf(memFlavor), capacity);
		// medium
		memFlavor = getAttValue("mem", fogbowTermToOpenNebula.get(RequestConstants.MEDIUM_TERM));
		cpuFlavor = getAttValue("cpu", fogbowTermToOpenNebula.get(RequestConstants.MEDIUM_TERM));
		capacity = Math.min((int) Math.min(cpuIdle / cpuFlavor, memIdle / memFlavor), (int) instancesIdle);
		Flavor mediumFlavor = new Flavor(RequestConstants.MEDIUM_TERM, String.valueOf(cpuFlavor),
				String.valueOf(memFlavor), capacity);
		// large
		memFlavor = getAttValue("mem", fogbowTermToOpenNebula.get(RequestConstants.LARGE_TERM));
		cpuFlavor = getAttValue("cpu", fogbowTermToOpenNebula.get(RequestConstants.LARGE_TERM));
		capacity = Math.min((int) Math.min(cpuIdle / cpuFlavor, memIdle / memFlavor), (int) instancesIdle);
		Flavor largeFlavor = new Flavor(RequestConstants.LARGE_TERM, String.valueOf(cpuFlavor),
				String.valueOf(memFlavor), capacity);
		flavors.add(smallFlavor);
		flavors.add(mediumFlavor);
		flavors.add(largeFlavor);
		return flavors;
	}

	@Override
	public void bypass(Request request, Response response) {
		response.setStatus(new Status(HttpStatus.SC_BAD_REQUEST),
				ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName) {
		
		LOGGER.info("Uploading image... ");
		LOGGER.info("Token=" + token.getAccessId() + "; imagePath=" + imagePath + "; imageName="
				+ imageName);
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		String remoteFilePath = sshTargetTempFolder + "/" + UUID.randomUUID();
		
		LOGGER.info("Remote File path = " + remoteFilePath);
		
		OpenNebulaSshClientWrapper sshClientWrapper = new OpenNebulaSshClientWrapper();
		try {
			sshClientWrapper.connect(sshHost, sshPort, sshUsername, sshKeyFile);
			sshClientWrapper.doScpUpload(imagePath, remoteFilePath);
		} catch (IOException e) {
			LOGGER.error("Error whilen SCP.", e);
			throw new RuntimeException(e);
		} finally {
			try {
				sshClientWrapper.disconnect();
			} catch (IOException e) {
				LOGGER.error("Error whilen disconnecting SCP client.", e);
			}
		}
		
		Map<String, String> templateProperties = new HashMap<String, String>();
		templateProperties.put("image_name", imageName);
		templateProperties.put("image_path", remoteFilePath);
		Long imageSize = (long) Math.ceil(((double) new File(imagePath).length()) / (1024d * 1024d));
		templateProperties.put("image_size", imageSize.toString());
		
		LOGGER.info("Template properties = " + templateProperties);
		OneResponse response = Image.allocate(oneClient, generateImageTemplate(templateProperties), dataStoreId);
		
		if (response.isError()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		
		Image.chmod(oneClient, response.getIntMessage(), 744);
	}
	
	private String generateImageTemplate(Map<String, String> templateProperties) {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("IMAGE");
			doc.appendChild(rootElement);
			
			Element nameElement = doc.createElement("NAME");
			nameElement.appendChild(doc.createTextNode(templateProperties.get("image_name")));
			rootElement.appendChild(nameElement);
			
			Element pathElement = doc.createElement("PATH");
			pathElement.appendChild(doc.createTextNode(templateProperties.get("image_path")));
			rootElement.appendChild(pathElement);
			
			Element sizeElement = doc.createElement("SIZE");
			sizeElement.appendChild(doc.createTextNode(templateProperties.get("image_size")));
			rootElement.appendChild(sizeElement);
			
			Element driverElement = doc.createElement("DRIVER");
			driverElement.appendChild(doc.createTextNode("qcow2"));
			rootElement.appendChild(driverElement);
			
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			
			DOMSource source = new DOMSource(doc);
			StringWriter stringWriter = new StringWriter();
			StreamResult result = new StreamResult(stringWriter);
			
			transformer.transform(source, result);
			
			return stringWriter.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getImageId(Token token, String imageName) {
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		ImagePool imagePool = new ImagePool(oneClient); 
		OneResponse response = imagePool.info();
		
		if (response.isError()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		
		for (Image image : imagePool) {
			if (image.getName().equals(imageName)) {
				return image.getId();
			}
		}
		return null;
	}
}

class ResourceQuota {
	
	double maxResource;
	double resourceInUse;
	
	public ResourceQuota(String maxResource, String resourceInUse) {
		this.maxResource = Double.parseDouble(maxResource);
		this.resourceInUse = Double.parseDouble(resourceInUse);
	}

	public double getInUse() {	
		return resourceInUse;
	}

	public double getMax() {
		return maxResource;
	}
}
