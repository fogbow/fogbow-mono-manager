package org.fogbowcloud.manager.core.plugins.imagestorage.vmcatcher;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.fogbowcloud.manager.core.CurrentThreadExecutorService;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestVMCatcherStoragePluginTest {

	private static final String IMAGE_LIST_URL = "file://" + new File("").getAbsolutePath() + 
			"/src/test/resources/imagestorage/vmcatcher/image.list";
	private ExecutorService downloader;
	private ComputePlugin computePlugin;
	private ShellWrapper shellWrapper;
	private Properties properties;
	private VMCatcherStoragePlugin plugin;

	@Before
	public void setup() {
		this.downloader = Mockito.mock(ExecutorService.class);
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		this.shellWrapper = Mockito.mock(ShellWrapper.class);
		this.properties = new Properties();
		this.plugin = new VMCatcherStoragePlugin(properties, 
				computePlugin, downloader, shellWrapper);
	}
	
	@Test
	public void testImageListWithMalformedURL() {
		Assert.assertNull(plugin.getLocalId(null, "non-url"));
		Mockito.verifyZeroInteractions(downloader);
	}
	
	@Test
	public void testImageListWithNotFoundURL() {
		Assert.assertNull(plugin.getLocalId(null, "file://path/does/not/exist"));
		Mockito.verifyZeroInteractions(downloader);
	}
	
	@Test
	public void testImageWithValidURLWithNoIdentifierCesga() {
		properties.put(VMCatcherStoragePlugin.PROP_VMC_PUSH_METHOD, "cesga");
		Assert.assertNull(plugin.getLocalId(null, "file://" + new File("").getAbsolutePath() + 
				"/src/test/resources/imagestorage/vmcatcher/image.list.noidentifier."));
		Mockito.verifyZeroInteractions(downloader);
	}
	
	@Test
	public void testImageWithValidURLWithNoIdentifierGlance() {
		properties.put(VMCatcherStoragePlugin.PROP_VMC_PUSH_METHOD, "glancepush");
		Assert.assertNull(plugin.getLocalId(null, "file://" + new File("").getAbsolutePath() + 
				"/src/test/resources/imagestorage/vmcatcher/image.list.notitle"));
		Mockito.verifyZeroInteractions(downloader);
	}
	
	@Test
	public void testImageWithValidURLWithProperIdentifierCesga() throws IOException, InterruptedException {
		properties.put(VMCatcherStoragePlugin.PROP_VMC_PUSH_METHOD, "cesga");
		Assert.assertNull(plugin.getLocalId(null, IMAGE_LIST_URL));
		Mockito.verify(computePlugin).getImageId(null, "2c24de6c-e385-49f1-b64f-f9ff35e70f43");
		Mockito.verify(shellWrapper).execute(new HashMap<String, String>(), "vmcatcher_subscribe", 
				"--imagelist-newimage-subscribe", "--auto-endorse", "-s", IMAGE_LIST_URL);
		Mockito.verify(downloader).execute(Mockito.any(Runnable.class));
	}
	
	@Test
	public void testImageWithValidURLWithProperIdentifierGlance() throws IOException, InterruptedException {
		properties.put(VMCatcherStoragePlugin.PROP_VMC_PUSH_METHOD, "glancepush");
		Assert.assertNull(plugin.getLocalId(null, IMAGE_LIST_URL));
		Mockito.verify(computePlugin).getImageId(null, "OS_Disk_Image");
		Mockito.verify(shellWrapper).execute(new HashMap<String, String>(), "vmcatcher_subscribe", 
				"--imagelist-newimage-subscribe", "--auto-endorse", "-s", IMAGE_LIST_URL);
		Mockito.verify(downloader).execute(Mockito.any(Runnable.class));
	}
	
	@Test
	public void testImageWithValidURLWithProperIdentifierGlanceWithEnvVars() throws IOException, InterruptedException {
		properties.put(VMCatcherStoragePlugin.PROP_VMC_PUSH_METHOD, "glancepush");
		properties.put(VMCatcherStoragePlugin.PROP_VMC_ENV_PREFIX + "VMCATCHER_CACHE_EVENT", "python /var/lib/vmcatcher/gpvcmupdate.py");
		properties.put(VMCatcherStoragePlugin.PROP_VMC_ENV_PREFIX + "VMCATCHER_CACHE_DIR_EXPIRE", "/var/lib/vmcatcher/cache/expired");
		Assert.assertNull(plugin.getLocalId(null, IMAGE_LIST_URL));
		Mockito.verify(computePlugin).getImageId(null, "OS_Disk_Image");
		
		HashMap<String, String> envVarMap = new HashMap<String, String>();
		envVarMap.put("VMCATCHER_CACHE_EVENT", "python /var/lib/vmcatcher/gpvcmupdate.py");
		envVarMap.put("VMCATCHER_CACHE_DIR_EXPIRE", "/var/lib/vmcatcher/cache/expired");
		
		Mockito.verify(shellWrapper).execute(envVarMap, "vmcatcher_subscribe", 
				"--imagelist-newimage-subscribe", "--auto-endorse", "-s", IMAGE_LIST_URL);
		Mockito.verify(downloader).execute(Mockito.any(Runnable.class));
	}
	
	@Test
	public void testImageWithValidURLWithProperIdentifierCesgaSameThread() throws IOException, InterruptedException {
		setupInSameThread();
		properties.put(VMCatcherStoragePlugin.PROP_VMC_PUSH_METHOD, "cesga");
		Assert.assertNull(plugin.getLocalId(null, IMAGE_LIST_URL));
		Mockito.verify(shellWrapper).execute(new HashMap<String, String>(), "vmcatcher_subscribe", "-U");
		Mockito.verify(shellWrapper).execute(new HashMap<String, String>(), "vmcatcher_cache");
	}
	
	@Test
	public void testImageWithValidURLWithProperIdentifierCesgaSameThreadForceSudo() throws IOException, InterruptedException {
		setupInSameThread();
		properties.put(VMCatcherStoragePlugin.PROP_VMC_PUSH_METHOD, "cesga");
		properties.put(VMCatcherStoragePlugin.PROP_VMC_USE_SUDO, "true");
		Assert.assertNull(plugin.getLocalId(null, IMAGE_LIST_URL));
		Mockito.verify(shellWrapper).execute(new HashMap<String, String>(), "sudo", "-E", "vmcatcher_subscribe", "-U");
		Mockito.verify(shellWrapper).execute(new HashMap<String, String>(), "sudo", "-E", "vmcatcher_cache");
	}
	
	@Test
	public void testImageWithValidURLWithProperIdentifierGlanceSameThread() throws IOException, InterruptedException {
		setupInSameThread();
		properties.put(VMCatcherStoragePlugin.PROP_VMC_PUSH_METHOD, "glancepush");
		Assert.assertNull(plugin.getLocalId(null, IMAGE_LIST_URL));
		Mockito.verify(shellWrapper).execute(new HashMap<String, String>(), "vmcatcher_subscribe", "-U");
		Mockito.verify(shellWrapper).execute(new HashMap<String, String>(), "vmcatcher_cache");
	}

	private void setupInSameThread() {
		CurrentThreadExecutorService executor = new CurrentThreadExecutorService();
		this.downloader = Mockito.mock(ExecutorService.class);
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		this.shellWrapper = Mockito.mock(ShellWrapper.class);
		this.properties = new Properties();
		this.plugin = new VMCatcherStoragePlugin(properties, 
				computePlugin, executor, shellWrapper);
	}
	
}
