package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.occi.core.Token;

public interface ImageStoragePlugin {
	
	enum Extensions {
		qcow2, img, iso, raw, vdi, vhd, vmdk, ova;
		
		public static boolean in(String extension) {
			try {
				return valueOf(extension) != null;
			} catch (Throwable e) {
				return false;
			}
		}
	}
	
	public String getLocalId(Token token, String globalId);
}
