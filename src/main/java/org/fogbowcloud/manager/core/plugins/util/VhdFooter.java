/**
 * credits to https://github.com/njovy/vhd-converter 
 */

package org.fogbowcloud.manager.core.plugins.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

public class VhdFooter {

	private static final String MS_COOKIE = "conectix";
	public static final byte[] COOKIE = new byte[] { 'c', 'o', 'n', 'e', 'c',
			't', 'i', 'x' };
	private static final byte[] CREATOR_APPLICATION = new byte[] { 'p', 'a',
			'r', 'k' };
	// January 1, 2000 12:00:00 AM in GMT in milliseconds.
	private static final long START_TIME = 946684800000L;//946728000000L;

	public static final int OS_WINDOWS = 0x5769326B;
	public static final int OS_MACINTOSH = 0x4D616320;

	public static final int FEATURE_RESERVED = 2;

	public static final int FIXED_HARD_DISK_TYPE = 2;

	public static final int DYNAMIC_HARD_DISK_TYPE = 3;
	public static final int DIFFERENCING_HARD_DISK_TYPE = 4;

	public static final int VHD_FOOTER_SIZE = 512;

	private static final int RESERVED_BIT = 427;

	public static final int VHD_SECTOR_SHIFT = 9;

	private String cookie;
	private int features;
	private float formatVersion;
	private long dataOffset;
	private int timeStamp;
	private String creatorApplication;
	private float creatorVersion;
	private int creatorOS;
	private long originalSize;
	private long currentSize;
	private DiskGeometry diskGeometry;
	private int diskType;
	private int checksum;
	private byte[] uuid;
	private int savedState;

	public static ByteBuffer create(long size) throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[VHD_FOOTER_SIZE]).order(
				ByteOrder.BIG_ENDIAN);
		buffer.put(COOKIE);
		buffer.putInt(FEATURE_RESERVED);
		buffer.putShort((short) 1);
		buffer.putShort((short) 0);
		buffer.putLong(-1);
		buffer.putInt((int) ((System.currentTimeMillis() - START_TIME) / 1000));
		buffer.put(CREATOR_APPLICATION);
		buffer.putShort((short) 5);
		buffer.putShort((short) 3);
		buffer.putInt(OS_WINDOWS);
		buffer.putLong(size);
		buffer.putLong(size);
		//buffer.put(new DiskGeometry(size).toByte());
		buffer.put(new byte[]{0x08, 0x20, 0x10, 0x3f});
		buffer.putInt(FIXED_HARD_DISK_TYPE);
		int checksumPos = buffer.position();
		buffer.putInt(0);

		// generate a random uuid.
		UUID uuid = UUID.randomUUID();
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());
		buffer.put((byte) 0);
		buffer.put(new byte[RESERVED_BIT]);
		int res = 0;
		byte[] temp = buffer.array();
		for (int i = 0; i < temp.length - RESERVED_BIT; i++) {
			if (i > 63 && i < 68) {
				continue;
			}
			res += (temp[i] & 0xff);
		}
		res = ~res;
		buffer.putInt(checksumPos, (int) (res));
		buffer.flip();
		return buffer;
	}

	public VhdFooter(ByteBuffer buff) {
		byte[] cookieData = new byte[8];
		buff.get(cookieData);
		cookie = new String(cookieData);
		features = buff.getInt();
		formatVersion = VhdUtils.toFloat(buff.getShort(), buff.getShort());
		dataOffset = buff.getLong();
		timeStamp = buff.getInt();
		byte[] creatorData = new byte[4];
		buff.get(creatorData);
		creatorApplication = new String(creatorData);
		creatorVersion = VhdUtils.toFloat(buff.getShort(), buff.getShort());
		creatorOS = buff.getInt();
		originalSize = buff.getLong();
		currentSize = buff.getLong();
		byte[] diskGeometryData = new byte[4];
		buff.get(diskGeometryData);
		diskGeometry = new DiskGeometry(diskGeometryData);
		diskType = buff.getInt();
		checksum = buff.getInt();
		uuid = new byte[16];
		buff.get(uuid);
		savedState = buff.get();
	}

	public boolean hasHeader() {
		// only dynamic and differencing disk images have headers
		return (diskType == DYNAMIC_HARD_DISK_TYPE || diskType == DIFFERENCING_HARD_DISK_TYPE);
	}

	public boolean isWindows() {
		return (creatorOS == VhdFooter.OS_WINDOWS);
	}

	public boolean isMac() {
		return (creatorOS == VhdFooter.OS_MACINTOSH);
	}

	public String getCookie() {
		return cookie;
	}

	public int getFeatures() {
		return features;
	}

	public float getFormatVersion() {
		return formatVersion;
	}

	public long getDataOffset() {
		return dataOffset;
	}

	public int getTimeStamp() {
		return timeStamp;
	}

	public String getCreatorApplication() {
		return creatorApplication;
	}

	public float getCreatorVersion() {
		return creatorVersion;
	}

	public int getCreatorOS() {
		return creatorOS;
	}

	public long getOriginalSize() {
		return originalSize;
	}

	public long getCurrentSize() {
		return currentSize;
	}

	public DiskGeometry getDiskGeometry() {
		return diskGeometry;
	}

	public int getDiskType() {
		return diskType;
	}

	public int getChecksum() {
		return checksum;
	}

	public String getUuid() {
		return VhdUtils.toUUIDFormat(uuid);
	}

	public int getSavedState() {
		return savedState;
	}

	public boolean validate() {
		return cookie.equals(MS_COOKIE);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("VhdFooter [cookie=");
		builder.append(cookie);
		builder.append(", features=");
		builder.append(features);
		builder.append(", formatVersion=");
		builder.append(formatVersion);
		builder.append(", dataOffset=");
		builder.append(dataOffset);
		builder.append(", timeStamp=");
		builder.append(timeStamp);
		builder.append(", creatorApplication=");
		builder.append(creatorApplication);
		builder.append(", creatorVersion=");
		builder.append(creatorVersion);
		builder.append(", creatorOS=");
		builder.append(creatorOS);
		builder.append(", originalSize=");
		builder.append(originalSize);
		builder.append(", currentSize=");
		builder.append(currentSize);
		builder.append(", diskGeometry=");
		builder.append(diskGeometry);
		builder.append(", diskType=");
		builder.append(diskType);
		builder.append(", checksum=");
		builder.append(checksum);
		builder.append(", uuid=");
		builder.append(Arrays.toString(uuid));
		builder.append(", savedState=");
		builder.append(savedState);
		builder.append("]");
		return builder.toString();
	}
}