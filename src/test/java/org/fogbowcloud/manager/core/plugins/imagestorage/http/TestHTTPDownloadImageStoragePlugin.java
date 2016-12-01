package org.fogbowcloud.manager.core.plugins.imagestorage.http;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.imagestorage.fixed.StaticImageStoragePlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestHTTPDownloadImageStoragePlugin {

	private HTTPDownloadImageStoragePlugin httpDownloadImageStoragePlugin;
	private String imageOneName = "imageOne";
	private String imageOneValue = "imageOneValue";
	
	@Before
	public void setUp() {
		this.httpDownloadImageStoragePlugin = new HTTPDownloadImageStoragePlugin(new Properties(), null);
	}
	
	@Test
	public void testGetLocalIdStatic() {
		String globalId = imageOneName;
		Properties properties = new Properties();
		properties.put(StaticImageStoragePlugin.PROP_STATIC_IMAGE_PREFIX + imageOneName, imageOneValue);
		ComputePlugin computePlugin = null;
		this.httpDownloadImageStoragePlugin = new HTTPDownloadImageStoragePlugin(properties, computePlugin);
		Assert.assertEquals(imageOneValue, httpDownloadImageStoragePlugin.getLocalId(null, globalId));
	}
	
	@Test
	public void testGetLocalIdInTheCloud() {
		Properties properties = new Properties();
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Token token = new Token("accessId", new Token.User("user", "user"), null, null);
		String globalId = "globalId";
		String localImage = "localId";
		Mockito.when(computePlugin.getImageId(token, globalId)).thenReturn(localImage);
		this.httpDownloadImageStoragePlugin = new HTTPDownloadImageStoragePlugin(properties, computePlugin);
		Assert.assertEquals(localImage, httpDownloadImageStoragePlugin.getLocalId(token, globalId));
	}
	
	@Test
	public void testGetLocalIdInTheCloudWithHttpBaseUrlPropertie() {
		Properties properties = new Properties();
		properties.put("image_storage_http_base_url", "neto");
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Token token = new Token("accessId", new Token.User("user", "user"), null, null);
		String globalId = "http://globalId";
		String localImage = "localId";
		Mockito.when(computePlugin.getImageId(Mockito.any(Token.class), Mockito.anyString())).thenReturn(null, localImage);
		this.httpDownloadImageStoragePlugin = new HTTPDownloadImageStoragePlugin(properties, computePlugin);
		Assert.assertEquals(localImage, httpDownloadImageStoragePlugin.getLocalId(token, globalId));		
	}
	
	@Test
	public void testGetLocalIdInTheCloudWithoutHttpBaseUrlPropertie() {
		Properties properties = new Properties();
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Token token = new Token("accessId", new Token.User("user", "user"), null, null);
		String globalId = "http://globalId";
		String localImage = "localId";
		Mockito.when(computePlugin.getImageId(Mockito.any(Token.class), Mockito.anyString())).thenReturn(null, localImage);
		this.httpDownloadImageStoragePlugin = new HTTPDownloadImageStoragePlugin(properties, computePlugin);
		Assert.assertEquals(localImage, httpDownloadImageStoragePlugin.getLocalId(token, globalId));		
	}	
	
	@Test
	public void testRemoveHTTPPrefix() {
		String imageName = "image";
		String imageURL = "http://" + imageName ;
		Assert.assertEquals(imageName, httpDownloadImageStoragePlugin.removeHTTPPrefix(imageURL));
	}
	
	@Test
	public void testCreateURL() {
		Properties properties = new Properties();
		String httpBaseUrl = "url";
		properties.put("image_storage_http_base_url", httpBaseUrl);
		this.httpDownloadImageStoragePlugin = new HTTPDownloadImageStoragePlugin(properties, null);
		String globalId = "globalId";
		Assert.assertEquals(httpBaseUrl + "/" + globalId, httpDownloadImageStoragePlugin.createURL(globalId));
	}	
}
