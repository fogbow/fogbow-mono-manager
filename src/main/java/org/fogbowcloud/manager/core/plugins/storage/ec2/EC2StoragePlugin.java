package org.fogbowcloud.manager.core.plugins.storage.ec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.plugins.identity.ec2.EC2IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
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

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DeleteVolumeRequest;
import com.amazonaws.services.ec2.model.DescribeVolumesResult;
import com.amazonaws.services.ec2.model.Volume;

public class EC2StoragePlugin implements StoragePlugin {
	private static final Logger LOGGER = Logger.getLogger(EC2StoragePlugin.class);
	public static final String DEFAULT_ATTACHMENT_DEVICE = "/dev/sdh";
	
	private HttpClientWrapper httpClient;
	private String region;
	private String availabilityZone;

	public EC2StoragePlugin(Properties properties) {
		this(properties, new HttpClientWrapper());
	}

	public EC2StoragePlugin(Properties properties,
			HttpClientWrapper httpClient) {
		this.httpClient = httpClient;
		this.region = properties.getProperty("compute_ec2_region");
		this.availabilityZone = properties.getProperty("storage_ec2_availability_zone");
	}
	
	protected AmazonEC2Client createEC2Client(Token token) {
		LOGGER.debug("Creating EC2 client with token: " + token);
		BasicAWSCredentials awsCreds = loadCredentials(token);
		AmazonEC2Client ec2Client = new AmazonEC2Client(awsCreds);
		ec2Client.setRegion(Region.getRegion(Regions.fromName(region)));
		return ec2Client;
	}
	
	private BasicAWSCredentials loadCredentials(Token token) {
		String accessKey = token.get(EC2IdentityPlugin.CRED_ACCESS_KEY);
		String secretKey = token.get(EC2IdentityPlugin.CRED_SECRET_KEY);
		
		if (accessKey == null || secretKey == null) {
			LOGGER.debug("Invalid EC2 token.");
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.INVALID_TOKEN);
		}
		
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
		return awsCreds;
	}

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		int size = Integer.parseInt(xOCCIAtt.get(OrderAttribute.STORAGE_SIZE.getValue()));
		LOGGER.debug("Requesting EBS volume with size: " + size);
		CreateVolumeRequest createVolumeRequest = new CreateVolumeRequest();
		createVolumeRequest.withSize(size)
			.withAvailabilityZone(this.availabilityZone);
		AmazonEC2Client ec2Client = createEC2Client(token);
		CreateVolumeResult createVolumeResult = null;
		try {
			createVolumeResult = ec2Client.createVolume(createVolumeRequest);
		} catch (Exception e) {
			LOGGER.debug("Could not create EBS volume.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		return createVolumeResult.getVolume().getVolumeId();
	}

	@Override
	public List<Instance> getInstances(Token token) {
		LOGGER.debug("Getting all EBS instances with token: " + token);
		AmazonEC2Client ec2Client = createEC2Client(token);
		
		DescribeVolumesResult describeVolumesResult = null;
		try {
			describeVolumesResult = ec2Client.describeVolumes();
		} catch (Exception e) {
			LOGGER.debug("Could not get list of EBS volumes.", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
		
		List<Volume> volumes = describeVolumesResult.getVolumes();
		List<Instance> instances = new ArrayList<Instance>();
		for (Volume volume : volumes) {
			instances.add(createInstance(volume));
		}
		return instances;
	}

	private Instance createInstance(Volume volume) {
		String id = volume.getVolumeId();
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get(OrderConstants.STORAGE_TERM));
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put("occi.storage.name", id);
		attributes.put("occi.storage.status", volume.getState());
		attributes.put("occi.storage.size", volume.getSize().toString());
		attributes.put("occi.core.id", id);
		
		return new Instance(id, resources, attributes, new ArrayList<Instance.Link>(), null);
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		LOGGER.debug("Getting information about EBS instance id: " + instanceId);
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			if (instance.getId().equals(instanceId)) {
				return instance;
			}
		}
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		LOGGER.debug("Removing EBS instance with id " + instanceId);
		AmazonEC2Client ec2Client = createEC2Client(token);
		
		Instance instance = getInstance(token, instanceId);
		if (instance == null) {
			LOGGER.debug("The EBS instance " + instanceId + " does not exists.");
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND_INSTANCE);
		}
		
		DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest();
		deleteVolumeRequest.withVolumeId(instanceId);
		try {
			ec2Client.deleteVolume(deleteVolumeRequest);
		} catch ( Exception e ) {
			LOGGER.debug("Could not delete EBS volume " + instanceId, e);
			throw new OCCIException(ErrorType.BAD_REQUEST, 
					ResponseConstants.IRREGULAR_SYNTAX);
		}
	}

	@Override
	public void removeInstances(Token token) {
		LOGGER.debug("Removing all EBS instances with token: " + token);
		List<Instance> instances = getInstances(token);
		for (Instance instance : instances) {
			removeInstance(token, instance.getId());
		}
	}
	
	public HttpClientWrapper getHttpClient() {
		return httpClient;
	}

}
