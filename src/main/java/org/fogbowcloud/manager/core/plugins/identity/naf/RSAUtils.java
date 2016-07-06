package org.fogbowcloud.manager.core.plugins.identity.naf;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
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
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

public class RSAUtils {

	private static String getKey(String filename) throws IOException {
		// Read key from file
		String strKeyPEM = "";
		BufferedReader br = new BufferedReader(new FileReader(filename));
		String line;
		while ((line = br.readLine()) != null) {
			strKeyPEM += line + "n";
		}
		br.close();
		return strKeyPEM;
	}

	/**
	 * Constructs a private key (RSA) from the given file
	 * 
	 * @param filename
	 *            PEM Private Key
	 * @return RSA Private Key
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public static RSAPrivateKey getPrivateKey(String filename)
			throws IOException, GeneralSecurityException {
		String privateKeyPEM = getKey(filename);
		return getPrivateKeyFromString(privateKeyPEM);
	}

	/**
	 * Constructs a private key (RSA) from the given string
	 * 
	 * @param key
	 *            PEM Private Key
	 * @return RSA Private Key
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public static RSAPrivateKey getPrivateKeyFromString(String key)
			throws IOException, GeneralSecurityException {
		String privateKeyPEM = key;

		// Remove the first and last lines
		privateKeyPEM = privateKeyPEM
				.replace("-----BEGIN PRIVATE KEY-----", "");
		privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");

		// Base64 decode data
		byte[] encoded = org.bouncycastle.util.encoders.Base64
				.decode(privateKeyPEM);

		KeyFactory kf = KeyFactory.getInstance("RSA");
		RSAPrivateKey privKey = (RSAPrivateKey) kf
				.generatePrivate(new PKCS8EncodedKeySpec(encoded));
		return privKey;
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

	/**
	 * Encrypts the text with the public key (RSA)
	 * 
	 * @param rawText
	 *            Text to be encrypted
	 * @param publicKey
	 * @return
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public static String encrypt(String rawText, PublicKey publicKey)
			throws IOException, GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		return new String(org.bouncycastle.util.encoders.Base64.encode(cipher
				.doFinal(rawText.getBytes("UTF-8"))));
	}

	/**
	 * Decrypts the text with the private key (RSA)
	 * 
	 * @param cipherText
	 *            Text to be decrypted
	 * @param privateKey
	 * @return Decrypted text (Base64 encoded)
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public static String decrypt(String cipherText, PrivateKey privateKey)
			throws IOException, GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		return new String(cipher.doFinal(org.bouncycastle.util.encoders.Base64
				.decode(cipherText)), "UTF-8");
	}

	public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(1024);
		KeyPair keyPair = keyGen.genKeyPair();
		return keyPair;
	}

	public static String savePrivateKey(PrivateKey priv)
			throws GeneralSecurityException {
		KeyFactory fact = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec spec = fact.getKeySpec(priv, PKCS8EncodedKeySpec.class);
		byte[] packed = spec.getEncoded();
		String key64 = new String(
				org.bouncycastle.util.encoders.Base64.encode(packed));

		Arrays.fill(packed, (byte) 0);
		return key64;
	}

	public static String savePublicKey(PublicKey publ)
			throws GeneralSecurityException {
		KeyFactory fact = KeyFactory.getInstance("RSA");
		X509EncodedKeySpec spec = fact.getKeySpec(publ, X509EncodedKeySpec.class);
		return new String(org.bouncycastle.util.encoders.Base64.encode(spec
				.getEncoded()));
	}

	public static String savePublicKeyInPEMFormat(PublicKey publicKey)
			throws IOException {
		StringWriter writer = new StringWriter();
		PemWriter pemWriter = new PemWriter(writer);
		pemWriter.writeObject(new PemObject("PUBLIC KEY", publicKey
				.getEncoded()));
		pemWriter.flush();
		pemWriter.close();
		return writer.toString();
	}
}