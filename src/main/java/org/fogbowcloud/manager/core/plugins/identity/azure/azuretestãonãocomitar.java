package org.fogbowcloud.manager.core.plugins.identity.azure;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;

public class azuretestãonãocomitar {

	private static KeyStore getKeyStore()
			throws IOException {
		KeyStore ks = null;
		FileInputStream fis = null;
		try {
			ks = KeyStore.getInstance("JKS");
			char[] passwordArray = "ana123".toCharArray();
			fis = new java.io.FileInputStream("AnaAzureKeystore.jks");
			ks.load(fis, passwordArray);
			fis.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (fis != null) {
				fis.close();
			}
		}
		return ks;
	}

	
	public static SSLConnectionSocketFactory getSSLFromToken() throws Exception {
		KeyManagerFactory keyManagerFactory = KeyManagerFactory
				.getInstance("SunX509");
		keyManagerFactory.init(getKeyStore(), "ana123".toCharArray());
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(keyManagerFactory.getKeyManagers(), null,
				new SecureRandom());
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(context);
		return sslsf;
	}

	private static HttpResponseWrapper processPostRequest(String url, String data,
			String contentType)
			throws Exception {
		
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("x-ms-version", " 2015-04-01");
		headers.put("Content-Type", contentType);
		
		return new HttpClientWrapper().doPostSSL(url, new StringEntity(data), 
				getSSLFromToken(), headers);
	}

	public static SSLConnectionSocketFactory getSSLConnectionSocketFactory(String keyStoreName,
			String password) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException {
		KeyStore ks = getKeyStore();
		KeyManagerFactory keyManagerFactory = KeyManagerFactory
				.getInstance("SunX509");
		keyManagerFactory.init(ks, "ana123".toCharArray());
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(keyManagerFactory.getKeyManagers(), null,
				new SecureRandom());
		
		System.out.println(context.getSocketFactory());
		
	        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(context);
	        //System.out.println(sslsf.g);
	        return sslsf;
	}
	
	private static void createVM(String subscriptionId, String cloudServiceName) throws Exception {
		String url = String.format(
				"https://management.core.windows.net/%s/services/hostedservices/%s/deployments", 
				subscriptionId, cloudServiceName);
		String requestBody = IOUtils.toString(new FileInputStream(
				"/home/anaribeiro/Desktop/azureXML/createVM.xml"));
		HttpResponseWrapper response = processPostRequest(url, requestBody, "application/xml");
		System.out.println(response.getStatusLine());
		System.out.println(response.getContent());
	}

	public static void main(String[] args) throws Exception {
		
		String subscriptionId = "f4e92e70-0604-4c1e-afd6-5d60e653effc";
		String cloudServiceName = "ana123";
		createVM(subscriptionId, cloudServiceName);
		
	}
			
			
}
