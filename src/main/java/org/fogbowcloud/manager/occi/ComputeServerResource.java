package org.fogbowcloud.manager.occi;

import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class ComputeServerResource extends ServerResource{

	@Get
	public String fetch() {		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = HeaderUtils.getAuthToken(req.getHeaders());
		String idVM = (String) getRequestAttributes().get("vmid");

		if (idVM == null) {
			//TODO implemet 
//			return application.getAllInstacesVM;			
		}
		//TODO implemet 
//		return application.getSpecificInstanceVm(idVM);
		
		return null;
	}
	
	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = HeaderUtils.getAuthToken(req.getHeaders());
		String idVM = (String) getRequestAttributes().get("vmid");

		if (idVM == null) {
			//TODO implemet 
//			return application.removeAllInstacesVM;			
		}
		//TODO implemet 
//		return application.removeSpecificInstanceVm(idVM);
		
		return null;
	}
	
	@Post
	public String post() {
		return null;
	}
}
