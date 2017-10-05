package org.fogbowcloud.manager.core.util;

import java.io.IOException;
import java.util.Properties;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.mockito.internal.configuration.GlobalConfiguration;
import org.mockito.internal.progress.ThreadSafeMockingProgress;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.support.ClassLoaderUtil;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ HttpClientBuilder.class, RequestConfig.class, HttpRequestUtilTest.class })
public class HttpRequestUtilTest {

	@After
	public void tearDown() throws InterruptedException {
		MockitoStateCleaner mockitoStateCleaner = new MockitoStateCleaner();
		mockitoStateCleaner.run();
	}
	
	@Test
	public void testInitalizeWithPropertiesNull() {
		Properties properties = null;
		HttpRequestUtil.init(properties);
		int timeoutHttpRequest = HttpRequestUtil.getTimeoutHttpRequest();
		Assert.assertEquals(timeoutHttpRequest, HttpRequestUtil.DEFAULT_TIMEOUT_REQUEST);
	}
	
	@Test
	public void testInitalizeWithWorngProperties() {
		Properties properties = new Properties();
		String valueIsNotANumber = "worng";
		properties.put(ConfigurationConstants.TIMEOUT_HTTP_REQUEST, valueIsNotANumber);
		HttpRequestUtil.init(properties);
		int timeoutHttpRequest = HttpRequestUtil.getTimeoutHttpRequest();
		Assert.assertEquals(timeoutHttpRequest, HttpRequestUtil.DEFAULT_TIMEOUT_REQUEST);
	}
	
	@Test(expected=Exception.class)
	public void testInitalizeWithUnknowError() {
		Properties properties = Mockito.mock(Properties.class);
		Mockito.when(properties.getProperty(Mockito.anyString()));
		HttpRequestUtil.init(properties);
		Assert.assertNull(HttpRequestUtil.DEFAULT_TIMEOUT_REQUEST);
	}
		
	@Test
	public void testCreateHttpClient() throws InterruptedException {
		HttpRequestUtil.setTimeoutHttpRequest(null);
		
		HttpClientBuilder httpClientBuilder = Mockito.mock(HttpClientBuilder.class);
		RequestConfig.Builder requestBuilder = Mockito.spy(RequestConfig.Builder.class);
		Mockito.when(requestBuilder.setSocketTimeout(Mockito.anyInt())).thenReturn(requestBuilder);

		PowerMockito.mockStatic(RequestConfig.class);
		BDDMockito.given(RequestConfig.custom()).willReturn(requestBuilder);
		
        PowerMockito.mockStatic(HttpClientBuilder.class);
        BDDMockito.given(HttpClientBuilder.create()).willReturn(httpClientBuilder);
        
        HttpRequestUtil.createHttpClient();      
                
        Mockito.verify(requestBuilder).setSocketTimeout(Mockito.eq(HttpRequestUtil.DEFAULT_TIMEOUT_REQUEST));
        Mockito.verify(httpClientBuilder, Mockito.times(1)).setDefaultRequestConfig(Mockito.any(RequestConfig.class));
	}
	
	@Test
	public void testCreateHttpClientCompleteWithTimeoutDefault() {
		Integer timeout = 10000;
		HttpRequestUtil.setTimeoutHttpRequest(null);
		
		HttpClientBuilder httpClientBuilder = Mockito.spy(HttpClientBuilder.class);
		RequestConfig.Builder requestBuilder = Mockito.spy(RequestConfig.Builder.class);
		Mockito.when(requestBuilder.setSocketTimeout(Mockito.anyInt())).thenReturn(requestBuilder);

		PowerMockito.mockStatic(RequestConfig.class);
		BDDMockito.given(RequestConfig.custom()).willReturn(requestBuilder);	
        PowerMockito.mockStatic(HttpClientBuilder.class);
        BDDMockito.given(HttpClientBuilder.create()).willReturn(httpClientBuilder);
        
		SSLConnectionSocketFactory sslConnectionSocketFactory = Mockito
				.mock(SSLConnectionSocketFactory.class);
        
        HttpRequestUtil.createHttpClient(timeout, sslConnectionSocketFactory, null);
        
        Mockito.verify(requestBuilder, Mockito.never()).setSocketTimeout(Mockito.eq(0));
        Mockito.verify(requestBuilder).setSocketTimeout(Mockito.eq(timeout));
        Mockito.verify(httpClientBuilder).setDefaultRequestConfig(Mockito.any(RequestConfig.class));
	}
	
	@Test
	public void testCreateHttpClientCompleteWithHttpClientConnection() {
		Integer timeout = 10000;
		HttpRequestUtil.setTimeoutHttpRequest(null);
		
		HttpClientBuilder httpClientBuilder = Mockito.mock(HttpClientBuilder.class);
		RequestConfig.Builder requestBuilder = Mockito.spy(RequestConfig.Builder.class);
		Mockito.when(requestBuilder.setSocketTimeout(Mockito.anyInt())).thenReturn(requestBuilder);

		PowerMockito.mockStatic(RequestConfig.class);
		BDDMockito.given(RequestConfig.custom()).willReturn(requestBuilder);	
        PowerMockito.mockStatic(HttpClientBuilder.class);
        BDDMockito.given(HttpClientBuilder.create()).willReturn(httpClientBuilder);
        
		HttpClientConnectionManager httpClientConnectionManager = Mockito
				.mock(HttpClientConnectionManager.class);		
        
        HttpRequestUtil.createHttpClient(timeout, null, httpClientConnectionManager);            
        
        Mockito.verify(requestBuilder, Mockito.never()).setSocketTimeout(Mockito.eq(0));
        Mockito.verify(requestBuilder).setSocketTimeout(Mockito.eq(timeout));
        Mockito.verify(httpClientBuilder).setConnectionManager(Mockito.eq(httpClientConnectionManager));
	}	
	
	@Test
	public void testSetDefaultResquestConfig() throws ClientProtocolException, IOException {	
		HttpRequestUtil.init(null);
		
		HttpClientBuilder httpClientBuilder = Mockito.mock(HttpClientBuilder.class);
		PowerMockito.mockStatic(RequestConfig.class);
		RequestConfig.Builder requestBuilder = Mockito.spy(RequestConfig.Builder.class);
		BDDMockito.given(RequestConfig.custom()).willReturn(requestBuilder);		
		
		Integer timeout = 1000;		
		HttpRequestUtil.setDefaultResquestConfig(timeout, httpClientBuilder);
		
        Mockito.verify(requestBuilder, Mockito.times(1)).setSocketTimeout(Mockito.eq(timeout));
        
        timeout = null;
        HttpRequestUtil.setDefaultResquestConfig(timeout, httpClientBuilder);
		
        Mockito.verify(requestBuilder).setSocketTimeout(Mockito.eq(HttpRequestUtil.DEFAULT_TIMEOUT_REQUEST));        
	}
	
	@Test
	public void testSetSSLConnection() {
		HttpClientBuilder httpClientBuilder = Mockito.mock(HttpClientBuilder.class);
		
		SSLConnectionSocketFactory sslsf = Mockito.mock(SSLConnectionSocketFactory.class);
		HttpRequestUtil.setSSLConnection(sslsf, httpClientBuilder);
		
		Mockito.verify(httpClientBuilder).setSSLSocketFactory(Mockito.eq(sslsf));
	}
	
	@Test
	public void testSetConnectionManager() {
		HttpClientBuilder httpClientBuilder = Mockito.mock(HttpClientBuilder.class);
		
		HttpClientConnectionManager conncetionManager = Mockito.mock(HttpClientConnectionManager.class);
		HttpRequestUtil.setConnectionManager(conncetionManager, httpClientBuilder);
		
		Mockito.verify(httpClientBuilder).setConnectionManager(Mockito.eq(conncetionManager));
	}
	
	private static class MockitoStateCleaner implements Runnable {
	    public void run() {
	        clearMockProgress();
	        clearConfiguration();
	    }

	    private void clearMockProgress() {
	        clearThreadLocalIn(ThreadSafeMockingProgress.class);
	    }

	    private void clearConfiguration() {
	        clearThreadLocalIn(GlobalConfiguration.class);
	    }

	    @SuppressWarnings("unchecked")
		private void clearThreadLocalIn(Class<?> cls) {
	        Whitebox.getInternalState(cls, ThreadLocal.class).set(null);
	        final Class<?> clazz = ClassLoaderUtil.loadClass(cls, ClassLoader.getSystemClassLoader());
	        Whitebox.getInternalState(clazz, ThreadLocal.class).set(null);
	    }
	}	
	
}
