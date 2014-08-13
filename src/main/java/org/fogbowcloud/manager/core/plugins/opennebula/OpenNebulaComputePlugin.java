package org.fogbowcloud.manager.core.plugins.opennebula;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.restlet.Request;
import org.restlet.Response;

public class OpenNebulaComputePlugin implements ComputePlugin {

	private Properties properties;

	public OpenNebulaComputePlugin(Properties properties){
		
	}
	
	@Override
	public String requestInstance(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Instance> getInstances(String authToken) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Instance getInstance(String authToken, String instanceId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void removeInstance(String authToken, String instanceId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeInstances(String authToken) {
		// TODO Auto-generated method stub

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

}
