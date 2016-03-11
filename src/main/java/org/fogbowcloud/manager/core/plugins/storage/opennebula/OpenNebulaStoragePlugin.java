package org.fogbowcloud.manager.core.plugins.storage.opennebula;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OneConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.image.Image;
import org.opennebula.client.image.ImagePool;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OpenNebulaStoragePlugin implements StoragePlugin {
	private static final Logger LOGGER = Logger.getLogger(OpenNebulaStoragePlugin.class);
	public static final String OPENNEBULA_DATABLOCK_IMAGE_TYPE = "DATABLOCK";
	public static final String OPENNEBULA_RAW_FSTYPE = "raw";
	public static final String OPENNEBULA_BLOCK_DISK_TYPE = "BLOCK";
	public static final String OPENNEBULA_DATASTORE_DEFAULT_DEVICE_PREFIX = "vd";
	
	
	private OpenNebulaClientFactory clientFactory;
	private String openNebulaEndpoint;
	private Integer dataStoreId;
	private String devicePrefix;

	
	public OpenNebulaStoragePlugin(Properties properties) {
		this(properties, new OpenNebulaClientFactory());
	}

	public OpenNebulaStoragePlugin(Properties properties,
			OpenNebulaClientFactory openNebulaClientFactory) {
		this.openNebulaEndpoint = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_URL);
		this.clientFactory = openNebulaClientFactory;
		String dataStoreIdStr = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_DATASTORE_ID);
		dataStoreId = dataStoreIdStr == null ? null: Integer.valueOf(dataStoreIdStr);
		devicePrefix = properties.getProperty(OneConfigurationConstants.STORAGE_ONE_DATASTORE_DEFAULT_DEVICE_PREFIX, 
				OPENNEBULA_DATASTORE_DEFAULT_DEVICE_PREFIX);
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		
		int size = Integer.parseInt(xOCCIAtt.get(OrderAttribute.STORAGE_SIZE.getValue())) * 1024;
		
		Map<String, String> templateProperties = new HashMap<String, String>();
		String imageName = "fogbow-volume-" + UUID.randomUUID().toString();
		templateProperties.put("volume_name", imageName);
		templateProperties.put("volume_type", OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		templateProperties.put("volume_fstype", OPENNEBULA_RAW_FSTYPE);
		templateProperties.put("volume_disk_type", OPENNEBULA_BLOCK_DISK_TYPE);
		templateProperties.put("volume_size", String.valueOf(size));
		
		String volumeTemplate = generateVolumeTemplate(templateProperties);
		Client client = this.clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		
		LOGGER.debug("Creating datablock image with template: " + volumeTemplate);
		return clientFactory.allocateImage(client, volumeTemplate, dataStoreId);
	}
	
	protected String generateVolumeTemplate(Map<String, String> templateProperties) {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("IMAGE");
			doc.appendChild(rootElement);

			Element nameElement = doc.createElement("NAME");
			nameElement.appendChild(doc.createTextNode(templateProperties.get("volume_name")));
			rootElement.appendChild(nameElement);
			
			Element persistentElement = doc.createElement("PERSISTENT");
			persistentElement.appendChild(doc.createTextNode("YES"));
			rootElement.appendChild(persistentElement);

			Element sizeElement = doc.createElement("SIZE");
			sizeElement.appendChild(doc.createTextNode(templateProperties.get("volume_size")));
			rootElement.appendChild(sizeElement);

			Element typeElement = doc.createElement("TYPE");
			typeElement.appendChild(doc.createTextNode(templateProperties.get("volume_type")));
			rootElement.appendChild(typeElement);
			
			Element fstypeElement = doc.createElement("FSTYPE");
			fstypeElement.appendChild(doc.createTextNode(templateProperties.get("volume_fstype")));
			rootElement.appendChild(fstypeElement);
			
			Element disktypeElement = doc.createElement("DISK_TYPE");
			disktypeElement.appendChild(doc.createTextNode(templateProperties.get("volume_disk_type")));
			rootElement.appendChild(disktypeElement);
			
			Element devicePrefix = doc.createElement("DEV_PREFIX");
			devicePrefix.appendChild(doc.createTextNode(this.devicePrefix));
			rootElement.appendChild(devicePrefix);

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
	public List<Instance> getInstances(Token token) {
		LOGGER.debug("Getting all datablock images.");
		Client client = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		ImagePool imagePool = clientFactory.createImagePool(client);
		List<Instance> instances = new LinkedList<Instance>();
		for (Image image : imagePool) {
			if (image.typeStr().equalsIgnoreCase(OPENNEBULA_DATABLOCK_IMAGE_TYPE)) {
				instances.add(createInstance(image));
			}
		}
		return instances;
	}
	
	private Instance createInstance(Image oneImage) {
		String id = oneImage.getId();
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get(OrderConstants.STORAGE_TERM));
		
		OneResponse info = oneImage.info();
		Map<String, String> attributes = new HashMap<String, String>();
		// CPU Architecture of the instance
		attributes.put("occi.storage.name", oneImage.getName());
		attributes.put("occi.storage.status", oneImage.stateString());
		String sizeInMB = oneImage.xpath("SIZE");
		Integer sizeInGB = (Integer.valueOf(sizeInMB) / 1024);
		attributes.put("occi.storage.size", String.valueOf(sizeInGB));
		attributes.put("occi.core.id", id);
		
		return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), null);
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.debug("Getting datablock image ID: " + instanceId);
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			if (instance.getId().equals(instanceId)) {
				LOGGER.debug("Datablock image found: ID " + instance.getId());
				return instance;
			}
		}
		LOGGER.debug("Datablock image with ID " + instanceId + " not found.");
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		LOGGER.debug("Removing datablock image ID: " + instanceId);
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		ImagePool imagePool = clientFactory.createImagePool(oneClient);
		for (Image image : imagePool) {
			if (image.typeStr().equals(
					OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE)
					&& image.getId().equals(instanceId)) {
				OneResponse deleteResponse = image.delete();
				if (deleteResponse.isError()) {
					throw new OCCIException(ErrorType.BAD_REQUEST, deleteResponse.getMessage());
				}
				return;
			}
		}
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND_INSTANCE);
	}

	@Override
	public void removeInstances(Token token) {
		LOGGER.debug("Removing all datablock images.");
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		ImagePool createImagePool = clientFactory.createImagePool(oneClient);
		for (Image image : createImagePool) {
			if (image.typeStr().equals(
					OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE)) {
				OneResponse deleteResponse = image.delete();
				if (deleteResponse.isError()) {
					LOGGER.debug("Could not delete datablock image: " + image.getName() + ", ID: " + image.getId());
				}
			}
		}
	}
}