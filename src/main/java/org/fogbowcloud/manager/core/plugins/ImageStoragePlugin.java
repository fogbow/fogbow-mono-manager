package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.occi.core.Token;

public interface ImageStoragePlugin {
	
	public static final String QCOW2 = "qcow2";
	public static final String IMG = "img";
	public static final String ISO = "iso";
	public static final String RAW = "raw";
	public static final String VDI = "vdi";
	public static final String VHD = "vhd";
	public static final String VMDK = "vmdk";
	public static final String OVA = "ova";
	
	public String getLocalId(Token token, String globalId);
}
