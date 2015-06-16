package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.restlet.Request;
import org.restlet.Response;

public class CloudStackComputePlugin implements ComputePlugin {

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		return null;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		return null;
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void bypass(Request request, Response response) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void uploadImage(Token token, String imagePath, String imageName,
			String diskFormat) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getImageId(Token token, String imageName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ImageState getImageState(Token token, String imageName) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public static void main(String[] args) {
		
	}

}
