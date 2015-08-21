package org.fogbowcloud.manager.core.plugins.compute.ec2;

import java.io.FileInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.google.common.collect.ImmutableList;

public class TestEC2ComputePlugin {

	private static final String FAKE_INSTANCE_ID_1 = "i-eab2bf46";
	private static final String FAKE_INSTANCE_ID_2 = "i-bf46eab2";

	@Test(expected=IllegalArgumentException.class)
	public void testConstructorWithoutMaxVCPU() {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_max_ram", "1024");
		properties.setProperty("compute_ec2_max_instances", "1");
		new EC2ComputePlugin(properties);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testConstructorWithoutMaxRAM() {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_max_vcpu", "1");
		properties.setProperty("compute_ec2_max_instances", "1");
		new EC2ComputePlugin(properties);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testConstructorWithoutMaxInstances() {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_max_vcpu", "1");
		properties.setProperty("compute_ec2_max_ram", "1024");
		new EC2ComputePlugin(properties);
	}
	
	@Test
	public void testRemoveInstance() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1);
		
		computePlugin.removeInstance(createToken(), FAKE_INSTANCE_ID_1);
		
		TerminateInstancesRequest terminateInstancesRequest = 
				new TerminateInstancesRequest(ImmutableList.of(FAKE_INSTANCE_ID_1));
		Mockito.verify(ec2Client).terminateInstances(Mockito.eq(terminateInstancesRequest));
	}

	@Test(expected=OCCIException.class)
	public void testRemoveInstanceWithFailure() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1);
		
		TerminateInstancesRequest terminateInstancesRequest = 
				new TerminateInstancesRequest(ImmutableList.of(FAKE_INSTANCE_ID_1));
		Mockito.doThrow(new AmazonServiceException(null)).when(ec2Client).terminateInstances(
				Mockito.eq(terminateInstancesRequest));
		
		computePlugin.removeInstance(createToken(), FAKE_INSTANCE_ID_1);
	}

	@Test
	public void testRemoveInstances() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1, FAKE_INSTANCE_ID_2);
		
		computePlugin.removeInstances(createToken());
		
		TerminateInstancesRequest terminateInstancesRequest = 
				new TerminateInstancesRequest(ImmutableList.of(
						FAKE_INSTANCE_ID_1, FAKE_INSTANCE_ID_2));
		Mockito.verify(ec2Client).terminateInstances(Mockito.eq(terminateInstancesRequest));
	}
	
	@Test(expected=OCCIException.class)
	public void testRemoveInstancesWithFailure() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1, FAKE_INSTANCE_ID_2);
		
		TerminateInstancesRequest terminateInstancesRequest = 
				new TerminateInstancesRequest(ImmutableList.of(
						FAKE_INSTANCE_ID_1, FAKE_INSTANCE_ID_2));
		Mockito.doThrow(new AmazonServiceException(null)).when(ec2Client).terminateInstances(
				Mockito.eq(terminateInstancesRequest));
		
		computePlugin.removeInstances(createToken());
	}
	
	@Test
	public void testGetResourcesInfoPartiallyUsed() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1);
		
		ResourcesInfo resourcesInfo = computePlugin.getResourcesInfo(createToken());
		Assert.assertEquals("1", resourcesInfo.getCpuIdle());
		Assert.assertEquals("1", resourcesInfo.getCpuInUse());
		Assert.assertEquals("1024", resourcesInfo.getMemIdle());
		Assert.assertEquals("1024", resourcesInfo.getMemInUse());
		Assert.assertEquals("1", resourcesInfo.getInstancesIdle());
		Assert.assertEquals("1", resourcesInfo.getInstancesInUse());
	}
	
	@Test
	public void testGetResourcesInfoFullyUsed() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1, FAKE_INSTANCE_ID_2);
		
		ResourcesInfo resourcesInfo = computePlugin.getResourcesInfo(createToken());
		Assert.assertEquals("0", resourcesInfo.getCpuIdle());
		Assert.assertEquals("2", resourcesInfo.getCpuInUse());
		Assert.assertEquals("0", resourcesInfo.getMemIdle());
		Assert.assertEquals("2048", resourcesInfo.getMemInUse());
		Assert.assertEquals("0", resourcesInfo.getInstancesIdle());
		Assert.assertEquals("2", resourcesInfo.getInstancesInUse());
	}
	
	@Test(expected=OCCIException.class)
	public void testGetResourcesInfoWithFailure() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		Mockito.doThrow(new AmazonServiceException(null)).when(ec2Client)
				.describeInstances(Mockito.any(DescribeInstancesRequest.class));
		
		computePlugin.getResourcesInfo(createToken());
	}
	
	@Test
	public void testGetInstance() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1);
		
		Instance instance = computePlugin.getInstance(createToken(), FAKE_INSTANCE_ID_1);
		Assert.assertEquals(FAKE_INSTANCE_ID_1, instance.getId());
		Assert.assertEquals(org.fogbowcloud.manager.occi.instance.InstanceState.RUNNING, instance.getState());
		Assert.assertEquals("1", instance.getAttributes().get("occi.compute.memory"));
		Assert.assertEquals("1", instance.getAttributes().get("occi.compute.cores"));
	}
	
	@Test(expected=OCCIException.class)
	public void testGetInstanceNotFound() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1);
		
		computePlugin.getInstance(createToken(), FAKE_INSTANCE_ID_2);
	}
	
	@Test(expected=OCCIException.class)
	public void testGetInstanceWithFailure() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		Mockito.doThrow(new AmazonServiceException(null)).when(ec2Client)
				.describeInstances(Mockito.any(DescribeInstancesRequest.class));
		
		computePlugin.getInstance(createToken(), FAKE_INSTANCE_ID_1);
	}
	
	@Test
	public void testGetInstancesNoInstance() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client);
		
		List<Instance> instances = computePlugin.getInstances(createToken());
		Assert.assertTrue(instances.isEmpty());
	}
	
	@Test
	public void testGetInstanceMultipleInstances() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		recordInstanceTags(ec2Client, FAKE_INSTANCE_ID_1, FAKE_INSTANCE_ID_2);
		
		List<Instance> instances = computePlugin.getInstances(createToken());
		Assert.assertEquals(2, instances.size());
		Assert.assertEquals(FAKE_INSTANCE_ID_1, instances.get(0).getId());
		Assert.assertEquals(FAKE_INSTANCE_ID_2, instances.get(1).getId());
	}
	
	@Test(expected=OCCIException.class)
	public void testGetInstancesWithFailure() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		Mockito.doThrow(new AmazonServiceException(null)).when(ec2Client)
				.describeInstances(Mockito.any(DescribeInstancesRequest.class));
		
		computePlugin.getInstances(createToken());
	}

	private Token createToken() {
		Token token = new Token("AccessKey:AccessSecret", "AccessKey", 
				new Date(), new HashMap<String, String>());
		return token;
	}
	
	private void recordInstanceTags(AmazonEC2Client ec2Client, String... instanceIds) {
		DescribeInstancesResult describeInstancesResult = new DescribeInstancesResult();
		List<Reservation> reservations = new LinkedList<Reservation>();
		for (String instanceId : instanceIds) {
			Reservation reservation = new Reservation();
			
			List<com.amazonaws.services.ec2.model.Instance> instances = new LinkedList<com.amazonaws.services.ec2.model.Instance>();
			com.amazonaws.services.ec2.model.Instance instance = new com.amazonaws.services.ec2.model.Instance();
			instance.setInstanceId(instanceId);
			instance.setTags(ImmutableList.of(new Tag()
					.withKey(EC2ComputePlugin.FOGBOW_INSTANCE_TAG)
					.withValue(Boolean.TRUE.toString())));
			instance.setState(new InstanceState().withName("running"));
			instance.setInstanceType("t2.micro");
			instances.add(instance);
			
			reservation.setInstances(instances);
			reservations.add(reservation);
		}
		describeInstancesResult.setReservations(reservations);
		Mockito.doReturn(describeInstancesResult).when(ec2Client)
				.describeInstances(Mockito.any(DescribeInstancesRequest.class));
	}

	private AmazonEC2Client createEC2Client(EC2ComputePlugin computePlugin) {
		AmazonEC2Client ec2Client = Mockito.mock(AmazonEC2Client.class);
		Mockito.doReturn(ec2Client).when(
				computePlugin).createEC2Client(Mockito.any(Token.class));
		return ec2Client;
	}
	
	private static final ProtocolVersion PROTO = new ProtocolVersion("HTTP", 1, 1);
	
	private EC2ComputePlugin createEC2ComputePlugin() {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_region", "us-east-1");
		properties.setProperty("compute_ec2_max_vcpu", "2");
		properties.setProperty("compute_ec2_max_ram", "2048");
		properties.setProperty("compute_ec2_max_instances", "2");
		
		HttpClientWrapper clientWrapper = Mockito.mock(HttpClientWrapper.class);
		String jdFlavors = null;
		try {
			jdFlavors = IOUtils.toString(
					new FileInputStream("src/test/resources/ec2/linux-od.json"));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		HttpResponseWrapper response = new HttpResponseWrapper
				(new BasicStatusLine(PROTO, 200, "test reason"), jdFlavors);
		
		Mockito.doReturn(response).when(clientWrapper).doGet(Mockito.anyString());
		
		EC2ComputePlugin computePlugin = Mockito.spy(new EC2ComputePlugin(properties));
		return computePlugin;
	}
	
	
}
