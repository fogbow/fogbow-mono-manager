package org.fogbowcloud.manager.core.plugins.compute.ec2;

import java.io.File;
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
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeImportImageTasksRequest;
import com.amazonaws.services.ec2.model.DescribeImportImageTasksResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.ImageDiskContainer;
import com.amazonaws.services.ec2.model.ImportImageRequest;
import com.amazonaws.services.ec2.model.ImportImageTask;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
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
	
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceImageIdNUll() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		computePlugin.requestInstance(createToken(), new LinkedList<Category>(), 
				new HashMap<String, String>(), null);
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceNoInstanceIdle() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		
		Token token = createToken();
		Mockito.doReturn(new ResourcesInfo("1", "1", 
				"512", "512", "0", "0")).when(computePlugin).getResourcesInfo(token);
		
		computePlugin.requestInstance(token, new LinkedList<Category>(), 
				new HashMap<String, String>(), "image");
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceNotEnoughvCPU() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		
		Token token = createToken();
		Mockito.doReturn(new ResourcesInfo("0", "1", 
				"1024", "1024", "1", "1")).when(computePlugin).getResourcesInfo(token);
		
		computePlugin.requestInstance(token, new LinkedList<Category>(), 
				new HashMap<String, String>(), "image");
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceNotEnoughRAM() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		
		Token token = createToken();
		Mockito.doReturn(new ResourcesInfo("1", "1", 
				"1024", "0", "1", "1")).when(computePlugin).getResourcesInfo(token);
		
		computePlugin.requestInstance(token, new LinkedList<Category>(), 
				new HashMap<String, String>(), "image");
	}
	
	@Test
	public void testRequestInstance() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		Token token = createToken();
		Mockito.doReturn(new ResourcesInfo("1", "1", 
				"1024", "1024", "1", "1")).when(computePlugin).getResourcesInfo(token);
		
		Reservation reservation = new Reservation();
		com.amazonaws.services.ec2.model.Instance instance = 
				new com.amazonaws.services.ec2.model.Instance();
		instance.setInstanceId("instanceId");
		reservation.withInstances(ImmutableList.of(instance));
		RunInstancesResult runInstancesResult = new RunInstancesResult();
		runInstancesResult.withReservation(reservation);
		
		Mockito.doReturn(runInstancesResult).when(ec2Client).runInstances(Mockito.argThat(
				new ArgumentMatcher<RunInstancesRequest>() {
					@Override
					public boolean matches(Object argument) {
						RunInstancesRequest requestArg = (RunInstancesRequest) argument;
								return requestArg.getInstanceType().equals("t2.micro")
										&& requestArg.getImageId().equals("image");
					}
		}));

		final String instanceId = computePlugin.requestInstance(token, new LinkedList<Category>(), 
				new HashMap<String, String>(), "image");

		Mockito.verify(ec2Client).createTags(Mockito.argThat(
				new ArgumentMatcher<CreateTagsRequest>() {
					@Override
					public boolean matches(Object argument) {
						CreateTagsRequest requestArg = (CreateTagsRequest) argument;
								return requestArg.getResources().get(0).equals(instanceId)
										&& requestArg.getTags().get(0).getKey().equals(
												EC2ComputePlugin.FOGBOW_INSTANCE_TAG);
					}
		}));
		
		Assert.assertEquals("instanceId", instanceId);
	}
	
	@Test
	public void testRequestInstanceWithUserDataAndSecGroup() {
		Map<String, String> extraProps = new HashMap<String, String>();
		extraProps.put("compute_ec2_security_group_id", "sg-123");
		
		EC2ComputePlugin computePlugin = createEC2ComputePlugin(extraProps);
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		Token token = createToken();
		Mockito.doReturn(new ResourcesInfo("1", "1", 
				"1024", "1024", "1", "1")).when(computePlugin).getResourcesInfo(token);
		
		Reservation reservation = new Reservation();
		com.amazonaws.services.ec2.model.Instance instance = 
				new com.amazonaws.services.ec2.model.Instance();
		instance.setInstanceId("instanceId");
		reservation.withInstances(ImmutableList.of(instance));
		RunInstancesResult runInstancesResult = new RunInstancesResult();
		runInstancesResult.withReservation(reservation);
		
		Mockito.doReturn(runInstancesResult).when(ec2Client).runInstances(Mockito.argThat(
				new ArgumentMatcher<RunInstancesRequest>() {
					@Override
					public boolean matches(Object argument) {
						RunInstancesRequest requestArg = (RunInstancesRequest) argument;
								return requestArg.getInstanceType().equals("t2.micro")
										&& requestArg.getImageId().equals("image")
										&& requestArg.getUserData().equals("userData")
										&& requestArg.getNetworkInterfaces().get(0).getGroups().get(0).equals("sg-123");
					}
		}));

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.USER_DATA_ATT.getValue(), "userData");
		final String instanceId = computePlugin.requestInstance(token, new LinkedList<Category>(), 
				xOCCIAtt, "image");
		
		Assert.assertEquals("instanceId", instanceId);
	}
	
	@Test
	public void testRequestInstanceWithUserDataAndSubnetId() {
		Map<String, String> extraProps = new HashMap<String, String>();
		extraProps.put("compute_ec2_subnet_id", "net-123");
		
		EC2ComputePlugin computePlugin = createEC2ComputePlugin(extraProps);
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		Token token = createToken();
		Mockito.doReturn(new ResourcesInfo("1", "1", 
				"1024", "1024", "1", "1")).when(computePlugin).getResourcesInfo(token);
		
		Reservation reservation = new Reservation();
		com.amazonaws.services.ec2.model.Instance instance = 
				new com.amazonaws.services.ec2.model.Instance();
		instance.setInstanceId("instanceId");
		reservation.withInstances(ImmutableList.of(instance));
		RunInstancesResult runInstancesResult = new RunInstancesResult();
		runInstancesResult.withReservation(reservation);
		
		Mockito.doReturn(runInstancesResult).when(ec2Client).runInstances(Mockito.argThat(
				new ArgumentMatcher<RunInstancesRequest>() {
					@Override
					public boolean matches(Object argument) {
						RunInstancesRequest requestArg = (RunInstancesRequest) argument;
								return requestArg.getInstanceType().equals("t2.micro")
										&& requestArg.getImageId().equals("image")
										&& requestArg.getUserData().equals("userData")
										&& requestArg.getNetworkInterfaces().get(0).getSubnetId().equals("net-123");
					}
		}));

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.USER_DATA_ATT.getValue(), "userData");
		final String instanceId = computePlugin.requestInstance(token, new LinkedList<Category>(), 
				xOCCIAtt, "image");
		
		Assert.assertEquals("instanceId", instanceId);
	}
	
	@Test
	public void testGetImageId() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		DescribeImagesResult describeImagesResult = new DescribeImagesResult().withImages(
				new Image().withImageId("ami-1234567"));
		Mockito.doReturn(describeImagesResult).when(ec2Client).describeImages(
				Mockito.any(DescribeImagesRequest.class));
		
		String imageId = computePlugin.getImageId(createToken(), "ubuntu");
		
		Assert.assertEquals("ami-1234567", imageId);
		
		DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withFilters(
				new Filter("description", ImmutableList.of("ubuntu")));
		Mockito.verify(ec2Client).describeImages(Mockito.eq(describeImagesRequest));
	}
	
	@Test
	public void testGetImageIdWithFailure() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		Mockito.doThrow(new AmazonServiceException(null)).when(ec2Client).describeImages(
				Mockito.any(DescribeImagesRequest.class));
		
		String imageId = computePlugin.getImageId(createToken(), "ubuntu");
		
		Assert.assertNull(imageId);
		
		DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withFilters(
				new Filter("description", ImmutableList.of("ubuntu")));
		Mockito.verify(ec2Client).describeImages(Mockito.eq(describeImagesRequest));
	}
	
	@Test
	public void testGetImageIdNoImages() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		DescribeImagesResult describeImagesResult = new DescribeImagesResult().withImages();
		Mockito.doReturn(describeImagesResult).when(ec2Client).describeImages(
				Mockito.any(DescribeImagesRequest.class));
		
		String imageId = computePlugin.getImageId(createToken(), "ubuntu");
		
		Assert.assertNull(imageId);
		
		DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withFilters(
				new Filter("description", ImmutableList.of("ubuntu")));
		Mockito.verify(ec2Client).describeImages(Mockito.eq(describeImagesRequest));
	}
	
	@Test
	public void testGetImageStateNoUploadTask() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		DescribeImportImageTasksResult importTasksResult = 
				new DescribeImportImageTasksResult().withImportImageTasks();
		
		Mockito.doReturn(importTasksResult).when(ec2Client).describeImportImageTasks(
				Mockito.any(DescribeImportImageTasksRequest.class));
		
		ImageState imageState = computePlugin.getImageState(createToken(), "ubuntu");
		
		Assert.assertEquals(ImageState.ACTIVE, imageState);
		
		DescribeImportImageTasksRequest describeImportTasks = new DescribeImportImageTasksRequest();
		Mockito.verify(ec2Client).describeImportImageTasks(Mockito.eq(describeImportTasks));
	}
	
	@Test
	public void testGetImageStateUploadTaskWithDifferentName() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		DescribeImportImageTasksResult importTasksResult = 
				new DescribeImportImageTasksResult().withImportImageTasks(
						new ImportImageTask().withDescription("non-ubuntu"));
		
		Mockito.doReturn(importTasksResult).when(ec2Client).describeImportImageTasks(
				Mockito.any(DescribeImportImageTasksRequest.class));
		
		ImageState imageState = computePlugin.getImageState(createToken(), "ubuntu");
		
		Assert.assertEquals(ImageState.ACTIVE, imageState);
		
		DescribeImportImageTasksRequest describeImportTasks = new DescribeImportImageTasksRequest();
		Mockito.verify(ec2Client).describeImportImageTasks(Mockito.eq(describeImportTasks));
	}

	@Test
	public void testGetImageStateUploadTaskWithActiveTask() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		DescribeImportImageTasksResult importTasksResult = 
				new DescribeImportImageTasksResult().withImportImageTasks(
						new ImportImageTask()
							.withDescription("ubuntu")
							.withStatus("active"));
		
		Mockito.doReturn(importTasksResult).when(ec2Client).describeImportImageTasks(
				Mockito.any(DescribeImportImageTasksRequest.class));
		
		ImageState imageState = computePlugin.getImageState(createToken(), "ubuntu");
		
		Assert.assertEquals(ImageState.PENDING, imageState);
		
		DescribeImportImageTasksRequest describeImportTasks = new DescribeImportImageTasksRequest();
		Mockito.verify(ec2Client).describeImportImageTasks(Mockito.eq(describeImportTasks));
	}
	
	@Test
	public void testGetImageStateUploadTaskWithCompletedTask() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		DescribeImportImageTasksResult importTasksResult = 
				new DescribeImportImageTasksResult().withImportImageTasks(
						new ImportImageTask()
							.withDescription("ubuntu")
							.withStatus("completed"));
		
		Mockito.doReturn(importTasksResult).when(ec2Client).describeImportImageTasks(
				Mockito.any(DescribeImportImageTasksRequest.class));
		
		ImageState imageState = computePlugin.getImageState(createToken(), "ubuntu");
		
		Assert.assertEquals(ImageState.ACTIVE, imageState);
		
		DescribeImportImageTasksRequest describeImportTasks = new DescribeImportImageTasksRequest();
		Mockito.verify(ec2Client).describeImportImageTasks(Mockito.eq(describeImportTasks));
	}
	
	@Test
	public void testGetImageStateUploadTaskInUnknownState() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		DescribeImportImageTasksResult importTasksResult = 
				new DescribeImportImageTasksResult().withImportImageTasks(
						new ImportImageTask()
							.withDescription("ubuntu")
							.withStatus("unknown"));
		
		Mockito.doReturn(importTasksResult).when(ec2Client).describeImportImageTasks(
				Mockito.any(DescribeImportImageTasksRequest.class));
		
		ImageState imageState = computePlugin.getImageState(createToken(), "ubuntu");
		
		Assert.assertEquals(ImageState.FAILED, imageState);
		
		DescribeImportImageTasksRequest describeImportTasks = new DescribeImportImageTasksRequest();
		Mockito.verify(ec2Client).describeImportImageTasks(Mockito.eq(describeImportTasks));
	}
	
	@Test(expected=AmazonServiceException.class)
	public void testGetImageStateWithFailure() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		
		Mockito.doThrow(new AmazonServiceException(null)).when(ec2Client).describeImportImageTasks(
				Mockito.any(DescribeImportImageTasksRequest.class));
		
		computePlugin.getImageState(createToken(), "ubuntu");
	}
	
	@Test
	public void testUploadImage() {
		EC2ComputePlugin computePlugin = createEC2ComputePlugin();
		AmazonEC2Client ec2Client = createEC2Client(computePlugin);
		AmazonS3Client s3Client = createS3Client(computePlugin);
		
		final String bucketName = "image_bucket_name";
		final String keyName = "ubuntu";
		final String uploadId = "upload-id";
		File uploadingFile = new File("src/test/resources/ec2/file-to-be-uploaded");
		
		InitiateMultipartUploadResult initResponse = new InitiateMultipartUploadResult();
		initResponse.setUploadId(uploadId);
		Mockito.doReturn(initResponse).when(s3Client).initiateMultipartUpload(Mockito.argThat(
				new ArgumentMatcher<InitiateMultipartUploadRequest>() {
					@Override
					public boolean matches(Object argument) {
						InitiateMultipartUploadRequest requestArg = (InitiateMultipartUploadRequest) argument;
						return requestArg.getBucketName().equals(bucketName) 
								&& requestArg.getKey().equals(keyName);
					}
		}));
		
		UploadPartRequest uploadRequest1 = new UploadPartRequest()
				.withBucketName(bucketName).withKey(keyName)
				.withUploadId(initResponse.getUploadId())
				.withPartNumber(1).withFileOffset(0)
				.withFile(uploadingFile).withPartSize(EC2ComputePlugin.S3_PART_SIZE);
		
		UploadPartRequest uploadRequest2 = new UploadPartRequest()
				.withBucketName(bucketName).withKey(keyName)
				.withUploadId(initResponse.getUploadId())
				.withPartNumber(2).withFileOffset(EC2ComputePlugin.S3_PART_SIZE)
				.withFile(uploadingFile).withPartSize(1024);
		
		Mockito.doReturn(new UploadPartResult()).when(s3Client).uploadPart(
				Mockito.argThat(createUploadRequestMatcher(uploadRequest1)));
		Mockito.doReturn(new UploadPartResult()).when(s3Client).uploadPart(
				Mockito.argThat(createUploadRequestMatcher(uploadRequest2)));
		
		Mockito.doReturn(new CompleteMultipartUploadResult()).when(s3Client).completeMultipartUpload(Mockito.argThat(
				new ArgumentMatcher<CompleteMultipartUploadRequest>() {
					@Override
					public boolean matches(Object argument) {
						CompleteMultipartUploadRequest requestArg = (CompleteMultipartUploadRequest) argument;
						return requestArg.getUploadId().equals(uploadId);
					}
		}));
		
		final String diskFormat = "vpc";
		computePlugin.uploadImage(createToken(), 
				uploadingFile.getAbsolutePath(), keyName, diskFormat);
		
		Mockito.verify(ec2Client).importImage(Mockito.argThat(
				new ArgumentMatcher<ImportImageRequest>() {
			@Override
			public boolean matches(Object argument) {
				ImportImageRequest requestArg = (ImportImageRequest) argument;
				ImageDiskContainer diskContainer = requestArg.getDiskContainers().get(0);
				return requestArg.getDescription().equals(keyName) 
						&& diskContainer.getDescription().equals(keyName)
						&& diskContainer.getFormat().equals(diskFormat)
						&& diskContainer.getUserBucket().getS3Bucket().equals(bucketName)
						&& diskContainer.getUserBucket().getS3Key().equals(keyName);
			}
		}));
		
		Mockito.verify(s3Client).deleteObject(
				Mockito.eq(bucketName), Mockito.eq(keyName));
	}
	
	private ArgumentMatcher<UploadPartRequest> createUploadRequestMatcher(
			final UploadPartRequest req) {
		return new ArgumentMatcher<UploadPartRequest>() {
			@Override
			public boolean matches(Object argument) {
				UploadPartRequest requestArg = (UploadPartRequest) argument;
				return requestArg.getBucketName().equals(req.getBucketName())
						&& requestArg.getKey().equals(req.getKey())
						&& requestArg.getPartNumber() == req.getPartNumber()
						&& requestArg.getFileOffset() == req.getFileOffset()
						&& requestArg.getPartSize() == req.getPartSize();
			}
		};
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
	
	private AmazonS3Client createS3Client(EC2ComputePlugin computePlugin) {
		AmazonS3Client s3Client = Mockito.mock(AmazonS3Client.class);
		Mockito.doReturn(s3Client).when(
				computePlugin).createS3Client(Mockito.any(Token.class));
		return s3Client;
	}
	
	private static final ProtocolVersion PROTO = new ProtocolVersion("HTTP", 1, 1);
	
	private EC2ComputePlugin createEC2ComputePlugin() {
		return createEC2ComputePlugin(new HashMap<String, String>());
	}
	
	private EC2ComputePlugin createEC2ComputePlugin(Map<String, String> extraProps) {
		Properties properties = new Properties();
		properties.setProperty("compute_ec2_region", "us-east-1");
		properties.setProperty("compute_ec2_max_vcpu", "2");
		properties.setProperty("compute_ec2_max_ram", "2048");
		properties.setProperty("compute_ec2_max_instances", "2");
		properties.setProperty("compute_ec2_image_bucket_name", "image_bucket_name");
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
		
		EC2ComputePlugin computePlugin = Mockito.spy(new EC2ComputePlugin(properties));
		Mockito.doReturn(clientWrapper).when(computePlugin).getHttpClient();
		
		return computePlugin;
	}
}
