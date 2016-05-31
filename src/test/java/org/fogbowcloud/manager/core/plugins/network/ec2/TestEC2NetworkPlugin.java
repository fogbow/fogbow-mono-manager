package org.fogbowcloud.manager.core.plugins.network.ec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.network.ec2.EC2NetworkPlugin;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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
import com.amazonaws.services.ec2.model.InternetGateway;
import com.amazonaws.services.ec2.model.InternetGatewayAttachment;
import com.amazonaws.services.ec2.model.ModifyVpcAttributeRequest;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;

public class TestEC2NetworkPlugin {

	private EC2NetworkPlugin ec2NetworkPlugin;
	private Token tokenDefault;
	
	@Before
	public void setUp() {
		Properties properties = new Properties();
		properties.put(EC2NetworkPlugin.NETWORK_EC2_REGION, "ec2-region");
		ec2NetworkPlugin = Mockito.spy(new EC2NetworkPlugin(properties));
	}
	
	@Test(expected=OCCIException.class)
	public void testNetworkRegionNotSpecified() {
		ec2NetworkPlugin = new EC2NetworkPlugin(new Properties());
	}
	
	@Test
	public void testRequestInstance() {
		String subnetId = "subnetId00";
		String cird = "20.20.20.0/24";
		String vpcId = "vpcId00";
		
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// creating vpc
		CreateVpcRequest createVpcRequest = new CreateVpcRequest(cird);
		CreateVpcResult createVpcResult = new CreateVpcResult();
		Vpc vpc = new Vpc();
		vpc.setVpcId(vpcId);
		createVpcResult.setVpc(vpc);
		Mockito.when(createEC2Client.createVpc(createVpcRequest)).thenReturn(createVpcResult);

		// creating subnet id
		CreateSubnetRequest createSubnetRequest = new CreateSubnetRequest(vpcId, cird);
		CreateSubnetResult createSubnetResult = new CreateSubnetResult();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(subnetId);
		createSubnetResult.setSubnet(subnet);
		Mockito.when(createEC2Client.createSubnet(createSubnetRequest)).thenReturn(createSubnetResult);
		
		// creating internet gateway		
		CreateInternetGatewayResult createInternetGatewayResult = new CreateInternetGatewayResult();
		InternetGateway internetGateway = new InternetGateway();
		String internetGatewayId = "internetGateWayId00";
		internetGateway.setInternetGatewayId(internetGatewayId);
		createInternetGatewayResult.setInternetGateway(internetGateway);
		Mockito.when(createEC2Client.createInternetGateway()).thenReturn(createInternetGatewayResult);
		
		// creating router
		DescribeRouteTablesResult describeRouteTablesResult = new DescribeRouteTablesResult();
		Collection<RouteTable> routeTables = new ArrayList<RouteTable>();
		RouteTable routeTable = new RouteTable();
		routeTable.setVpcId(vpcId);
		routeTable.setRouteTableId("routeTableId");
		routeTables.add(routeTable);
		describeRouteTablesResult.setRouteTables(routeTables);
		Mockito.when(createEC2Client.describeRouteTables()).thenReturn(describeRouteTablesResult);

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, cird);
		String instance = ec2NetworkPlugin.requestInstance(tokenDefault, null, xOCCIAtt);
		
		Mockito.verify(createEC2Client, Mockito.times(1)).createVpc(
				Mockito.any(CreateVpcRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createSubnet(
				Mockito.any(CreateSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(2)).modifyVpcAttribute(
				Mockito.any(ModifyVpcAttributeRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createInternetGateway();
		Mockito.verify(createEC2Client, Mockito.times(1))
				.attachInternetGateway(Mockito.any(AttachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createRoute(
				Mockito.any(CreateRouteRequest.class));
		
		Assert.assertEquals(subnetId, instance);
	} 
	
	@Test
	public void testRequestInstanceErrorWhenCreatingRouter() {
		String subnetId = "subnetId00";
		String cird = "20.20.20.0/24";
		String vpcId = "vpcId00";
		
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// creating vpc
		CreateVpcRequest createVpcRequest = new CreateVpcRequest(cird);
		CreateVpcResult createVpcResult = new CreateVpcResult();
		Vpc vpc = new Vpc();
		vpc.setVpcId(vpcId);
		createVpcResult.setVpc(vpc);
		Mockito.when(createEC2Client.createVpc(createVpcRequest)).thenReturn(createVpcResult);

		// creating subnet id
		CreateSubnetRequest createSubnetRequest = new CreateSubnetRequest(vpcId, cird);
		CreateSubnetResult createSubnetResult = new CreateSubnetResult();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(subnetId);
		createSubnetResult.setSubnet(subnet);
		Mockito.when(createEC2Client.createSubnet(createSubnetRequest)).thenReturn(createSubnetResult);
		
		// creating internet gateway		
		CreateInternetGatewayResult createInternetGatewayResult = new CreateInternetGatewayResult();
		InternetGateway internetGateway = new InternetGateway();
		String internetGatewayId = "internetGateWayId00";
		internetGateway.setInternetGatewayId(internetGatewayId);
		createInternetGatewayResult.setInternetGateway(internetGateway);
		Mockito.when(createEC2Client.createInternetGateway()).thenReturn(createInternetGatewayResult);
		
		// getting routers table
		DescribeRouteTablesResult describeRouteTablesResult = new DescribeRouteTablesResult();
		Collection<RouteTable> routeTables = new ArrayList<RouteTable>();
		RouteTable routeTable = new RouteTable();
		routeTable.setRouteTableId("id");
		routeTables.add(routeTable);
		describeRouteTablesResult.setRouteTables(routeTables);
		Mockito.when(createEC2Client.describeRouteTables()).thenReturn(describeRouteTablesResult);
		
		Mockito.when(createEC2Client.describeRouteTables()).thenThrow(
				new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		// Removing instance because exception
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		subnet = new Subnet();
		subnet.setVpcId(vpcId);
		subnet.setSubnetId(subnetId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		
		//ec2Client.describeInternetGateways() 
		DescribeInternetGatewaysResult describeInternetGatewaysResult = new DescribeInternetGatewaysResult();
		Collection<InternetGateway> internetGateways = new ArrayList<InternetGateway>();
		internetGateway = new InternetGateway();
		internetGateway.setInternetGatewayId("internetGatewayId");
		Collection<InternetGatewayAttachment> attachments = new ArrayList<InternetGatewayAttachment>();
		InternetGatewayAttachment internetGatewayAttachment = new InternetGatewayAttachment();
		internetGatewayAttachment.setVpcId(vpcId);
		attachments.add(internetGatewayAttachment);
		internetGateway.setAttachments(attachments);
		internetGateways.add(internetGateway);
		describeInternetGatewaysResult.setInternetGateways(internetGateways);
		Mockito.when(createEC2Client.describeInternetGateways()).thenReturn(describeInternetGatewaysResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteVpc(Mockito.any(DeleteVpcRequest.class));			
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, cird);
		try {
			ec2NetworkPlugin.requestInstance(tokenDefault, null, xOCCIAtt);			
			Assert.fail();
		} catch (OCCIException e) {}
		
		Mockito.verify(createEC2Client, Mockito.times(1)).createVpc(
				Mockito.any(CreateVpcRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createSubnet(
				Mockito.any(CreateSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(2)).modifyVpcAttribute(
				Mockito.any(ModifyVpcAttributeRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createInternetGateway();
		Mockito.verify(createEC2Client, Mockito.times(1))
				.attachInternetGateway(Mockito.any(AttachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).createRoute(
				Mockito.any(CreateRouteRequest.class));
		
		// Removing instance because exception
		Mockito.verify(createEC2Client, Mockito.times(1)).describeSubnets();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).describeInternetGateways();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteInternetGateway(
				Mockito.any(DeleteInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).detachInternetGateway(
				Mockito.any(DetachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
		
	}
	
	@Test
	public void testRequestInstanceErrorWhenCreatingInternetGateway() {
		String subnetId = "subnetId00";
		String cird = "20.20.20.0/24";
		String vpcId = "vpcId00";
		
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// creating vpc
		CreateVpcRequest createVpcRequest = new CreateVpcRequest(cird);
		CreateVpcResult createVpcResult = new CreateVpcResult();
		Vpc vpc = new Vpc();
		vpc.setVpcId(vpcId);
		createVpcResult.setVpc(vpc);
		Mockito.when(createEC2Client.createVpc(createVpcRequest)).thenReturn(createVpcResult);

		// creating subnet id
		CreateSubnetRequest createSubnetRequest = new CreateSubnetRequest(vpcId, cird);
		CreateSubnetResult createSubnetResult = new CreateSubnetResult();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(subnetId);
		createSubnetResult.setSubnet(subnet);
		Mockito.when(createEC2Client.createSubnet(createSubnetRequest)).thenReturn(createSubnetResult);
		
		// creating internet gateway
		Mockito.when(createEC2Client.createInternetGateway()).thenThrow(
				new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		// Removing instance because exception
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		subnet = new Subnet();
		subnet.setVpcId(vpcId);
		subnet.setSubnetId(subnetId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		
		//ec2Client.describeInternetGateways() 
		DescribeInternetGatewaysResult describeInternetGatewaysResult = new DescribeInternetGatewaysResult();
		Collection<InternetGateway> internetGateways = new ArrayList<InternetGateway>();
		InternetGateway internetGateway = new InternetGateway();
		internetGateway.setInternetGatewayId("internetGatewayId");
		Collection<InternetGatewayAttachment> attachments = new ArrayList<InternetGatewayAttachment>();
		InternetGatewayAttachment internetGatewayAttachment = new InternetGatewayAttachment();
		internetGatewayAttachment.setVpcId(vpcId);
		attachments.add(internetGatewayAttachment);
		internetGateway.setAttachments(attachments);
		internetGateways.add(internetGateway);
		describeInternetGatewaysResult.setInternetGateways(internetGateways);
		Mockito.when(createEC2Client.describeInternetGateways()).thenReturn(describeInternetGatewaysResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteVpc(Mockito.any(DeleteVpcRequest.class));			
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, cird);
		try {
			ec2NetworkPlugin.requestInstance(tokenDefault, null, xOCCIAtt);
			Assert.fail();
		} catch (OCCIException e) {}		
		
		Mockito.verify(createEC2Client, Mockito.times(1)).createVpc(
				Mockito.any(CreateVpcRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createSubnet(
				Mockito.any(CreateSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(2)).modifyVpcAttribute(
				Mockito.any(ModifyVpcAttributeRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createInternetGateway();
		Mockito.verify(createEC2Client, Mockito.times(0))
				.attachInternetGateway(Mockito.any(AttachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).createRoute(
				Mockito.any(CreateRouteRequest.class));
		
		// Removing instance because exception
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}	
	
	@Test
	public void testRequestInstanceErrorWhenAttachingInternetGateway() {
		String subnetId = "subnetId00";
		String cird = "20.20.20.0/24";
		String vpcId = "vpcId00";
		
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// creating vpc
		CreateVpcRequest createVpcRequest = new CreateVpcRequest(cird);
		CreateVpcResult createVpcResult = new CreateVpcResult();
		Vpc vpc = new Vpc();
		vpc.setVpcId(vpcId);
		createVpcResult.setVpc(vpc);
		Mockito.when(createEC2Client.createVpc(createVpcRequest)).thenReturn(createVpcResult);

		// creating subnet id
		CreateSubnetRequest createSubnetRequest = new CreateSubnetRequest(vpcId, cird);
		CreateSubnetResult createSubnetResult = new CreateSubnetResult();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(subnetId);
		createSubnetResult.setSubnet(subnet);
		Mockito.when(createEC2Client.createSubnet(createSubnetRequest)).thenReturn(createSubnetResult);
		
		// creating internet gateway		
		CreateInternetGatewayResult createInternetGatewayResult = new CreateInternetGatewayResult();
		InternetGateway internetGateway = new InternetGateway();
		String internetGatewayId = "internetGateWayId00";
		internetGateway.setInternetGatewayId(internetGatewayId);
		createInternetGatewayResult.setInternetGateway(internetGateway);
		Mockito.when(createEC2Client.createInternetGateway()).thenReturn(createInternetGatewayResult);
		
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, "")).when(createEC2Client)
				.attachInternetGateway((Mockito.any(AttachInternetGatewayRequest.class)));	
		
		// Removing instance because exception
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		subnet = new Subnet();
		subnet.setVpcId(vpcId);
		subnet.setSubnetId(subnetId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		
		//ec2Client.describeInternetGateways() 
		DescribeInternetGatewaysResult describeInternetGatewaysResult = new DescribeInternetGatewaysResult();
		Collection<InternetGateway> internetGateways = new ArrayList<InternetGateway>();
		internetGateway = new InternetGateway();
		internetGateway.setInternetGatewayId("internetGatewayId");
		Collection<InternetGatewayAttachment> attachments = new ArrayList<InternetGatewayAttachment>();
		InternetGatewayAttachment internetGatewayAttachment = new InternetGatewayAttachment();
		internetGatewayAttachment.setVpcId(vpcId);
		attachments.add(internetGatewayAttachment);
		internetGateway.setAttachments(attachments);
		internetGateways.add(internetGateway);
		describeInternetGatewaysResult.setInternetGateways(internetGateways);
		Mockito.when(createEC2Client.describeInternetGateways()).thenReturn(describeInternetGatewaysResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteVpc(Mockito.any(DeleteVpcRequest.class));			
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, cird);
		try {
			ec2NetworkPlugin.requestInstance(tokenDefault, null, xOCCIAtt);
			Assert.fail();
		} catch (OCCIException e) {}		
		
		Mockito.verify(createEC2Client, Mockito.times(1)).createVpc(
				Mockito.any(CreateVpcRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createSubnet(
				Mockito.any(CreateSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(2)).modifyVpcAttribute(
				Mockito.any(ModifyVpcAttributeRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).createInternetGateway();
		Mockito.verify(createEC2Client, Mockito.times(1))
				.attachInternetGateway(Mockito.any(AttachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).createRoute(
				Mockito.any(CreateRouteRequest.class));
		
		// Removing instance because exception
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}	
	
	@Test
	public void testRequestInstanceErrorWhenCreatingVPC() {
		String cird = "20.20.20.0/24";
		
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		Mockito.when(createEC2Client.createVpc(Mockito.any(CreateVpcRequest.class)))
				.thenThrow(new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, cird);
		try {
			ec2NetworkPlugin.requestInstance(tokenDefault, null, xOCCIAtt);
			Assert.fail();
		} catch (OCCIException e) {}

		Mockito.verify(createEC2Client, Mockito.times(1)).createVpc(
				Mockito.any(CreateVpcRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).createSubnet(
				Mockito.any(CreateSubnetRequest.class));				
	}	
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceErrorWhenCreatingSubnet() {
		String subnetId = "subnetId00";
		String cird = "20.20.20.0/24";
		String vpcId = "vpcId00";
		
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// creating vpc
		CreateVpcRequest createVpcRequest = new CreateVpcRequest(cird);
		CreateVpcResult createVpcResult = new CreateVpcResult();
		Vpc vpc = new Vpc();
		vpc.setVpcId(vpcId);
		createVpcResult.setVpc(vpc);
		Mockito.when(createEC2Client.createVpc(createVpcRequest)).thenReturn(createVpcResult);

		// creating subnet id
		CreateSubnetRequest createSubnetRequest = new CreateSubnetRequest(vpcId, cird);
		Mockito.when(createEC2Client.createSubnet(createSubnetRequest)).thenThrow(
				new OCCIException(ErrorType.BAD_REQUEST, ""));
		
		// removing vpc
		DeleteVpcRequest deleteVpcRequest = new DeleteVpcRequest(vpcId);
		Mockito.doNothing().when(createEC2Client).deleteVpc(deleteVpcRequest);
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, cird);
		String instance = ec2NetworkPlugin.requestInstance(tokenDefault, null, xOCCIAtt);		

		Mockito.verify(createEC2Client, Mockito.times(1)).createVpc(createVpcRequest);
		Mockito.verify(createEC2Client, Mockito.times(1)).createSubnet(
				Mockito.any(CreateSubnetRequest.class));		
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteVpc(
				Mockito.any(DeleteVpcRequest.class));	
		Mockito.verify(createEC2Client, Mockito.times(0)).modifyVpcAttribute(
				Mockito.any(ModifyVpcAttributeRequest.class));
		
		Assert.assertEquals(subnetId, instance);
	}	
	
	@Test
	public void testGetInstance() {
		String instanceId = "instanceId00";
		String cidr = "10.10.10.10/20";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		DescribeSubnetsResult describeSubnetsResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		Subnet subnet = new Subnet();
		subnet.setCidrBlock(cidr);
		subnet.setSubnetId(instanceId);
		subnet.setState("available");
		subnets.add(subnet);
		describeSubnetsResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetsResult);
		
		Instance instance = ec2NetworkPlugin.getInstance(tokenDefault, instanceId);
		Assert.assertEquals(instanceId, instance.getId());
		Assert.assertEquals(cidr, instance.getAttributes().get(OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals(OCCIConstants.NetworkState.ACTIVE.getValue(),
				instance.getAttributes().get(OCCIConstants.NETWORK_INTERFACE_STATE));
		Assert.assertEquals("", instance.getAttributes().get(OCCIConstants.NETWORK_GATEWAY));
		Assert.assertEquals("", instance.getAttributes().get(OCCIConstants.NETWORK_INTERFACE_MAC));
		Assert.assertEquals("", instance.getAttributes().get(OCCIConstants.NETWORK_INTERFACE_INTERFACE));
		Assert.assertEquals(instanceId, instance.getAttributes().get(OCCIConstants.TITLE));
		Assert.assertEquals(instanceId, instance.getAttributes().get(OCCIConstants.ID));
	}
	
	@Test(expected=OCCIException.class)
	public void testGetInstanceNotFount() {
		String instanceId = "instanceId00";
		String cidr = "10.10.10.10/20";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		DescribeSubnetsResult describeSubnetsResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		Subnet subnet = new Subnet();
		subnet.setCidrBlock(cidr);
		subnet.setSubnetId(instanceId);
		subnet.setState("available");
		subnets.add(subnet);
		describeSubnetsResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetsResult);
		
		ec2NetworkPlugin.getInstance(tokenDefault, "WRONG");
	}	
	
	@Test
	public void testRemoveInstance() { 
		String instanceId = "subnetId00";
		String vpcId = "vpcId";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(instanceId);
		subnet.setVpcId(vpcId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		
		//ec2Client.describeInternetGateways() 
		DescribeInternetGatewaysResult describeInternetGatewaysResult = new DescribeInternetGatewaysResult();
		Collection<InternetGateway> internetGateways = new ArrayList<InternetGateway>();
		InternetGateway internetGateway = new InternetGateway();
		internetGateway.setInternetGatewayId("internetGatewayId");
		Collection<InternetGatewayAttachment> attachments = new ArrayList<InternetGatewayAttachment>();
		InternetGatewayAttachment internetGatewayAttachment = new InternetGatewayAttachment();
		internetGatewayAttachment.setVpcId(vpcId);
		attachments.add(internetGatewayAttachment);
		internetGateway.setAttachments(attachments);
		internetGateways.add(internetGateway);
		describeInternetGatewaysResult.setInternetGateways(internetGateways);
		Mockito.when(createEC2Client.describeInternetGateways()).thenReturn(describeInternetGatewaysResult);
		
		// deleting vpc
		Mockito.doNothing().when(createEC2Client).deleteVpc(Mockito.any(DeleteVpcRequest.class));		
		
		ec2NetworkPlugin.removeInstance(tokenDefault, instanceId);
		
		Mockito.verify(createEC2Client, Mockito.times(1)).describeSubnets();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).describeInternetGateways();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteInternetGateway(
				Mockito.any(DeleteInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).detachInternetGateway(
				Mockito.any(DetachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}	
	
	@Test
	public void testRemoveInstanceErrorWhileRemovingVPC() { 
		String instanceId = "subnetId00";
		String vpcId = "vpcId";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(instanceId);
		subnet.setVpcId(vpcId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		
		//ec2Client.describeInternetGateways() 
		DescribeInternetGatewaysResult describeInternetGatewaysResult = new DescribeInternetGatewaysResult();
		Collection<InternetGateway> internetGateways = new ArrayList<InternetGateway>();
		InternetGateway internetGateway = new InternetGateway();
		internetGateway.setInternetGatewayId("internetGatewayId");
		Collection<InternetGatewayAttachment> attachments = new ArrayList<InternetGatewayAttachment>();
		InternetGatewayAttachment internetGatewayAttachment = new InternetGatewayAttachment();
		internetGatewayAttachment.setVpcId(vpcId);
		attachments.add(internetGatewayAttachment);
		internetGateway.setAttachments(attachments);
		internetGateways.add(internetGateway);
		describeInternetGatewaysResult.setInternetGateways(internetGateways);
		Mockito.when(createEC2Client.describeInternetGateways()).thenReturn(describeInternetGatewaysResult);
		
		// deleting subnet - ERROR
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, ""))
				.when(createEC2Client)
				.deleteVpc(Mockito.any(DeleteVpcRequest.class));		
		
		try {
			ec2NetworkPlugin.removeInstance(tokenDefault, instanceId);
			Assert.fail();
		} catch (Exception e) {}
		
		Mockito.verify(createEC2Client, Mockito.times(1)).describeSubnets();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).describeInternetGateways();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteInternetGateway(
				Mockito.any(DeleteInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).detachInternetGateway(
				Mockito.any(DetachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}		
	
	@Test
	public void testRemoveInstanceErrorWhileRemovingInternetGateway() { 
		String instanceId = "subnetId00";
		String vpcId = "vpcId";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(instanceId);
		subnet.setVpcId(vpcId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		
		//ec2Client.describeInternetGateways() 
		DescribeInternetGatewaysResult describeInternetGatewaysResult = new DescribeInternetGatewaysResult();
		Collection<InternetGateway> internetGateways = new ArrayList<InternetGateway>();
		InternetGateway internetGateway = new InternetGateway();
		internetGateway.setInternetGatewayId("internetGatewayId");
		Collection<InternetGatewayAttachment> attachments = new ArrayList<InternetGatewayAttachment>();
		InternetGatewayAttachment internetGatewayAttachment = new InternetGatewayAttachment();
		internetGatewayAttachment.setVpcId(vpcId);
		attachments.add(internetGatewayAttachment);
		internetGateway.setAttachments(attachments);
		internetGateways.add(internetGateway);
		describeInternetGatewaysResult.setInternetGateways(internetGateways);
		Mockito.when(createEC2Client.describeInternetGateways()).thenReturn(describeInternetGatewaysResult);
	
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, "")).when(createEC2Client)
				.deleteInternetGateway(Mockito.any(DeleteInternetGatewayRequest.class));
		
		try {
			ec2NetworkPlugin.removeInstance(tokenDefault, instanceId);
			Assert.fail();
		} catch (Exception e) {}
		
		Mockito.verify(createEC2Client, Mockito.times(1)).describeSubnets();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).describeInternetGateways();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteInternetGateway(
				Mockito.any(DeleteInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).detachInternetGateway(
				Mockito.any(DetachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}		
	
	@Test
	public void testRemoveInstanceErrorWhileDetachInternetGateway() { 
		String instanceId = "subnetId00";
		String vpcId = "vpcId";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(instanceId);
		subnet.setVpcId(vpcId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		
		//ec2Client.describeInternetGateways() 
		DescribeInternetGatewaysResult describeInternetGatewaysResult = new DescribeInternetGatewaysResult();
		Collection<InternetGateway> internetGateways = new ArrayList<InternetGateway>();
		InternetGateway internetGateway = new InternetGateway();
		internetGateway.setInternetGatewayId("internetGatewayId");
		Collection<InternetGatewayAttachment> attachments = new ArrayList<InternetGatewayAttachment>();
		InternetGatewayAttachment internetGatewayAttachment = new InternetGatewayAttachment();
		internetGatewayAttachment.setVpcId(vpcId);
		attachments.add(internetGatewayAttachment);
		internetGateway.setAttachments(attachments);
		internetGateways.add(internetGateway);
		describeInternetGatewaysResult.setInternetGateways(internetGateways);
		Mockito.when(createEC2Client.describeInternetGateways()).thenReturn(describeInternetGatewaysResult);
	
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, "")).when(createEC2Client)
				.detachInternetGateway(Mockito.any(DetachInternetGatewayRequest.class));
		
		try {
			ec2NetworkPlugin.removeInstance(tokenDefault, instanceId);
			Assert.fail();
		} catch (Exception e) {}
		
		Mockito.verify(createEC2Client, Mockito.times(1)).describeSubnets();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).describeInternetGateways();
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteInternetGateway(
				Mockito.any(DeleteInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).detachInternetGateway(
				Mockito.any(DetachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}		
	
	@Test
	public void testRemoveInstanceErrorWhileDescribingInternetGateway() { 
		String instanceId = "subnetId00";
		String vpcId = "vpcId";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(instanceId);
		subnet.setVpcId(vpcId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doNothing().when(createEC2Client).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
	
		Mockito.when(createEC2Client.describeInternetGateways()).thenThrow(
				new OCCIException(ErrorType.BAD_REQUEST, ""));
	
		try {
			ec2NetworkPlugin.removeInstance(tokenDefault, instanceId);
			Assert.fail();
		} catch (Exception e) {}
		
		Mockito.verify(createEC2Client, Mockito.times(1)).describeSubnets();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(1)).describeInternetGateways();
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteInternetGateway(
				Mockito.any(DeleteInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).detachInternetGateway(
				Mockito.any(DetachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}		
	
	@Test
	public void testRemoveInstanceErrorWhileRemovingSubnet() { 
		String instanceId = "subnetId00";
		String vpcId = "vpcId";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// describing subnets
		DescribeSubnetsResult describeSubnetResult = new DescribeSubnetsResult();
		Collection<Subnet> subnets = new ArrayList<Subnet>();
		Subnet subnet = new Subnet();
		subnet.setSubnetId(instanceId);
		subnet.setVpcId(vpcId);
		subnets.add(subnet);
		describeSubnetResult.setSubnets(subnets);
		Mockito.when(createEC2Client.describeSubnets()).thenReturn(describeSubnetResult);
		
		// deleting subnet
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, ""))
				.when(createEC2Client)
				.deleteSubnet(Mockito.any(DeleteSubnetRequest.class));	
	
		try {
			ec2NetworkPlugin.removeInstance(tokenDefault, instanceId);
			Assert.fail();
		} catch (Exception e) {}
		
		Mockito.verify(createEC2Client, Mockito.times(1)).describeSubnets();
		Mockito.verify(createEC2Client, Mockito.times(1)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).describeInternetGateways();
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteInternetGateway(
				Mockito.any(DeleteInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).detachInternetGateway(
				Mockito.any(DetachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}	
	
	@Test
	public void testRemoveInstanceErrorWhileDescribingSubnets() { 
		String instanceId = "subnetId00";
		AmazonEC2Client createEC2Client = createEC2Client(ec2NetworkPlugin);
		
		// describing subnets
		Mockito.when(createEC2Client.describeSubnets()).thenThrow(
				new OCCIException(ErrorType.BAD_REQUEST, ""));
	
		try {
			ec2NetworkPlugin.removeInstance(tokenDefault, instanceId);
			Assert.fail();
		} catch (Exception e) {}
		
		Mockito.verify(createEC2Client, Mockito.times(1)).describeSubnets();
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteSubnet(Mockito.any(DeleteSubnetRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).describeInternetGateways();
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteInternetGateway(
				Mockito.any(DeleteInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).detachInternetGateway(
				Mockito.any(DetachInternetGatewayRequest.class));
		Mockito.verify(createEC2Client, Mockito.times(0)).deleteVpc(Mockito.any(DeleteVpcRequest.class));
	}		
	
	private AmazonEC2Client createEC2Client(EC2NetworkPlugin ec2NetworkPlugin) {
		AmazonEC2Client ec2Client = Mockito.mock(AmazonEC2Client.class);
		Mockito.doReturn(ec2Client).when(ec2NetworkPlugin).createEC2Client(Mockito.any(Token.class));
		return ec2Client;
	}
	
}
