package org.fogbowcloud.manager.occi.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.Request;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.mockito.Mockito;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class TestRequestHelper {

	private Component component;

	public static final String ACCESS_TOKEN = "HgjhgYUDFTGBgrbelihBDFGBÇuyrb";
	public static final String CONTENT_TYPE_OCCI = "text/occi";
	public static final String URI_FOGBOW_REQUEST = "http://localhost:8182/request";
	public static final String UTF_8 = "utf-8";

	private final int PORT_ENDPOINT = 8182;

	public void initializeComponent(ComputePlugin computePlugin, IdentityPlugin identityPlugin) throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);
		
		OCCIApplication application = new OCCIApplication();
		
		application.setComputePlugin(computePlugin);		
		application.setIdentityPlugin(identityPlugin);

		component.getDefaultHost().attach(application);
		component.start();
	}

	public void stopComponent() throws Exception {
		component.stop();
	}

	public static List<String> getRequestIds(HttpResponse response) throws ParseException,
			IOException {
		String responseStr = EntityUtils.toString(response.getEntity(), UTF_8);	
		
		List<String> requestIds = new ArrayList<String>();
		if(responseStr.contains(Request.X_OCCI_LOCATION)){
			String[] tokens = responseStr.split(Request.X_OCCI_LOCATION);
			
			for (int i = 0; i < tokens.length; i++) {
				if (!tokens[i].equals("")) {
					requestIds.add(tokens[i]);
				}
			}
		}
		
		return requestIds;
	}
}
