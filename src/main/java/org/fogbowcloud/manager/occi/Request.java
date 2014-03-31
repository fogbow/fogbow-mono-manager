package org.fogbowcloud.manager.occi;

import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class Request extends ServerResource {

	@Get
    public String fetch() {
		//HttpRequest req = (HttpRequest) getRequest();
		//Header xAttr = req.getHeaders().getFirst(normalize("Category"));
		return "X-OCCI-RequestId: REQUESNASAKJBADJHDJABHCXJHBDSJHBshabxjabhscjhabcjhba"; 
    }

	@Post
    public String post() {
		//HttpRequest req = (HttpRequest) getRequest();		
		//Header xAttr = req.getHeaders().getFirst(normalize("Category"));
		return "X-OCCI-RequestId: REQUESNASAKJBADJHDJABHCXJHBDSJHBshabxjabhscjhabcjhba"; 
    }
	
	@Delete
	public String remove(){
		return null;
	}
	
	private static String normalize(String headerName) {
		String lowerHeader = headerName.toLowerCase();
		char[] lowerHeaderArray = lowerHeader.toCharArray();
		lowerHeaderArray[0] = Character.toUpperCase(
				lowerHeaderArray[0]);
		return new String(lowerHeaderArray);
	}
}
