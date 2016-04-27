package org.fogbowcloud.manager.core.plugins.compute.opennebula;

import java.io.ByteArrayInputStream;
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

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.storage.StorageAttribute;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.group.Group;
import org.opennebula.client.image.Image;
import org.opennebula.client.image.ImagePool;
import org.opennebula.client.template.Template;
import org.opennebula.client.template.TemplatePool;
import org.opennebula.client.user.User;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OpenNebulaComputePlugin implements ComputePlugin {

	public static final String OPENNEBULA_TEMPLATES = "compute_one_templates";
	public static final String OPENNEBULA_TEMPLATES_TYPE_ALL = "all";	
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
	private List<Flavor> flavors;
	private String templateType;
	private List<String> validTemplates;
	
	private static final Logger LOGGER = Logger.getLogger(OpenNebulaComputePlugin.class);
	private Properties properties;

	public OpenNebulaComputePlugin(Properties properties){
		this(properties, new OpenNebulaClientFactory());
	}
		
	protected OpenNebulaComputePlugin(Properties properties, OpenNebulaClientFactory clientFactory) {
		this.properties = properties;
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
		
		validTemplates = new ArrayList<String>();

		templateType = properties.getProperty(OPENNEBULA_TEMPLATES);
		if (templateType != null
				&& !templateType.equals(OPENNEBULA_TEMPLATES_TYPE_ALL)) {
			validTemplates = getTemplatesInProperties(properties);
		}
		
		flavors = new ArrayList<Flavor>();
		
		// userdata
		fogbowTermToOpenNebula.put(OrderConstants.USER_DATA_TERM, "user_data");
		
		//ssh public key
		fogbowTermToOpenNebula.put(OrderConstants.PUBLIC_KEY_TERM, "ssh-public-key");
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String localImageId) {
		
		LOGGER.debug("Requesting instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt + "; localImageId=" + localImageId);
		
		Map<String, String> templateProperties = new HashMap<String, String>();
		
		// removing fogbow-order category
		categories.remove(new Category(OrderConstants.TERM, OrderConstants.SCHEME,
				OrderConstants.KIND_CLASS));			
		
		Flavor foundFlavor = getFlavor(token, xOCCIAtt.get(OrderAttribute.REQUIREMENTS.getValue()));
		
		// checking categories are valid	
		for (Category category : categories) {
			if (category.getScheme().equals(OrderConstants.TEMPLATE_RESOURCE_SCHEME)) {
				continue;
			}
			
			if (fogbowTermToOpenNebula.get(category.getTerm()) == null) {
				throw new OCCIException(ErrorType.BAD_REQUEST,
						ResponseConstants.CLOUD_NOT_SUPPORT_CATEGORY + category.getTerm());
			} 
			
			if (category.getTerm().equals(OrderConstants.PUBLIC_KEY_TERM)) {
				templateProperties.put("ssh-public-key",
						xOCCIAtt.get(OrderAttribute.DATA_PUBLIC_KEY.getValue()));
			}
		}		
		
		// image or flavor was not specified
		if (foundFlavor == null || localImageId == null){
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		String userdata = xOCCIAtt.get(OrderAttribute.USER_DATA_ATT.getValue());

		templateProperties.put("mem", String.valueOf(foundFlavor.getMem()));
		templateProperties.put("cpu", String.valueOf(foundFlavor.getCpu()));
		templateProperties.put("userdata", userdata);
		templateProperties.put("image-id", localImageId);
		templateProperties.put("disk-size", String.valueOf(foundFlavor.getDisk()));

		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		String vmTemplate = generateTemplate(templateProperties);	
		
		LOGGER.debug("The instance will be allocated according to template: " + vmTemplate);
		return clientFactory.allocateVirtualMachine(oneClient, vmTemplate);
	}

	protected String generateTemplate(Map<String, String> templateProperties) {
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
			if (Boolean.parseBoolean(properties.getProperty(
					OneConfigurationConstants.COMPUTE_ONE_NETWORK_CONTEXTUALIZATION, 
					Boolean.FALSE.toString()))) {
				Element networkContextElement = doc.createElement("NETWORK");
				networkContextElement.appendChild(doc.createTextNode("YES"));
				contextElement.appendChild(networkContextElement);
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
			
			String diskSize = templateProperties.get("disk-size");
			if (!diskSize.equals("0")) {
				// disk volatile
				Element diskVolatileElement = doc.createElement("DISK");
				templateElement.appendChild(diskVolatileElement);
				// size
				Element sizeElement = doc.createElement("SIZE");
				sizeElement.appendChild(doc.createTextNode(diskSize));
				diskVolatileElement.appendChild(sizeElement);
				// type 
				Element typeElementDisk = doc.createElement("TYPE");
				typeElementDisk.appendChild(doc.createTextNode("fs"));
				diskVolatileElement.appendChild(typeElementDisk);
			}
			
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
			instances.add(createVMInstance(virtualMachine));
		}
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.debug("Getting instance " + instanceId + " of token: " + token);

		if (getFlavors() == null || getFlavors().isEmpty() ) {
			updateFlavors(token);
		}
		
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		VirtualMachine vm = clientFactory.createVirtualMachine(oneClient, instanceId);
		return createVMInstance(vm);
	}

	private Instance createVMInstance(VirtualMachine vm) {
		LOGGER.debug("Mounting instance structure of instanceId: " + vm.getId());

		String mem = vm.xpath("TEMPLATE/MEMORY");
		String cpu = vm.xpath("TEMPLATE/CPU");
		String image = vm.xpath("TEMPLATE/DISK/IMAGE");
		String arch = vm.xpath("TEMPLATE/OS/ARCH");
		String privateIp = vm.xpath("TEMPLATE/NIC/IP");
		
		LOGGER.debug("mem=" + mem + ", cpu=" + cpu + ", image=" + image + ", arch=" + arch + ", privateIP=" + privateIp);

		// TODO To get information about network when it'll be necessary
		// vm.xpath("TEMPLATE/NIC/NETWORK");
		// vm.xpath("TEMPLATE/NIC/NETWORK_ID");

		Map<String, String> attributes = new HashMap<String, String>();
		// CPU Architecture of the instance
		attributes.put("occi.compute.architecture", getArch(arch));
		InstanceState state = getInstanceState(vm.lcmStateStr());
		attributes.put("occi.compute.state", state.getOcciState());
		// CPU Clock frequency (speed) in gigahertz
		attributes.put("occi.compute.speed", "Not defined");
		attributes.put("occi.compute.memory", String.valueOf(Double.parseDouble(mem) / 1024)); // Gb
		attributes.put("occi.compute.cores", cpu);
		attributes.put("occi.compute.hostname", vm.getName());
		attributes.put("occi.core.id", vm.getId());
		attributes.put(Instance.LOCAL_IP_ADDRESS_ATT, privateIp);

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		Resource flavorResource = ResourceRepository.generateFlavorResource(getUsedFlavor(
				Double.parseDouble(cpu), Double.parseDouble(mem)));
		if (flavorResource != null) {
			resources.add(flavorResource);
		}
		
		return new Instance(vm.getId(), resources, attributes, new ArrayList<Link>(), state);
	}

	private String getArch(String arch) {		
		// x86 is default
		return !arch.isEmpty() ? arch : "x86";
	}

	private InstanceState getInstanceState(String oneVMState) {
		if ("Running".equalsIgnoreCase(oneVMState)) {
			return InstanceState.RUNNING;
		}
		if ("Suspended".equalsIgnoreCase(oneVMState)) {
			return InstanceState.SUSPENDED;
		}
		if ("Failure".equalsIgnoreCase(oneVMState)) {
			return InstanceState.FAILED;
		}
		return InstanceState.PENDING;
	}


	private String getUsedFlavor(double cpu, double mem) {
		try {
			List<Flavor> flavors = new ArrayList<Flavor>(this.flavors);
			for (Flavor flavor : flavors) {
				if (Double.parseDouble(flavor.getCpu()) == cpu && Double.parseDouble(flavor.getMem()) == mem) {
					return flavor.getName();
				}
			}		
		} catch (Exception e) {}
		return null;
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
		
		LOGGER.debug("Information about quota : MaxUserCpu = " + maxUserCpuStr + ", CPUUserInUse = " + cpuUserInUseStr
				+ ", MaxUserMem = " + maxUserMemStr + ", MemUserInUse = " + memUserInUseStr + ", MaxUserInUser = " + maxUserVMsStr
				+ ", VmsYserInUse = " + vmsUserInUseStr + ", MaxGroupCpu = " + maxGroupCpuStr + ", CpuGroupInUse = " + cpuGroupInUseStr
				+ ", MaxGroupMem = " + maxGroupMemStr + ", MemGroupInUse = " + memGroupInUseStr + ", MaxGroupVms = " + maxGroupVMsStr
				+ ", VmsGroupInUse" + vmsGroupInUseStr + ".");
		
		ResourceQuota resourceQuota = getQuota(maxUserCpuStr, cpuUserInUseStr, maxGroupCpuStr, cpuGroupInUseStr);
		double maxCpu = resourceQuota.getMax();
		double cpuInUse = resourceQuota.getInUse();
	
		resourceQuota = getQuota(maxUserMemStr, memUserInUseStr, maxGroupMemStr, memGroupInUseStr);
		double maxMem = resourceQuota.getMax();
		double memInUse = resourceQuota.getInUse();
		
		resourceQuota = getQuota(maxUserVMsStr, vmsUserInUseStr, maxGroupVMsStr, vmsGroupInUseStr);
		double maxVMs = resourceQuota.getMax();
		int instancesInUse = (int) resourceQuota.getInUse();

		double cpuIdle = maxCpu - cpuInUse;
		double memIdle = maxMem - memInUse;
		int instancesIdle = (int) (maxVMs - instancesInUse);
	
		return new ResourcesInfo(String.valueOf(cpuIdle), String.valueOf(cpuInUse),
				String.valueOf(memIdle), String.valueOf(memInUse), 
				String.valueOf(instancesIdle), String.valueOf(instancesInUse));
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

	@Override
	public void bypass(Request request, Response response) {
		response.setStatus(new Status(HttpStatus.SC_BAD_REQUEST),
				ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE);
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName, String diskFormat) {
		LOGGER.info("Uploading image... ");
		LOGGER.info("Token=" + token.getAccessId() + "; imagePath=" + imagePath + "; imageName="
				+ imageName);
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);		
		
		String imageSourcePath;
		if (isRemoteCloudManager()){
			LOGGER.info("Cloud Manager is Remote. Doing SCP to cloud manager machine.");
			String remoteFilePath = sshTargetTempFolder + "/" + UUID.randomUUID();
			LOGGER.info("Remote File Path=" + remoteFilePath);			
			imageSourcePath = remoteFilePath;
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
		} else {
			LOGGER.info("Cloud Manager is Local. Manager is running in the same machine that cloud manager.");
			imageSourcePath = imagePath;
		}
		
		LOGGER.debug("Image Source Path = " + imageSourcePath);
		Map<String, String> templateProperties = new HashMap<String, String>();
		templateProperties.put("image_name", imageName);
		templateProperties.put("image_path", imageSourcePath);
		templateProperties.put("image_type", "OS");
		Long imageSize = (long) Math.ceil(((double) new File(imagePath).length()) / (1024d * 1024d));
		templateProperties.put("image_size", imageSize.toString());
		
		LOGGER.info("Template properties = " + templateProperties);
		OneResponse response = Image.allocate(oneClient, generateImageTemplate(templateProperties), dataStoreId);
		
		if (response.isError()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		
		Image.chmod(oneClient, response.getIntMessage(), 744);
	}
	
	private boolean isRemoteCloudManager() {
		return sshHost != null && !sshHost.isEmpty();
	}

	protected String generateImageTemplate(Map<String, String> templateProperties) {
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

			Element typeElement = doc.createElement("TYPE");
			typeElement.appendChild(doc.createTextNode(templateProperties.get("image_type")));
			rootElement.appendChild(typeElement);

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
		ImagePool imagePool = clientFactory.createImagePool(oneClient);
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

	protected List<Flavor> getFlavors() {
		return this.flavors;
	}

	protected void updateFlavors(Token token) {
		Client oneClient = this.clientFactory.createClient(token.getAccessId(),
				openNebulaEndpoint);
		List<Flavor> newFlavors = new ArrayList<Flavor>();		
		
		Map<String, String> imageSizes = new HashMap<String, String>();
		ImagePool imagePool = this.clientFactory.createImagePool(oneClient);
		for (Image image : imagePool) {
			imageSizes.put(image.getName(), image.xpath("SIZE"));
		}				
		
		List<Flavor> allFlavorsTemplate = new ArrayList<Flavor>();
		TemplatePool templatePool = this.clientFactory.createTemplatePool(oneClient);
		for (Template template : templatePool) {
			String name = template.xpath("NAME");
			String memory = template.xpath("TEMPLATE/MEMORY");
			String vcpu = template.xpath("TEMPLATE/CPU");
			
			allFlavorsTemplate.add(new Flavor(name, vcpu, memory, 0));
			
			boolean containsFlavor = false;
			List<Flavor> flavors = new ArrayList<Flavor>(this.flavors);
			for (Flavor flavor : flavors) {
				if (name.equals(flavor.getName())) {
					containsFlavor = true;
					break;
				}
			} 
			if (containsFlavor) {
				continue;
			}
			
			if (templateType != null 
					&& !templateType.equals(OPENNEBULA_TEMPLATES_TYPE_ALL)
					&& !validTemplates.contains(name)) {
				continue;
			}

			int diskIndex = 1;
			int allDiskSize = 0;
			while (true) {
				String imageDiskName = template.xpath("TEMPLATE/DISK[" + diskIndex + "]/IMAGE");
				String volatileDiskSize = template.xpath("TEMPLATE/DISK[" + diskIndex + "]/SIZE");
				if (volatileDiskSize != null && !volatileDiskSize.isEmpty()) {
					try {
						allDiskSize += Integer.parseInt(volatileDiskSize);
					} catch (Exception e) {
					}
				} else if (imageDiskName != null && !imageDiskName.isEmpty()){
					try {
						allDiskSize += Integer.parseInt(imageSizes.get(imageDiskName));
					} catch (Exception e) {
					}
				}
				diskIndex++;
				if (template.xpath("TEMPLATE/DISK[" + diskIndex + "]") == null
						|| template.xpath("TEMPLATE/DISK[" + diskIndex + "]").isEmpty()) {
					break;
				}
			}

			newFlavors.add(new Flavor(name, vcpu, memory, String.valueOf(allDiskSize)));
		}
		if (newFlavors != null) {
			this.flavors.addAll(newFlavors);			
		}
		removeInvalidFlavors(allFlavorsTemplate);
	}
	
	protected void removeInvalidFlavors(List<Flavor> flavors) {
		ArrayList<Flavor> copyFlavors = new ArrayList<Flavor>(this.flavors);
		for (Flavor flavor : copyFlavors) {
			boolean containsFlavor = false;
			for (Flavor flavorName : flavors) {
				if (flavorName.getName().equals(flavor.getName())) {
					containsFlavor = true;
					continue;
				}
			}
			if (!containsFlavor && copyFlavors.size() != 0) {
				try {
					this.flavors.remove(flavor);					
				} catch (Exception e) {
				}
			}
		}
	}	
	
	protected void setFlavors(List<Flavor> flavors) {
		this.flavors = flavors;
	}

	protected Flavor getFlavor(Token token, String requirements) {
		if (templateType == null || templateType.isEmpty()) {
			String cpu = getMinimumValueForRequirement(requirements, RequirementsHelper.GLUE_VCPU_TERM, 1);
			String mem = getMinimumValueForRequirement(requirements, RequirementsHelper.GLUE_MEM_RAM_TERM, 1024);
			String disk = getMinimumValueForRequirement(requirements, RequirementsHelper.GLUE_DISK_TERM, 0);
			return new Flavor("flavor", cpu, mem, disk);
		} 
		updateFlavors(token);
		return RequirementsHelper.findSmallestFlavor(getFlavors(),requirements);			
	}
	
	private String getMinimumValueForRequirement(String requirements, 
			String term, int minimumValue) {
		String valueStr = RequirementsHelper
				.getSmallestValueForAttribute(requirements, term);
		return String.valueOf(Math.max(Integer.valueOf(valueStr), minimumValue));
	}

	protected List<String> getTemplatesInProperties(Properties properties) {
		List<String> listTemplate = new ArrayList<String>();
		String propertiesTample = (String) properties.get(OPENNEBULA_TEMPLATES);
		if (propertiesTample != null) {
			String[] templates = propertiesTample.split(",");
			for (String template : templates) {
				listTemplate.add(template.trim());
			}
		}
		return listTemplate;
	}
	
	protected void setClientFactory(OpenNebulaClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}
	
	@Override
	public ImageState getImageState(Token token, String imageName) {
		LOGGER.debug("Getting image status from image " + imageName + " with token " + token);
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		ImagePool imagePool = clientFactory.createImagePool(oneClient);
		OneResponse response = imagePool.info();
		
		if (response.isError()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
		
		for (Image image : imagePool) {
			if (image.getName().equals(imageName)) {
				/*
				 * Possible one image state described on
				 * http://archives.opennebula.org/documentation:rel4.4:img_guide
				 */
				String imageState = image.stateString();
				if ("LOCKED".equals(imageState)) {
					return ImageState.PENDING;
				} else if ("READY".equals(imageState) || "USED".equals(imageState)
						|| "USED_PERS".equals(imageState)) {
					return ImageState.ACTIVE;
				}
				return ImageState.FAILED;
			}
		}
		return null;
	}
	
	private static class ResourceQuota {
		
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

	@Override
	public String attach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		String storageId = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
		Client client = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		String diskTemplate = generateDiskTemplate(storageId);
		OneResponse attachResponse = VirtualMachine.diskAttach(client, Integer.valueOf(instanceId), diskTemplate);
		if (attachResponse.isError()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, attachResponse.getMessage());
		}
		
		String attachmentId = attachResponse.getMessage();
		attachmentId = getAttachmentId(instanceId, storageId, client);
		
		return UUID.randomUUID().toString() + "-disk-" + attachmentId;
	}

	private String getAttachmentId(String instanceId, String storageId,
			Client client) {
		OneResponse vmInfo = VirtualMachine.info(client, Integer.valueOf(instanceId));
		if (vmInfo.isError()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, vmInfo.getMessage());
		}
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
			StringBuilder xmlStringBuilder = new StringBuilder();
			xmlStringBuilder.append(vmInfo.getMessage());
			ByteArrayInputStream inputStream = new ByteArrayInputStream(xmlStringBuilder.toString().getBytes("UTF-8"));
			Document document = documentBuilder.parse(inputStream);
			NodeList templateElements = document.getElementsByTagName("TEMPLATE");
			if (templateElements.getLength() > 0) {
				Element vmTemplate = (Element) templateElements.item(0);
				NodeList diskElements = vmTemplate.getElementsByTagName("DISK");
				for (int i = 0; i < diskElements.getLength(); i++) {
					Element disk = (Element) diskElements.item(i);
					NodeList imageIdElements = disk.getElementsByTagName("IMAGE_ID");
					if (imageIdElements.getLength() > 0) {
						Node imageID = imageIdElements.item(0);
						if (imageID.getTextContent().equals(storageId)) {
							return disk.getElementsByTagName("DISK_ID").item(0).getTextContent();
						}
					}
				}
			}
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, e.getMessage());
		}
		return instanceId;
	}
	
	private String generateDiskTemplate(String volumeId) {
		return "DISK=[IMAGE_ID=" + volumeId + "]";
	}

	@Override
	public void dettach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
		String attachmentId = xOCCIAtt.get(StorageAttribute.ATTACHMENT_ID.getValue());
		
		String[] attachmentIdPieces = attachmentId.split("-disk-");
		String diskId = attachmentIdPieces[attachmentIdPieces.length-1];
		
		Client client = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		OneResponse response = VirtualMachine.diskDetach(client, Integer.parseInt(instanceId), Integer.parseInt(diskId));
		if (response.isError()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getMessage());
		}
	}
	
}

