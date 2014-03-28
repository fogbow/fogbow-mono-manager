package org.fogbowcloud.manager.occi;

import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class Request extends ServerResource {

	@Get
    public String fetch() {
		HttpRequest req = (HttpRequest) getRequest();
		Header xAttr = req.getHeaders().getFirst(normalize("X-OCCI-Attribute"));
        return xAttr.getValue();
    }

	@Post
    public String post() {
        return "";
    }
	
	private static String normalize(String headerName) {
		String lowerHeader = headerName.toLowerCase();
		char[] lowerHeaderArray = lowerHeader.toCharArray();
		lowerHeaderArray[0] = Character.toUpperCase(
				lowerHeaderArray[0]);
		return new String(lowerHeaderArray);
	}
	
}
