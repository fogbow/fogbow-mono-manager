package org.fogbowcloud.manager.occi.util;

import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;

public class SecurityRestrictionHelper {

	public static boolean checkUnlimitedStrengthPolicy() {
		try {
			return Cipher.getMaxAllowedKeyLength("AES") > 128;
		} catch (NoSuchAlgorithmException e) {
			return false;
		}
	}

}
