package org.fogbowcloud.manager.core.plugins.compute.nocloud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.restlet.Request;
import org.restlet.Response;

public class NoCloudComputePlugin implements ComputePlugin {

	protected static final String ZERO = Integer.toString(0);
	protected static final String FAKE_IMAGE_ID = "no-image";
	
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
		return new ResourcesInfo(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
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
