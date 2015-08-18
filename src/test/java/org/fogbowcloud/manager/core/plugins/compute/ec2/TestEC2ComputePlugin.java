package org.fogbowcloud.manager.core.plugins.compute.ec2;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.identity.ec2.EC2IdentityPlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Test;

public class TestEC2ComputePlugin {

	@Test
	public void testGetInstances() {
		Properties properties = new Properties();
		EC2IdentityPlugin ec2IdentityPlugin = new EC2IdentityPlugin(properties);
		Token token = ec2IdentityPlugin.getToken("AccessId:SecretKey");
		
		EC2ComputePlugin computePlugin = new EC2ComputePlugin(properties);
		List<Instance> instances = computePlugin.getInstances(token);
		for (Instance instance : instances) {
			System.out.println(instance.getId());
		}
	}
	
	@Test
	public void testGetInstance() {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_region", "us-east-1");
		EC2IdentityPlugin ec2IdentityPlugin = new EC2IdentityPlugin(properties);
		Token token = ec2IdentityPlugin.getToken("AccessId:SecretKey");
		
		EC2ComputePlugin computePlugin = new EC2ComputePlugin(properties);
		Instance instance = computePlugin.getInstance(token, "i-e99e2105");
		System.out.println(instance.getId());
	}
	
	@Test
	public void testRequestInstance() {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_region", "us-east-1");
		properties.setProperty("compute_ec2_max_vcpu", "1");
		properties.setProperty("compute_ec2_max_ram", "1");
		properties.setProperty("compute_ec2_max_instances", "1");
		properties.setProperty("compute_ec2_security_group_id", "sg-078f6663");
		properties.setProperty("compute_ec2_subnet_id", "subnet-6cd40d35");
		
		EC2IdentityPlugin ec2IdentityPlugin = new EC2IdentityPlugin(properties);
		Token token = ec2IdentityPlugin.getToken("AccessId:SecretKey");
		
		EC2ComputePlugin computePlugin = new EC2ComputePlugin(properties);
		String instanceId = computePlugin.requestInstance(
				token, new LinkedList<Category>(), new HashMap<String, String>(), 
				"ami-d05e75b8");
		System.out.println(instanceId);
	}
	
}
