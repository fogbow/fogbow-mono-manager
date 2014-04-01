package org.fogbowcloud.manager.occi;

import org.apache.http.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class Request extends ServerResource {

	public static final String TERM_FOGBOW_REQUEST = "fogbow-request";
	public static final String SCHEME_FOGBOW_REQUEST = "http://schemas.fogbowcloud.org/request#";
	public static final String ATRIBUTE_INSTANCE_FOGBOW_REQUEST = "org.fogbowcloud.request.instance";
	public static final String ATRIBUTE_TYPE_FOGBOW_REQUEST = "org.fogbowcloud.request.type";
	public static final String ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST = "org.fogbowcloud.request.valid-until";
	public static final String ATRIBUTE_VALID_FROM_FOGBOW_REQUEST = "org.fogbowcloud.request.valid-from";
	
	@Get
    public String fetch() {
		//HttpRequest req = (HttpRequest) getRequest();
		//Header xAttr = req.getHeaders().getFirst(normalize("Category"));
		return "X-OCCI-RequestId: REQUESNASAKJBADJHDJABHCXJHBDSJHBshabxjabhscjhabcjhba"; 
    }

	@Post
    public String post() {
		HttpRequest req = (HttpRequest) getRequest();
		String listCAtegory ;
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
