package org.fogbowcloud.manager.core.plugins.network.ec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.plugins.identity.ec2.EC2IdentityPlugin;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderConstants;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AttachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.CreateInternetGatewayResult;
import com.amazonaws.services.ec2.model.CreateRouteRequest;
import com.amazonaws.services.ec2.model.CreateSubnetRequest;
import com.amazonaws.services.ec2.model.CreateSubnetResult;
import com.amazonaws.services.ec2.model.CreateVpcRequest;
import com.amazonaws.services.ec2.model.CreateVpcResult;
import com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest;
import com.amazonaws.services.ec2.model.DeleteSubnetRequest;
import com.amazonaws.services.ec2.model.DeleteVpcRequest;
import com.amazonaws.services.ec2.model.DescribeInternetGatewaysResult;
import com.amazonaws.services.ec2.model.DescribeRouteTablesResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.DetachInternetGatewayRequest;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.InternetGateway;
import com.amazonaws.services.ec2.model.InternetGatewayAttachment;
import com.amazonaws.services.ec2.model.ModifyVpcAttributeRequest;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Subnet;

public class EC2NetworkPlugin implements NetworkPlugin {
	
	private static final String DEFAULT_DESTINATION_CIRD = "0.0.0.0/0";
	private static final String COULD_NOT_CREATE_EC2_NETWORK = "Could not create ec2 network.";
	private static final String COULD_NOT_REMOVE_EC2_NETWORK = "Could not remove ec2 network.";
	protected static final String NETWORK_EC2_REGION = "network_ec2_region";
	private static final Logger LOGGER = Logger.getLogger(EC2NetworkPlugin.class);
	
	private String region;
	
	public EC2NetworkPlugin(Properties properties) {
		this.region = properties.getProperty(NETWORK_EC2_REGION);
		if (this.region == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "EC2 network region not specified.");
		}
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		LOGGER.debug("Starting to create network instance (EC2).");
		AmazonEC2Client ec2Client = createEC2Client(token);	
		
		String cird = xOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS);
		CreateVpcResult createVpcResult = null;
		try {
			LOGGER.debug("Trying create VPC with: " + cird);
			createVpcResult = ec2Client.createVpc(new CreateVpcRequest(cird));
		} catch (Exception e) {
			LOGGER.error("Could not create VPC.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, COULD_NOT_CREATE_EC2_NETWORK);
		}
		
		String vpcId = null;
		CreateSubnetResult createSubnet = null;
		try {
			vpcId = createVpcResult.getVpc().getVpcId();
			createSubnet = ec2Client.createSubnet(new CreateSubnetRequest(vpcId, cird));
		} catch (Exception e) {
			LOGGER.error("Could not create subnet.", e);
			removeVPC(ec2Client, vpcId);
			throw new OCCIException(ErrorType.BAD_REQUEST, COULD_NOT_CREATE_EC2_NETWORK);
		}
		
		try {
			// enable dns hostnames
			ModifyVpcAttributeRequest modifyVpcAttributeRequest = new ModifyVpcAttributeRequest()
					.withEnableDnsHostnames(true).withVpcId(vpcId);
			ec2Client.modifyVpcAttribute(modifyVpcAttributeRequest);

			// enable dns support			
			modifyVpcAttributeRequest = new ModifyVpcAttributeRequest()
					.withEnableDnsSupport(true).withVpcId(vpcId);			
			ec2Client.modifyVpcAttribute(modifyVpcAttributeRequest);
		} catch (Exception e) {
			LOGGER.error("Could not modify vpc attributes.", e);
			removeSubnet(ec2Client, createSubnet.getSubnet().getSubnetId());
			removeVPC(ec2Client, vpcId);
			throw new OCCIException(ErrorType.BAD_REQUEST, COULD_NOT_CREATE_EC2_NETWORK);
		}
		
		CreateInternetGatewayResult createInternetGateway = null;
		try {
			createInternetGateway = ec2Client.createInternetGateway();			
		} catch (Exception e) {
			LOGGER.debug("Could not create internet gateway.", e);
			removeSubnet(ec2Client, createSubnet.getSubnet().getSubnetId());
			removeVPC(ec2Client, vpcId);
			throw new OCCIException(ErrorType.BAD_REQUEST, COULD_NOT_CREATE_EC2_NETWORK);
		}
		
		try {
			ec2Client.attachInternetGateway(new AttachInternetGatewayRequest()
					.withInternetGatewayId(createInternetGateway.getInternetGateway()
					.getInternetGatewayId()).withVpcId(vpcId));			
		} catch (Exception e) {
			LOGGER.error("Could not attach internet gateway.", e);
			ec2Client.deleteInternetGateway(new DeleteInternetGatewayRequest()
					.withInternetGatewayId(createInternetGateway.getInternetGateway().getInternetGatewayId()));			
			removeSubnet(ec2Client, createSubnet.getSubnet().getSubnetId());
			removeVPC(ec2Client, vpcId);	
			throw new OCCIException(ErrorType.BAD_REQUEST, COULD_NOT_CREATE_EC2_NETWORK);
		}
		
		CreateRouteRequest createRouteRequest = null;
		try {
			String routerId = getRouterIdByVpc(ec2Client, createVpcResult.getVpc().getVpcId());
			createRouteRequest = new CreateRouteRequest()
					.withRouteTableId(routerId).withGatewayId(createInternetGateway.getInternetGateway()
					.getInternetGatewayId()).withDestinationCidrBlock(DEFAULT_DESTINATION_CIRD);
			ec2Client.createRoute(createRouteRequest);			
		} catch (Exception e) {
			LOGGER.error("Could not create router.", e);
			removeInstance(token, createSubnet.getSubnet().getSubnetId());
			throw new OCCIException(ErrorType.BAD_REQUEST, COULD_NOT_CREATE_EC2_NETWORK);
		}
		
		LOGGER.debug("Ending to create network instance (EC2).");
		return createSubnet.getSubnet().getSubnetId();
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.debug("Getting information about networks(subnet) instance id: " + instanceId);
		
		Subnet subnet = getSubnetById(token, instanceId);
		return createInstance(subnet);
	}
	
	@Override
	public void removeInstance(Token token, String instanceId) {
		LOGGER.debug("Trying remove network: " + instanceId);
		AmazonEC2Client ec2Client = createEC2Client(token);
		Subnet subnetFound = getSubnetById(token, instanceId);
		
		removeSubnet(ec2Client, subnetFound.getSubnetId());
		
		try {
			detachInternetGateway(ec2Client, subnetFound);			
		} catch (Exception e) {
			LOGGER.error("Could not attach internet gateway.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, COULD_NOT_REMOVE_EC2_NETWORK);
		}		
		
		removeVPC(ec2Client, subnetFound.getVpcId());
		LOGGER.debug("Removing the network with Success: " + instanceId);
	}

	private void detachInternetGateway(AmazonEC2Client ec2Client, Subnet subnetFound) {
		DescribeInternetGatewaysResult describeInternetGateways = ec2Client.describeInternetGateways();		
		for (InternetGateway internetGateway : describeInternetGateways.getInternetGateways()) {
			List<InternetGatewayAttachment> attachments = internetGateway.getAttachments();
			for (InternetGatewayAttachment internetGatewayAttachment : attachments) {
				if (internetGatewayAttachment.getVpcId().equals(subnetFound.getVpcId())) {
					// Detach internet gateway
					ec2Client.detachInternetGateway(new DetachInternetGatewayRequest()
							.withVpcId(subnetFound.getVpcId()).withInternetGatewayId(
							internetGateway.getInternetGatewayId()));

					// remove internet gateway					
					ec2Client.deleteInternetGateway(new DeleteInternetGatewayRequest()
							.withInternetGatewayId(internetGateway.getInternetGatewayId()));
				}
			}
		}
	}

	private Subnet getSubnetById(Token token, String instanceId) {
		List<Subnet> subnets = new ArrayList<Subnet>();
		try {			
			subnets = getSubnets(token);			
		} catch (Exception e) {
			String errorMessage = "Could not get subnets.";
			LOGGER.error(errorMessage, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMessage);
		}
		Subnet subnetFound = null;
		for (Subnet subnet : subnets) {
			if (subnet.getSubnetId().equals(instanceId)) {
				subnetFound = subnet;
				break;
			}
		}
		if (subnetFound == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, "Network not found.");
		}
		return subnetFound;
	}	

	private String getRouterIdByVpc(AmazonEC2Client ec2Client, String vpcId) {
		DescribeRouteTablesResult describeRouteTables = ec2Client.describeRouteTables();
		List<RouteTable> routeTables = describeRouteTables.getRouteTables();
		for (RouteTable routeTable : routeTables) {
			if (routeTable.getVpcId().equals(vpcId)) {
				return routeTable.getRouteTableId();
			}
		}
		return null;
	}
	
	private void removeSubnet(AmazonEC2Client ec2Client, String subnetId) {
		try {
			LOGGER.debug("Trying delete subnet with id: " + subnetId);
			ec2Client.deleteSubnet(new DeleteSubnetRequest(subnetId));			
		} catch (Exception e) {
			String errorMessage = "Could not delete subnet.";
			LOGGER.error(errorMessage, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMessage);
		}
	}
	
	private void removeVPC(AmazonEC2Client ec2Client, String vpcId) {
		DeleteVpcRequest deleteVpcRequest = new DeleteVpcRequest(vpcId);
		try {
			LOGGER.debug("Trying delete VPC with id: " + vpcId);
			ec2Client.deleteVpc(deleteVpcRequest);			
		} catch (Exception e) {
			String errorMessage = "Could not delete VPC.";
			LOGGER.error(errorMessage, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, errorMessage);
		}
	}	
	
	private List<Subnet> getSubnets(Token token) {
		AmazonEC2Client ec2Client = createEC2Client(token);
		try {
			LOGGER.debug("Trying get subnets");
			DescribeSubnetsResult describeSubnets = ec2Client.describeSubnets();
			return describeSubnets.getSubnets();			
		} catch (Exception e) {
			LOGGER.error("Could not get subnets", e);
			return null;
		}
	}	
	
	private Instance createInstance(Subnet subnet) {
		String subnetId = subnet.getSubnetId();
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get(OrderConstants.NETWORK_TERM));
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(OCCIConstants.NETWORK_ADDRESS, subnet.getCidrBlock());
		attributes.put(OCCIConstants.NETWORK_GATEWAY, "");
		attributes.put(OCCIConstants.NETWORK_ALLOCATION, OCCIConstants.NetworkAllocation.DYNAMIC.getValue());
		attributes.put(OCCIConstants.NETWORK_INTERFACE_STATE,
						"available".equals(subnet.getState()) ? OCCIConstants.NetworkState.ACTIVE
						.getValue(): OCCIConstants.NetworkState.INACTIVE.getValue());
		attributes.put(OCCIConstants.NETWORK_INTERFACE_MAC, "");
		attributes.put(OCCIConstants.NETWORK_INTERFACE_INTERFACE, "");	
		attributes.put(OCCIConstants.TITLE, subnetId);
		attributes.put(OCCIConstants.ID, subnetId);
		
		return new Instance(subnetId, resources, attributes, new ArrayList<Instance.Link>(), null);
	}	
	
	// For tests 
	public void run(Token token) {
		AmazonEC2Client ec2Client = createEC2Client(token);
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		
		runInstancesRequest.withImageId("ami-9abea4fb")
				.withInstanceType("t2.nano")
				.withMinCount(1)
				.withMaxCount(1);
		
		InstanceNetworkInterfaceSpecification networkSpec = 
				new InstanceNetworkInterfaceSpecification();
		networkSpec.withDeviceIndex(0);
		networkSpec.withSubnetId("subnet-b531dfed");
		networkSpec.withGroups("sg-14b34772");
		networkSpec.withAssociatePublicIpAddress(true);
		runInstancesRequest.withNetworkInterfaces(networkSpec);
		
		try {
			ec2Client.runInstances(runInstancesRequest);
		} catch (Exception e) {
			LOGGER.error("Couldn't start EC2 instance.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
			
	}
	
//	// For tests	
//	public Instance get(Token token) {
//		AmazonEC2Client ec2Client = createEC2Client(token);
//		
//		System.out.println("----------------------" + "VPC" + "-----------------------");
//		DescribeVpcsResult describeVpcs = ec2Client.describeVpcs();
//		List<Vpc> vpcs = describeVpcs.getVpcs();
//		for (Vpc vpc : vpcs) {
//			System.out.println(vpc.toString());
//		}
//		System.out.println("----------------------" + "SUBNET" + "-----------------------");
//		DescribeSubnetsResult describeSubnets = ec2Client.describeSubnets();
//		List<Subnet> subnets = describeSubnets.getSubnets();
//		for (Subnet subnet : subnets) {
//			System.out.println(subnet.toString());
//		}
//		System.out.println("----------------------" + "NET INTERFACE" + "-----------------------");
//		DescribeNetworkInterfacesResult describeNetworkInterfaces = ec2Client.describeNetworkInterfaces();
//		List<NetworkInterface> networkInterfaces = describeNetworkInterfaces.getNetworkInterfaces();
//		for (NetworkInterface networkInterface : networkInterfaces) {
//			System.out.println(networkInterface.toString());
//		}
//		System.out.println("----------------------" + "ROUTER" + "-----------------------");
//		DescribeRouteTablesResult describeRouteTables = ec2Client.describeRouteTables();
//		List<RouteTable> routeTables = describeRouteTables.getRouteTables();
//		for (RouteTable routeTable : routeTables) {
//			System.out.println(routeTable.toString());
//		}
//		System.out.println("----------------------" + "DHCP" + "-----------------------");
//		DescribeDhcpOptionsResult describeDhcpOptions = ec2Client.describeDhcpOptions();
//		List<DhcpOptions> dhcpOptions = describeDhcpOptions.getDhcpOptions();
//		for (DhcpOptions dhcpOption : dhcpOptions) {
//			System.out.println(dhcpOption.toString());
//		}
//		System.out.println("----------------------" + "GATEWAYID" + "-----------------------");
//		DescribeInternetGatewaysResult describeInternetGateways = ec2Client.describeInternetGateways();
//		
//		for (InternetGateway gateway : describeInternetGateways.getInternetGateways()) {
//			System.out.println(gateway.toString());
//		}	
//		
////		System.out.println("----------------------" + "IMAGES" + "-----------------------");
////		DescribeImagesResult describeImages = ec2Client.describeImages();
////		
////		for (Image image : describeImages.getImages()) {
////			System.out.println(image.toString());
////		}			
//		
//		System.out.println("----------------------" + "VOLUMES" + "-----------------------");
//		DescribeVolumesResult describeVolumes = ec2Client.describeVolumes();
//		
//		for (Volume volume: describeVolumes.getVolumes()) {
//			System.out.println(volume.toString());
//		}					
//		
//		return null;		
//	}
	
	private BasicAWSCredentials loadCredentials(Token token) {
		String accessKey = token.get(EC2IdentityPlugin.CRED_ACCESS_KEY);
		String secretKey = token.get(EC2IdentityPlugin.CRED_SECRET_KEY);
		
		if (accessKey == null || secretKey == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}
		
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
		return awsCreds;
	}	
	
	protected AmazonEC2Client createEC2Client(Token token) {
		BasicAWSCredentials awsCreds = loadCredentials(token);
		AmazonEC2Client ec2Client = new AmazonEC2Client(awsCreds);
		ec2Client.setRegion(Region.getRegion(Regions.fromName(region)));
		return ec2Client;
	}
	
}
