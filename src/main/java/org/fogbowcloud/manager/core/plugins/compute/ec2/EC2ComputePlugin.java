package org.fogbowcloud.manager.core.plugins.compute.ec2;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.identity.ec2.EC2IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Request;
import org.restlet.Response;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
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
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.UserBucket;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.google.common.collect.ImmutableList;

public class EC2ComputePlugin implements ComputePlugin {

	private static final Logger LOGGER = Logger.getLogger(EC2ComputePlugin.class);
	
	private static final String FLAVORS_JSON_URL = "https://a0.awsstatic.com/pricing/1/deprecated/ec2/linux-od.json";
	protected static final int S3_PART_SIZE = 5 * 1024 * 1024;
	
	private static final String STATE_STOPPED = "stopped";
	private static final String STATE_RUNNING = "running";
	private static final String STATE_TERMINATED = "terminated";
	
	protected static final String FOGBOW_INSTANCE_TAG = "fogbow-instance";
	private static final String DEFAULT_SSH_HOST_PORT = "22";
	
	private Map<String, Flavor> flavors;
	
	private String region;
	private String securityGroupId;
	private String subnetId;
	private String imageBucketName;
	
	private int maxVCPU;
	private int maxRAM;
	private int maxInstances;

	private HttpClientWrapper httpClient;

	public EC2ComputePlugin(Properties properties) {
		this(properties, new HttpClientWrapper());
	}
	
	protected EC2ComputePlugin(Properties properties, HttpClientWrapper httpClient) {
		this.httpClient = httpClient;
		this.region = properties.getProperty("compute_ec2_region");
		this.securityGroupId = properties.getProperty("compute_ec2_security_group_id");
		this.subnetId = properties.getProperty("compute_ec2_subnet_id");
		this.imageBucketName = properties.getProperty("compute_ec2_image_bucket_name");
		
		String maxCPUStr = properties.getProperty("compute_ec2_max_vcpu");
		if (maxCPUStr == null) {
			LOGGER.error("Property compute_ec2_max_vcpu must be set.");
			throw new IllegalArgumentException("Property compute_ec2_max_vcpu must be set.");
		}
		this.maxVCPU = Integer.parseInt(maxCPUStr);
		
		String maxRAMStr = properties.getProperty("compute_ec2_max_ram");
		if (maxRAMStr == null) {
			LOGGER.error("Property compute_ec2_max_ram must be set.");
			throw new IllegalArgumentException("Property compute_ec2_max_ram must be set.");
		}
		this.maxRAM = Integer.parseInt(maxRAMStr);
		
		String maxInstancesStr = properties.getProperty("compute_ec2_max_instances");
		if (maxInstancesStr == null) {
			LOGGER.error("Property compute_ec2_max_instances must be set.");
			throw new IllegalArgumentException("Property compute_ec2_max_instances must be set.");
		}
		this.maxInstances = Integer.parseInt(maxInstancesStr);
	}
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		
		if (imageId == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		ResourcesInfo resourcesInfo = getResourcesInfo(token);
		if (Integer.parseInt(resourcesInfo.getInstancesIdle()) == 0) {
			throw new OCCIException(ErrorType.QUOTA_EXCEEDED, 
					ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
		}
		
		Flavor flavor = RequirementsHelper.findSmallestFlavor(
				new LinkedList<Flavor>(getFlavors().values()), 
				xOCCIAtt.get(OrderAttribute.REQUIREMENTS.getValue()));
		
		if (Integer.parseInt(resourcesInfo.getCpuIdle()) < Integer.parseInt(flavor.getCpu()) || 
				Integer.parseInt(resourcesInfo.getMemIdle()) < Integer.parseInt(flavor.getMem())) {
			throw new OCCIException(ErrorType.QUOTA_EXCEEDED, 
					ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
		}
		
		AmazonEC2Client ec2Client = createEC2Client(token);
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		
		runInstancesRequest.withImageId(imageId)
				.withInstanceType(flavor.getName())
				.withMinCount(1)
				.withMaxCount(1);
		
		if (subnetId != null || securityGroupId != null) {
			InstanceNetworkInterfaceSpecification networkSpec = 
					new InstanceNetworkInterfaceSpecification();
			networkSpec.withDeviceIndex(0);
			if (subnetId != null) {
				networkSpec.withSubnetId(subnetId);
			}
			if (securityGroupId != null) {
				networkSpec.withGroups(securityGroupId);
			}
			runInstancesRequest.withNetworkInterfaces(networkSpec);
		}
				
		String userData = xOCCIAtt.get(OrderAttribute.USER_DATA_ATT.getValue());
		if (userData != null) {
			runInstancesRequest.withUserData(userData);
		}
		
		RunInstancesResult runInstancesResult = null;
		try {
			runInstancesResult = ec2Client.runInstances(runInstancesRequest);
		} catch (Exception e) {
			LOGGER.error("Couldn't start EC2 instance.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		Reservation reservation = runInstancesResult.getReservation();
		String instanceId = reservation.getInstances().get(0).getInstanceId();
		
		CreateTagsRequest createTagsRequest = new CreateTagsRequest();
		createTagsRequest.withResources(instanceId).withTags(
				new Tag(FOGBOW_INSTANCE_TAG, Boolean.TRUE.toString()));
		ec2Client.createTags(createTagsRequest);
		
		return instanceId;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		return getInstances(token, false);
	}
	
	public List<Instance> getInstances(Token token, boolean fullInfo) {
		AmazonEC2Client ec2Client = createEC2Client(token);
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
				.withFilters(new Filter("tag-key", ImmutableList.of(FOGBOW_INSTANCE_TAG)));
		DescribeInstancesResult describeInstancesResult = null;
		try {
			describeInstancesResult = ec2Client.describeInstances(describeInstancesRequest);
		} catch (Exception e) {
			LOGGER.error("Couldn't describe EC2 instances.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		List<Reservation> reservations = describeInstancesResult.getReservations();
		List<Instance> instances = new LinkedList<Instance>();
		for (Reservation reservation : reservations) {
			List<com.amazonaws.services.ec2.model.Instance> ec2Instances = reservation.getInstances();
			for (com.amazonaws.services.ec2.model.Instance ec2Instance : ec2Instances) {
				if (ec2Instance.getState().getName().equals(STATE_TERMINATED)) {
					continue;
				}
				String instanceId = ec2Instance.getInstanceId();
				instances.add(fullInfo ? convertInstance(ec2Instance) : new Instance(instanceId));
			}
		}
		return instances;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		checkFogbowTag(token, instanceId);
		AmazonEC2Client ec2Client = createEC2Client(token);
		DescribeInstancesRequest describeInstanceStatusRequest = new DescribeInstancesRequest()
				.withInstanceIds(ImmutableList.of(instanceId));
		
		DescribeInstancesResult describeInstancesResult = null;
		try {
			describeInstancesResult = ec2Client.describeInstances(describeInstanceStatusRequest);
		} catch (Exception e) {
			LOGGER.error("Couldn't describe EC2 instance.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		List<Reservation> reservations = describeInstancesResult.getReservations();
		for (Reservation reservation : reservations) {
			for (com.amazonaws.services.ec2.model.Instance ec2Instance : reservation.getInstances()) {
				if (ec2Instance.getState().getName().equals(STATE_TERMINATED)) {
					continue;
				}
				return convertInstance(ec2Instance);
			}
		}
		
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		checkFogbowTag(token, instanceId);
		AmazonEC2Client ec2Client = createEC2Client(token);
		TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest(
				ImmutableList.of(instanceId));
		try {
			ec2Client.terminateInstances(terminateInstancesRequest);
		} catch (Exception e) {
			LOGGER.error("Couldn't remove EC2 instance.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public void removeInstances(Token token) {
		List<Instance> instances = getInstances(token);
		List<String> ids = new LinkedList<String>();
		for (Instance instance : instances) {
			ids.add(instance.getId());
		}
		AmazonEC2Client ec2Client = createEC2Client(token);
		TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest(ids);
		try {
			ec2Client.terminateInstances(terminateInstancesRequest);
		} catch (Exception e) {
			LOGGER.error("Couldn't remove EC2 instances.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		List<Instance> instances = getInstances(token, true);
		int cpuInUse = 0;
		int ramInUse = 0;
		for (Instance instance : instances) {
			Map<String, String> attributes = instance.getAttributes();
			String memoryStr = attributes.get("occi.compute.memory");
			ramInUse += Integer.parseInt(memoryStr) * 1024;
			String coresStr = attributes.get("occi.compute.cores");
			cpuInUse += Integer.parseInt(coresStr);
		}
		
		return new ResourcesInfo(
				String.valueOf(maxVCPU - cpuInUse), 
				String.valueOf(cpuInUse), 
				String.valueOf(maxRAM - ramInUse), 
				String.valueOf(ramInUse), 
				String.valueOf(maxInstances - instances.size()), 
				String.valueOf(instances.size()));
	}

	@Override
	public void bypass(Request request, Response response) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName,
			String diskFormat) {
		
		AmazonS3Client s3Client = createS3Client(token);
		try {
			uploadToS3(new File(imagePath), s3Client, imageBucketName, imageName);
		} catch (Exception e) {
			LOGGER.error("Couldn't upload file to S3.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_STATE);
		}
		
		AmazonEC2Client ec2Client = createEC2Client(token);
		
		UserBucket userBucket = new UserBucket()
				.withS3Bucket(imageBucketName)
				.withS3Key(imageName);
		ImageDiskContainer imageDiskContainer = new ImageDiskContainer()
				.withDescription(imageName)
				.withFormat(diskFormat)
				.withUserBucket(userBucket);
		ImportImageRequest importImageRequest = new ImportImageRequest()
				.withDiskContainers(imageDiskContainer)
				.withDescription(imageName);
		
		ec2Client.importImage(importImageRequest);
		
		try {
			s3Client.deleteObject(imageBucketName, imageName);
		} catch (Exception e) {
			LOGGER.warn("Couldn't cleanup image from S3 bucket after importing it to EC2.", e);
		}
	}

	private void uploadToS3(File file, AmazonS3Client s3Client, 
			String bucketName, String keyName) throws Exception {
		List<PartETag> partETags = new ArrayList<PartETag>();
		InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(
				bucketName, keyName);
		InitiateMultipartUploadResult initResponse = s3Client
				.initiateMultipartUpload(initRequest);

		long contentLength = file.length();
		long partSize = S3_PART_SIZE;
		try {
			long filePosition = 0;
			for (int i = 1; filePosition < contentLength; i++) {
				partSize = Math.min(partSize, (contentLength - filePosition));

				UploadPartRequest uploadRequest = new UploadPartRequest()
						.withBucketName(bucketName).withKey(keyName)
						.withUploadId(initResponse.getUploadId())
						.withPartNumber(i).withFileOffset(filePosition)
						.withFile(file).withPartSize(partSize);

				partETags.add(s3Client.uploadPart(uploadRequest).getPartETag());

				filePosition += partSize;
			}
			CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(
					bucketName, keyName, initResponse.getUploadId(),
					partETags);

			s3Client.completeMultipartUpload(compRequest);
		} catch (Exception e) {
			s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(
					bucketName, keyName, initResponse.getUploadId()));
			throw e;
		}
	}
	
	@Override
	public String getImageId(Token token, String imageName) {
		AmazonEC2Client ec2Client = createEC2Client(token);
		DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withFilters(
				new Filter("description", ImmutableList.of(imageName)));
		DescribeImagesResult describeImagesResult = null;
		try {
			describeImagesResult = ec2Client.describeImages(describeImagesRequest);
		} catch (Exception e) {
			LOGGER.error("Couldn't describe images on description.", e);
			return null;
		}
		List<Image> images = describeImagesResult.getImages();
		if (images.isEmpty()) {
			return null;
		}
		return images.get(0).getImageId();
	}

	@Override
	public ImageState getImageState(Token token, String imageName) {
		AmazonEC2Client ec2Client = createEC2Client(token);
		DescribeImportImageTasksRequest describeImportImageTasksRequest = new DescribeImportImageTasksRequest();
		DescribeImportImageTasksResult imageTasksResult = 
				ec2Client.describeImportImageTasks(describeImportImageTasksRequest);
		
		ImportImageTask imageTask = null;
		List<ImportImageTask> imageTasks = imageTasksResult.getImportImageTasks();
		for (ImportImageTask importImageTask : imageTasks) {
			if (importImageTask.getDescription().equals(imageName)) {
				imageTask = importImageTask;
				break;
			}
		}
		
		if (imageTask == null) {
			return ImageState.ACTIVE;
		}
		if (imageTask.getStatus().equals("active")) {
			return ImageState.PENDING;
		}
		if (imageTask.getStatus().equals("completed")) {
			return ImageState.ACTIVE;
		}
		return ImageState.FAILED;
	}
	
	protected AmazonEC2Client createEC2Client(Token token) {
		BasicAWSCredentials awsCreds = loadCredentials(token);
		AmazonEC2Client ec2Client = new AmazonEC2Client(awsCreds);
		ec2Client.setRegion(Region.getRegion(Regions.fromName(region)));
		return ec2Client;
	}
	
	protected AmazonS3Client createS3Client(Token token) {
		BasicAWSCredentials awsCreds = loadCredentials(token);
		AmazonS3Client s3Client = new AmazonS3Client(awsCreds);
		s3Client.setRegion(Region.getRegion(Regions.fromName(region)));
		return s3Client;
	}

	private BasicAWSCredentials loadCredentials(Token token) {
		String accessKey = token.get(EC2IdentityPlugin.CRED_ACCESS_KEY);
		String secretKey = token.get(EC2IdentityPlugin.CRED_SECRET_KEY);
		
		if (accessKey == null || secretKey == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
		}
		
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
		return awsCreds;
	}

	private Instance convertInstance(
			com.amazonaws.services.ec2.model.Instance ec2Instance) {
		String iid = ec2Instance.getInstanceId();
		InstanceState state = getInstanceState(ec2Instance.getState().getName());
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("occi.compute.state", state.getOcciState());
		attributes.put("occi.compute.speed", "Not defined");
		attributes.put("occi.compute.architecture", ec2Instance.getArchitecture());
		
		Flavor flavor = getFlavors().get(ec2Instance.getInstanceType());
		
		attributes.put("occi.compute.memory", String.valueOf(
				Integer.parseInt(flavor.getMem()) / 1024));
		attributes.put("occi.compute.cores", flavor.getCpu());

		attributes.put("occi.compute.hostname", ec2Instance.getPrivateDnsName());
		attributes.put("occi.core.id", iid);
		
		attributes.put(Instance.SSH_PUBLIC_ADDRESS_ATT, 
				ec2Instance.getPublicIpAddress() + ":" + DEFAULT_SSH_HOST_PORT);
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.generateFlavorResource(flavor.getName()));
		
		return new Instance(iid, resources, attributes, new ArrayList<Instance.Link>(), state);
	}

	private InstanceState getInstanceState(String instanceStatus) {
		if (STATE_RUNNING.equalsIgnoreCase(instanceStatus)) {
			return InstanceState.RUNNING;
		}
		if (STATE_STOPPED.equalsIgnoreCase(instanceStatus)) {
			return InstanceState.SUSPENDED;
		}
		return InstanceState.PENDING;
	}
	
	private Map<String, Flavor> getFlavors() {
		
		if (this.flavors != null) {
			return flavors;
		}
		
		HttpResponseWrapper response = getHttpClient().doGet(FLAVORS_JSON_URL);
		JSONObject content = null;
		try {
			content = new JSONObject(response.getContent());
		} catch (JSONException e) {
			LOGGER.error("Couldn't retrieve flavours from AWS.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_STATE);
		}
		
		JSONArray regionsAr = content.optJSONObject("config").optJSONArray("regions");
		JSONObject regionJson = null;
		for (int i = 0; i < regionsAr.length(); i++) {
			JSONObject curRegionJson = regionsAr.optJSONObject(i);
			if (curRegionJson.optString("region").equals(region)) {
				regionJson = curRegionJson;
				break;
			}
		}
		
		Map<String, Flavor> flavors = new HashMap<String, Flavor>();
		JSONArray instanceTypes = regionJson.optJSONArray("instanceTypes");
		for (int i = 0; i < instanceTypes.length(); i++) {
			JSONArray sizes = instanceTypes.optJSONObject(i).optJSONArray("sizes");
			for (int j = 0; j < sizes.length(); j++) {
				JSONObject size = sizes.optJSONObject(j);
				Flavor flavor = new Flavor(size.optString("size"), 
						size.optString("vCPU"), 
						String.valueOf(size.optInt("memoryGiB") * 1024), 
						storageToGB(size.optString("storageGB")));
				flavors.put(size.optString("size"), flavor);
			}
		}
		
		this.flavors = flavors;
		return flavors;
	}
	
	private void checkFogbowTag(Token token, String instanceId) {
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			if (instance.getId().equals(instanceId)) {
				return;
			}
		}
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}
	
	private static String storageToGB(String storageStr) {
		if (storageStr.equals("ebsonly")) {
			return Integer.valueOf(0).toString();
		}
		String[] storageStrSplit = storageStr.split(" ");
		Integer totalStorage = 1;
		for (String storageStrPart : storageStrSplit) {
			try {
				totalStorage *= Integer.parseInt(storageStrPart);
			} catch (NumberFormatException e) {
				// Expected when dealing with storage type, e.g. SSD
			}
		}
		return totalStorage.toString();
	}
	
	public HttpClientWrapper getHttpClient() {
		return httpClient;
	}

	@Override
	public String attach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void dettach(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		// TODO Auto-generated method stub
		
	}
	
}
