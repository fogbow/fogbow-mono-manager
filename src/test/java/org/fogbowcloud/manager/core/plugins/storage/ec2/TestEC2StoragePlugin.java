package org.fogbowcloud.manager.core.plugins.storage.ec2;

import java.io.FileInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.ec2.model.VolumeState;
import com.google.common.collect.ImmutableList;

public class TestEC2StoragePlugin {
	private static final String FAKE_INSTANCE_ID_1 = "vol-eab2bf46";
	private static final String FAKE_INSTANCE_ID_2 = "vol-bf46eab2";
	
	@Test
	public void testRequestInstance() {
		EC2StoragePlugin storagePlugin = createEC2StoragePlugin();
		AmazonEC2Client ec2Client = createEC2Client(storagePlugin);
		
		Token token = createToken();
		
		Volume volume = new Volume();
		volume.setVolumeId("volumeId");
		CreateVolumeResult createVolumeResult = new CreateVolumeResult();
		createVolumeResult.withVolume(volume);
		
		Mockito.doReturn(createVolumeResult).when(ec2Client).createVolume(Mockito.argThat(
			new ArgumentMatcher<CreateVolumeRequest>() {
				@Override
				public boolean matches(Object argument) {
					CreateVolumeRequest requestArg = (CreateVolumeRequest) argument;
							return requestArg.getAvailabilityZone().equals("us-east-1b")
									&& requestArg.getSize().equals(1);
				}
		}));

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), "1");
		String volumeId = storagePlugin.requestInstance(token, 
				new LinkedList<Category>(), xOCCIAtt);

		Assert.assertEquals("volumeId", volumeId);
	}
	
	@Test
	public void testGetInstances() {
		EC2StoragePlugin storagePlugin = createEC2StoragePlugin();
		AmazonEC2Client ec2Client = createEC2Client(storagePlugin);
		Token token = createToken();
		
		Volume volume1 = new Volume();
		volume1.withVolumeId(FAKE_INSTANCE_ID_1);
		volume1.withState(VolumeState.Available);
		volume1.withSize(1);
		
		Volume volume2 = new Volume();
		volume2.withVolumeId(FAKE_INSTANCE_ID_2);
		volume2.withState(VolumeState.Available);
		volume2.withSize(1);
		
		DescribeVolumesResult describeVolumesResult = new DescribeVolumesResult();
		describeVolumesResult.withVolumes(ImmutableList.of(volume1, volume2));
		Mockito.doReturn(describeVolumesResult).when(ec2Client).describeVolumes();
		
		List<Instance> instances = storagePlugin.getInstances(token);
		Assert.assertEquals(2, instances.size());
	}
	
	@Test
	public void testGetInstance() {
		EC2StoragePlugin storagePlugin = createEC2StoragePlugin();
		AmazonEC2Client ec2Client = createEC2Client(storagePlugin);
		Token token = createToken();
		
		Volume volume1 = new Volume();
		volume1.withVolumeId(FAKE_INSTANCE_ID_1);
		volume1.withState(VolumeState.Available);
		volume1.withSize(1);
		
		Volume volume2 = new Volume();
		volume2.withVolumeId(FAKE_INSTANCE_ID_2);
		volume2.withState(VolumeState.Available);
		volume2.withSize(1);
		
		DescribeVolumesResult describeVolumesResult = new DescribeVolumesResult();
		describeVolumesResult.withVolumes(ImmutableList.of(volume1, volume2));
		Mockito.doReturn(describeVolumesResult).when(ec2Client).describeVolumes();
		
		Instance instance1 = storagePlugin.getInstance(token, FAKE_INSTANCE_ID_1);
		Assert.assertEquals(FAKE_INSTANCE_ID_1, instance1.getId());
		
		Instance instance2 = storagePlugin.getInstance(token, FAKE_INSTANCE_ID_2);
		Assert.assertEquals(FAKE_INSTANCE_ID_2, instance2.getId());
	}
	
	@Test
	public void testGetInstanceWithInexistentId() {
		EC2StoragePlugin storagePlugin = createEC2StoragePlugin();
		AmazonEC2Client ec2Client = createEC2Client(storagePlugin);
		Token token = createToken();
		
		Volume volume1 = new Volume();
		volume1.withVolumeId(FAKE_INSTANCE_ID_1);
		volume1.withState(VolumeState.Available);
		volume1.withSize(1);
		
		Volume volume2 = new Volume();
		volume2.withVolumeId(FAKE_INSTANCE_ID_2);
		volume2.withState(VolumeState.Available);
		volume2.withSize(1);
		
		DescribeVolumesResult describeVolumesResult = new DescribeVolumesResult();
		describeVolumesResult.withVolumes(ImmutableList.of(volume1, volume2));
		Mockito.doReturn(describeVolumesResult).when(ec2Client).describeVolumes();
		
		Instance instance = storagePlugin.getInstance(token, "invalidId");
		Assert.assertNull(instance);
	}
	
	@Test(expected = OCCIException.class)
	public void testRemoveInstanceWithInexistentId() {
		EC2StoragePlugin storagePlugin = createEC2StoragePlugin();
		AmazonEC2Client ec2Client = createEC2Client(storagePlugin);
		Token token = createToken();
		
		Volume volume1 = new Volume();
		volume1.withVolumeId(FAKE_INSTANCE_ID_1);
		volume1.withState(VolumeState.Available);
		volume1.withSize(1);
		
		Volume volume2 = new Volume();
		volume2.withVolumeId(FAKE_INSTANCE_ID_2);
		volume2.withState(VolumeState.Available);
		volume2.withSize(1);
		
		DescribeVolumesResult describeVolumesResult = new DescribeVolumesResult();
		describeVolumesResult.withVolumes(ImmutableList.of(volume1, volume2));
		Mockito.doReturn(describeVolumesResult).when(ec2Client).describeVolumes();
		
		storagePlugin.removeInstance(token, "invalidId");
	}
	
	@Test
	public void testRemoveInstance() {
		EC2StoragePlugin storagePlugin = createEC2StoragePlugin();
		AmazonEC2Client ec2Client = createEC2Client(storagePlugin);
		Token token = createToken();
		
		Volume volume1 = new Volume();
		volume1.withVolumeId(FAKE_INSTANCE_ID_1);
		volume1.withState(VolumeState.Available);
		volume1.withSize(1);
		
		Volume volume2 = new Volume();
		volume2.withVolumeId(FAKE_INSTANCE_ID_2);
		volume2.withState(VolumeState.Available);
		volume2.withSize(1);
		
		DescribeVolumesResult describeVolumesResult = new DescribeVolumesResult();
		describeVolumesResult.withVolumes(ImmutableList.of(volume1, volume2));
		Mockito.doReturn(describeVolumesResult).when(ec2Client).describeVolumes();
		
		storagePlugin.removeInstance(token, FAKE_INSTANCE_ID_1);
		Mockito.verify(ec2Client).deleteVolume(Mockito.any(DeleteVolumeRequest.class));
	}
	
	@Test
	public void testRemoveInstances() {
		EC2StoragePlugin storagePlugin = createEC2StoragePlugin();
		AmazonEC2Client ec2Client = createEC2Client(storagePlugin);
		Token token = createToken();
		
		Volume volume1 = new Volume();
		volume1.withVolumeId(FAKE_INSTANCE_ID_1);
		volume1.withState(VolumeState.Available);
		volume1.withSize(1);
		
		Volume volume2 = new Volume();
		volume2.withVolumeId(FAKE_INSTANCE_ID_2);
		volume2.withState(VolumeState.Available);
		volume2.withSize(1);
		
		DescribeVolumesResult describeVolumesResult = new DescribeVolumesResult();
		describeVolumesResult.withVolumes(ImmutableList.of(volume1, volume2));
		Mockito.doReturn(describeVolumesResult).when(ec2Client).describeVolumes();
		
		storagePlugin.removeInstances(token);
		Mockito.verify(ec2Client, new Times(2)).deleteVolume(Mockito.any(DeleteVolumeRequest.class));
	}
	
	private AmazonEC2Client createEC2Client(EC2StoragePlugin storagePlugin) {
		AmazonEC2Client ec2Client = Mockito.mock(AmazonEC2Client.class);
		Mockito.doReturn(ec2Client).when(
				storagePlugin).createEC2Client(Mockito.any(Token.class));
		return ec2Client;
	}
	
	private Token createToken() {
		Token token = new Token("AccessKey:AccessSecret", "AccessKey", 
				new Date(), new HashMap<String, String>());
		return token;
	}
	
	private static final ProtocolVersion PROTO = new ProtocolVersion("HTTP", 1, 1);
	
	private EC2StoragePlugin createEC2StoragePlugin() {
		return createEC2StoragePlugin(new HashMap<String, String>());
	}
	
	private EC2StoragePlugin createEC2StoragePlugin(Map<String, String> extraProps) {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_region", "us-east-1");
		properties.setProperty("storage_ec2_availability_zone", "us-east-1b");
		for (Entry<String, String> entry : extraProps.entrySet()) {
			properties.setProperty(entry.getKey(), entry.getValue());
		}
		
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
		
		EC2StoragePlugin storagePlugin = Mockito.spy(new EC2StoragePlugin(properties));
		Mockito.doReturn(clientWrapper).when(storagePlugin).getHttpClient();
		
		return storagePlugin;
	}
	
}
