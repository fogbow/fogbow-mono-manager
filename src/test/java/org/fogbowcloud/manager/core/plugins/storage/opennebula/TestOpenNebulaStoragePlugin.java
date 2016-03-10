package org.fogbowcloud.manager.core.plugins.storage.opennebula;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.compute.opennebula.OneConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.image.Image;
import org.opennebula.client.image.ImagePool;

public class TestOpenNebulaStoragePlugin {
	private static final String OPEN_NEBULA_URL = "http://localhost:2633/RPC2";
	private static final String VOLUME_ID = "0";
	private static final String VOLUME_ID2 = "1";
	private static final String STORAGE_SIZE = "1";
	private static final String DATASTORE_ID = "1";
	
	private Properties properties;
	private Map<String, String> xOCCIAtt;
	private OpenNebulaStoragePlugin storageOpenNebula;
	private Token defaultToken;
	
	@Before
	public void setUp(){
		properties = new Properties();
		properties.put(OneConfigurationConstants.COMPUTE_ONE_DATASTORE_ID, String.valueOf(DATASTORE_ID));
		properties.put(OneConfigurationConstants.COMPUTE_ONE_URL, OPEN_NEBULA_URL);
		xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), STORAGE_SIZE);
		
		defaultToken = new Token(PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS,
				PluginHelper.USERNAME, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
	}
	
	@Test
	public void testRequestInstance() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		storageOpenNebula = new OpenNebulaStoragePlugin(properties, clientFactory);
		
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), 
				OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.allocateImage(Mockito.eq(oneClient), 
				Mockito.any(String.class), Mockito.eq(Integer.valueOf(DATASTORE_ID)))).thenReturn(VOLUME_ID);
		
		List<Category> categories = new LinkedList<Category>();
		String instanceId = storageOpenNebula.requestInstance(defaultToken, categories, xOCCIAtt);
		
		Assert.assertEquals(VOLUME_ID, instanceId);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGetInstances() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		storageOpenNebula = new OpenNebulaStoragePlugin(properties, clientFactory);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(
				defaultToken.getAccessId(), OPEN_NEBULA_URL)).thenReturn(oneClient);
		
		Image vol = Mockito.mock(Image.class);
		Mockito.when(vol.getId()).thenReturn(VOLUME_ID);
		Mockito.when(vol.typeStr()).thenReturn(OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		
		Image vol2 = Mockito.mock(Image.class);
		Mockito.when(vol2.getId()).thenReturn(VOLUME_ID2);
		Mockito.when(vol2.typeStr()).thenReturn(OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageIterator.next()).thenReturn(vol, vol2);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		List<Instance> instances = storageOpenNebula.getInstances(defaultToken);
		Assert.assertEquals(2, instances.size());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGetInstance() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		storageOpenNebula = new OpenNebulaStoragePlugin(properties, clientFactory);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(
				defaultToken.getAccessId(), OPEN_NEBULA_URL)).thenReturn(oneClient);
		
		Image vol = Mockito.mock(Image.class);
		Mockito.when(vol.getId()).thenReturn(VOLUME_ID);
		Mockito.when(vol.typeStr()).thenReturn(OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		
		Image vol2 = Mockito.mock(Image.class);
		Mockito.when(vol2.getId()).thenReturn(VOLUME_ID2);
		Mockito.when(vol2.typeStr()).thenReturn(OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageIterator.next()).thenReturn(vol2, vol);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		Instance instance = storageOpenNebula.getInstance(defaultToken, VOLUME_ID);
		Assert.assertEquals(VOLUME_ID, instance.getId());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRemoveInstance() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		storageOpenNebula = new OpenNebulaStoragePlugin(properties, clientFactory);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(
				defaultToken.getAccessId(), OPEN_NEBULA_URL)).thenReturn(oneClient);
		
		Image vol = Mockito.mock(Image.class);
		Mockito.when(vol.getId()).thenReturn(VOLUME_ID);
		Mockito.when(vol.typeStr()).thenReturn(
				OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		Mockito.when(vol.delete()).thenReturn(new OneResponse(true, VOLUME_ID));
		
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(vol);
		
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		storageOpenNebula.removeInstance(defaultToken, VOLUME_ID);
		Mockito.verify(vol).delete();
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRemoveInstances() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		storageOpenNebula = new OpenNebulaStoragePlugin(properties, clientFactory);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(
				defaultToken.getAccessId(), OPEN_NEBULA_URL)).thenReturn(oneClient);
		
		Image vol = Mockito.mock(Image.class);
		Mockito.when(vol.getId()).thenReturn(VOLUME_ID);
		Mockito.when(vol.typeStr()).thenReturn(
				OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		Mockito.when(vol.delete()).thenReturn(new OneResponse(true, VOLUME_ID));
		
		Image vol2 = Mockito.mock(Image.class);
		Mockito.when(vol2.getId()).thenReturn(VOLUME_ID2);
		Mockito.when(vol2.typeStr()).thenReturn(
				OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		Mockito.when(vol2.delete()).thenReturn(new OneResponse(true, VOLUME_ID2));
		
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, true, false);
		Mockito.when(imageIterator.next()).thenReturn(vol, vol2);
		
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		storageOpenNebula.removeInstances(defaultToken);
		Mockito.verify(vol).delete();
		Mockito.verify(vol2).delete();
	}
	
	@SuppressWarnings("unchecked")
	
	@Test(expected = OCCIException.class)
	public void testRemoveInexistentInstance() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		storageOpenNebula = new OpenNebulaStoragePlugin(properties, clientFactory);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(
				defaultToken.getAccessId(), OPEN_NEBULA_URL)).thenReturn(oneClient);
		
		Image vol = Mockito.mock(Image.class);
		Mockito.when(vol.getId()).thenReturn(VOLUME_ID);
		Mockito.when(vol.typeStr()).thenReturn(
				OpenNebulaStoragePlugin.OPENNEBULA_DATABLOCK_IMAGE_TYPE);
		Mockito.when(vol.delete()).thenReturn(new OneResponse(true, VOLUME_ID));
		
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(vol);
		
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(clientFactory.createImagePool(oneClient)).thenReturn(imagePool);
		
		storageOpenNebula.removeInstance(defaultToken, VOLUME_ID2);
	}

}