package org.fogbowcloud.manager.core.plugins.identity.naf;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;

public class RSAUtils {

	private static String getKey(String filename) throws IOException {
		// Read key from file
		String strKeyPEM = "";
		BufferedReader br = new BufferedReader(new FileReader(filename));
		String line;
		while ((line = br.readLine()) != null) {
			strKeyPEM += line;
		}
		br.close();
		return strKeyPEM;
	}

	/**
	 * Constructs a public key (RSA) from the given file
	 * 
	 * @param filename
	 *            PEM Public Key
	 * @return RSA Public Key
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public static RSAPublicKey getPublicKey(String filename)
			throws IOException, GeneralSecurityException {
		String publicKeyPEM = getKey(filename);
		return getPublicKeyFromString(publicKeyPEM);
	}

	/**
	 * Constructs a public key (RSA) from the given string
	 * 
	 * @param key
	 *            PEM Public Key
	 * @return RSA Public Key
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public static RSAPublicKey getPublicKeyFromString(String key)
			throws IOException, GeneralSecurityException {
		String publicKeyPEM = key;

		// Remove the first and last lines
		publicKeyPEM = publicKeyPEM.replace("-----BEGIN PUBLIC KEY-----", "");
		publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");

		// Base64 decode data
		byte[] encoded = org.bouncycastle.util.encoders.Base64
				.decode(publicKeyPEM);

		KeyFactory kf = KeyFactory.getInstance("RSA");
		RSAPublicKey pubKey = (RSAPublicKey) kf
				.generatePublic(new X509EncodedKeySpec(encoded));
		return pubKey;
	}

	/**
	 * @param privateKey
	 * @param message
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 * @throws UnsupportedEncodingException
	 */
	public static String sign(PrivateKey privateKey, String message)
			throws NoSuchAlgorithmException, InvalidKeyException,
			SignatureException, UnsupportedEncodingException {
		Signature sign = Signature.getInstance("SHA1withRSA");
		sign.initSign(privateKey);
		sign.update(message.getBytes("UTF-8"));
		return new String(Base64.encodeBase64(sign.sign()), "UTF-8");
	}

	/**
	 * @param publicKey
	 * @param message
	 * @param signature
	 * @return
	 * @throws SignatureException
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 * @throws InvalidKeyException
	 */
	public static boolean verify(PublicKey publicKey, String message,
			String signature) throws SignatureException,
			NoSuchAlgorithmException, UnsupportedEncodingException,
			InvalidKeyException {
		Signature sign = Signature.getInstance("SHA1withRSA");
		sign.initVerify(publicKey);
		sign.update(message.getBytes("UTF-8"));
		return sign.verify(Base64.decodeBase64(signature.getBytes("UTF-8")));
	}

	public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(1024);
		KeyPair keyPair = keyGen.genKeyPair();
		return keyPair;
	}


	public static String savePublicKey(PublicKey publ)
			throws GeneralSecurityException {
		KeyFactory fact = KeyFactory.getInstance("RSA");
		X509EncodedKeySpec spec = fact.getKeySpec(publ, X509EncodedKeySpec.class);
		return new String(org.bouncycastle.util.encoders.Base64.encode(spec
				.getEncoded()));
	}
	
}