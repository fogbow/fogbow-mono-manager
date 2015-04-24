package org.fogbowcloud.manager.core.plugins.compute;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.restlet.Request;
import org.restlet.Response;

public class NoCloudComputePlugin implements ComputePlugin {

	private static final String ZERO = Integer.toString(0);
	private static final String FAKE_IMAGE_ID = "no-image";
	
	public NoCloudComputePlugin(Properties properties) {}
	
	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		throw new OCCIException(ErrorType.QUOTA_EXCEEDED, "There is no underlying cloud.");
	}

	@Override
	public List<Instance> getInstances(Token token) {
		return new ArrayList<Instance>();
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		
	}

	@Override
	public void removeInstances(Token token) {
		
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		return new ResourcesInfo(ZERO, ZERO, ZERO, ZERO, 
				new LinkedList<Flavor>());
	}

	@Override
	public void bypass(Request request, Response response) {
		
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName,
			String diskFormat) {
		
	}

	@Override
	public String getImageId(Token token, String imageName) {
		return FAKE_IMAGE_ID;
	}

	@Override
	public ImageState getImageState(Token token, String imageName) {
		return ImageState.ACTIVE;
	}

}
