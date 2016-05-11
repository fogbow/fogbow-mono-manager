package org.fogbowcloud.manager.core.plugins.network.opennebula;

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
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OneConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.vnet.VirtualNetwork;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OpenNebulaNetworkPlugin implements NetworkPlugin {
	private static final Logger LOGGER = Logger.getLogger(OpenNebulaNetworkPlugin.class);
	
	private static final String NETWORK_NAME = "network_name";
	private static final String NETWORK_DESCRIPTION = "network_description";
	private static final String NETWORK_BRIDGE = "network_bridge";
	private static final String NETWORK_AR_TYPE = "network_ar_type";
	private static final String NETWORK_AR_START_IP = "network_ar_start_ip";
	private static final String NETWORK_AR_SIZE = "network_ar_size";
	private static final String NETWORK_ADDRESS = "network_address";
	private static final String NETWORK_GATEWAY = "network_gateway";
	
	private OpenNebulaClientFactory clientFactory;
	private Properties properties;
	private String openNebulaEndpoint;
	private String bridge;
	
	public OpenNebulaNetworkPlugin (Properties properties) {
		this(properties, new OpenNebulaClientFactory());
	}
	
	protected OpenNebulaNetworkPlugin (Properties properties, OpenNebulaClientFactory clientFactory) {
		this.properties = properties;
		this.clientFactory = clientFactory;
		this.openNebulaEndpoint = properties.getProperty(OneConfigurationConstants.COMPUTE_ONE_URL);
		this.bridge = properties.getProperty(OneConfigurationConstants.NETWORK_ONE_BRIDGE);
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		LOGGER.debug("Requesting network instance with token=" + token + "; categories="
				+ categories + "; xOCCIAtt=" + xOCCIAtt + "; bridge=" + bridge);
		
		String address = xOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS);
		String gateway = xOCCIAtt.get(OCCIConstants.NETWORK_GATEWAY);
		String allocation = xOCCIAtt.get(OCCIConstants.NETWORK_ALLOCATION);
		String label = xOCCIAtt.get(OCCIConstants.NETWORK_LABEL);
		String vlanId = xOCCIAtt.get(OCCIConstants.NETWORK_VLAN);
		
		String networkName = "fogbow" + (int) (Math.random() * 100000);
		
		Map<String, String> templateProperties = new HashMap<String, String>();
		templateProperties.put(NETWORK_NAME, networkName);
		templateProperties.put(NETWORK_DESCRIPTION, 
				"Virtual network created by " + token.getUser());
		templateProperties.put(NETWORK_BRIDGE, bridge);
		templateProperties.put(NETWORK_AR_TYPE, "IP4");
		templateProperties.put(NETWORK_AR_START_IP, address);
		templateProperties.put(NETWORK_AR_SIZE, "256");
		templateProperties.put(NETWORK_ADDRESS, address);
		templateProperties.put(NETWORK_GATEWAY, gateway);
		
		String vnetTemplate = generateNetworkTemplate(templateProperties);
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		
		LOGGER.debug("The network instance will be allocated according to template: " + vnetTemplate);
		return clientFactory.allocateNetwork(oneClient, vnetTemplate);
	}
	
	protected String generateNetworkTemplate(Map<String, String> templateProperties) {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();
			// template elements
			Document doc = docBuilder.newDocument();
			Element template = doc.createElement("TEMPLATE");
			doc.appendChild(template);
			
			Element name = doc.createElement("NAME");
			name.appendChild(doc.createTextNode(templateProperties.get(NETWORK_NAME)));
			template.appendChild(name);
			
			Element description = doc.createElement("DESCRIPTION");
			description.appendChild(doc.createTextNode(templateProperties.get(NETWORK_DESCRIPTION)));
			template.appendChild(description);
			
			Element bridge = doc.createElement("BRIDGE");
			bridge.appendChild(doc.createTextNode(templateProperties.get(NETWORK_BRIDGE)));
			template.appendChild(bridge);
			
			Element networkAddress = doc.createElement("NETWORK_ADDRESS");
			networkAddress.appendChild(doc.createTextNode(templateProperties.get(NETWORK_ADDRESS)));
			template.appendChild(networkAddress);
			
			Element networkGateway = doc.createElement("NETWORK_GATEWAY");
			networkGateway.appendChild(doc.createTextNode(templateProperties.get(NETWORK_GATEWAY)));
			template.appendChild(networkGateway);
			
			Element addressRange = doc.createElement("AR");
			template.appendChild(addressRange);
			
			Element arType = doc.createElement("TYPE");
			arType.appendChild(doc.createTextNode(templateProperties.get(NETWORK_AR_TYPE)));
			addressRange.appendChild(arType);
			
			Element arStartIP = doc.createElement("IP");
			arStartIP.appendChild(doc.createTextNode(templateProperties.get(NETWORK_AR_START_IP)));
			addressRange.appendChild(arStartIP);
			
			Element arSize = doc.createElement("SIZE");
			arSize.appendChild(doc.createTextNode(templateProperties.get(NETWORK_AR_SIZE)));
			addressRange.appendChild(arSize);
			
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
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.info("Getting network instance ID=" + instanceId + " and token=" + token);
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		VirtualNetwork vnet = clientFactory.createVirtualNetwork(oneClient, instanceId);
		return createInstance(vnet);
	}

	private Instance createInstance(VirtualNetwork vnet) {
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get(OrderConstants.NETWORK_TERM));
		String netAddr = vnet.xpath("TEMPLATE/NETWORK_ADDRESS");
		String netGateway = vnet.xpath("TEMPLATE/NETWORK_GATEWAY");
		String vlanId = vnet.xpath("TEMPLATE/VLAN_ID");
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(OCCIConstants.ID, vnet.getId());
		attributes.put(OCCIConstants.TITLE, vnet.getName());
		attributes.put(OCCIConstants.NETWORK_LABEL, "");
		attributes.put(OCCIConstants.NETWORK_VLAN, vlanId);
		attributes.put(OCCIConstants.NETWORK_STATE, 
				OCCIConstants.NetworkState.ACTIVE.getValue());
		attributes.put(OCCIConstants.NETWORK_ADDRESS, netAddr);
		attributes.put(OCCIConstants.NETWORK_GATEWAY, netGateway);
		attributes.put(OCCIConstants.NETWORK_ALLOCATION, 
				OCCIConstants.NetworkAllocation.DYNAMIC.getValue());
		
		return new Instance(vnet.getId(), resources, attributes, new ArrayList<Link>(), null);
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		LOGGER.info("Removing network instance ID=" + instanceId + " and token=" + token);
		Client oneClient = clientFactory.createClient(token.getAccessId(), openNebulaEndpoint);
		VirtualNetwork vnet = clientFactory.createVirtualNetwork(oneClient, instanceId);
		OneResponse response = vnet.delete();
		if (response.isError()) {
			LOGGER.error("Error occurred while trying to delete network instance with ID=" 
					+ instanceId + "; " + response.getErrorMessage());
			throw new OCCIException(ErrorType.BAD_REQUEST, response.getErrorMessage());
		}
	}

}
