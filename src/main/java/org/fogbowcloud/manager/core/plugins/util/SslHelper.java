package org.fogbowcloud.manager.core.plugins.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;

public class SslHelper {
	
	public static final String KEYSTORE_PATH_KEY = "keystorePath";
	public static final String KEYSTORE_PASSWORD_KEY = "keystorePassword";
	
	private static final Logger LOGGER = Logger.getLogger(SslHelper.class);
	
	public static SSLConnectionSocketFactory getSSLFromToken(Token token) {
		String keyStorePath = token.get(KEYSTORE_PATH_KEY);
		String keyStorePassword = token.get(KEYSTORE_PASSWORD_KEY);
		KeyStore keyStore;
		try {
			keyStore = getKeyStore(keyStorePath, keyStorePassword);
			KeyManagerFactory keyManagerFactory = KeyManagerFactory
					.getInstance("SunX509");
			keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(keyManagerFactory.getKeyManagers(), null,
					new SecureRandom());
			SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(context);
			return  sslsf;
		} catch (Exception e) {
			LOGGER.error("It was not possible retrive the KeyStore from the "
					+ "specified Token", e);
			throw new OCCIException(ErrorType.NOT_FOUND, 
					"It was not possible retrive the KeyStore "
					+ "from the specified Token");
		}
	}
	
	private static KeyStore getKeyStore(String keyStorePath, String keyStorePassword)
			throws IOException {
		KeyStore keyStore = null;
		FileInputStream fileInputStream = null;
		try {
			keyStore = KeyStore.getInstance("JKS");
			char[] passwordArray = keyStorePassword.toCharArray();
			fileInputStream = new java.io.FileInputStream(keyStorePath);
			keyStore.load(fileInputStream, passwordArray);
			fileInputStream.close();
		} catch (Exception e) {
			LOGGER.warn("It was not possible retrive the KeyStore from the "
					+ "specified Token", e);
			throw new OCCIException(ErrorType.NOT_FOUND, 
					"It was not possible retrive the KeyStore "
					+ "from the specified Token");
		} finally {
			if (fileInputStream != null) {
				fileInputStream.close();
			}
		}
		return keyStore;
	}
	
}
