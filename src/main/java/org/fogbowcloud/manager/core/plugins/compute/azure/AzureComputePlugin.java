package org.fogbowcloud.manager.core.plugins.compute.azure;

import java.util.List;
import java.util.Map;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.util.SslHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.restlet.Request;
import org.restlet.Response;

public class AzureComputePlugin implements ComputePlugin{
	
	private SSLConnectionSocketFactory sslSocketFactory;

	@Override
	public String requestInstance(Token token, List<Category> categories,
			Map<String, String> xOCCIAtt, String imageId) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		return null;
	}

	@Override
	public List<Instance> getInstances(Token token) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		return null;
	}

	@Override
	public Instance getInstance(Token token, String instanceId) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		
	}

	@Override
	public void removeInstances(Token token) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		
	}

	@Override
	public ResourcesInfo getResourcesInfo(Token token) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
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
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		return null;
	}

	@Override
	public ImageState getImageState(Token token, String imageName) {
		if (sslSocketFactory == null) {
			sslSocketFactory = SslHelper.getSSLFromToken(token);
		}
		return null;
	}

}
