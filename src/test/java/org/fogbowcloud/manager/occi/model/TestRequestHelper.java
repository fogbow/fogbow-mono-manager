package org.fogbowcloud.manager.occi.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.junit.After;
import org.junit.Before;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class TestRequestHelper {
	
	private Component component;

	public static final String ACCESS_TOKEN = "xxxxxxxxxxxxxxxxxxxxxxxxxxx";
	public static final String CONTENT_TYPE_OCCI = "text/occi";
	public static final String URI_FOGBOW_REQUEST = "http://localhost:8182/request";
	private final int PORT_ENDPOINT = 8182;
	
	public void inicializeComponent() throws Exception{
		component = new Component();
		component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);
		component.getDefaultHost().attach(new OCCIApplication());
		component.start();		
	}
	
	public void stopComponent() throws Exception{
		component.stop();
	}

	public static List<String> getRequestIds(HttpResponse response) throws ParseException, IOException {
		String responseStr = EntityUtils.toString(response.getEntity(), "utf-8");

		String[] tokens = responseStr.split("X-OCCI-RequestId:");
		List<String> requestIds = new ArrayList<String>();

		for (int i = 0; i < tokens.length; i++) {
			if (!tokens[i].equals("")) {
				requestIds.add(tokens[i]);
			}
		}

		return requestIds;
	}		
	
}
