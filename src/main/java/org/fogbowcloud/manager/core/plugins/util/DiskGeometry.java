/**
 * credits to https://github.com/njovy/vhd-converter 
 */
package org.fogbowcloud.manager.core.plugins.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DiskGeometry {

	private int cylinderCount;
	private int headCount;
	private int sectorsPerTrack;

	public DiskGeometry() {
		// empty constructor
	}

	public DiskGeometry(long bytes) {
		int secs = secs_round_up_no_zero(bytes);
		int cth;
		if (secs > 65535 * 16 * 255)
			secs = 65535 * 16 * 255;
		if (secs >= 65535 * 16 * 63) {
			sectorsPerTrack = 255;
			cth = secs / sectorsPerTrack;
			headCount = 16;
		} else {
			sectorsPerTrack = 17;
			cth = secs / sectorsPerTrack;
			headCount = (cth + 1023) / 1024;

			if (headCount < 4)
				headCount = 4;

			if (cth >= (headCount * 1024) || headCount > 16) {
				sectorsPerTrack = 31;
				cth = secs / sectorsPerTrack;
				headCount = 16;
			}

			if (cth >= headCount * 1024) {
				sectorsPerTrack = 63;
				cth = secs / sectorsPerTrack;
				headCount = 16;
			}
		}

		cylinderCount = cth / headCount + 1;

	}

	private int secs_round_up(long bytes) {
		return (int) ((bytes + (VhdFooter.VHD_FOOTER_SIZE - 1)) >> VhdFooter.VHD_SECTOR_SHIFT);
	}

	private int secs_round_up_no_zero(long bytes) {
		return (secs_round_up(bytes) > 0 ? secs_round_up(bytes) : 1);
	}

	public DiskGeometry(byte[] data) {
		if (data.length != 4) {
			throw new IllegalArgumentException("Invalid disk geometry data");
		}

		cylinderCount = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
				.getShort(0);
		headCount = data[2] & 0xFF;
		sectorsPerTrack = data[3] & 0xFF;
	}

	public int getCylinderCount() {
		return cylinderCount;
	}

	public void setCylinderCount(int cylinderCount) {
		this.cylinderCount = cylinderCount;
	}

	public int getHeadCount() {
		return headCount;
	}

	public void setHeadCount(int headCount) {
		this.headCount = headCount;
	}

	public int getSectorsPerTrack() {
		return sectorsPerTrack;
	}

	public void setSectorsPerTrack(int sectorsPerTrack) {
		this.sectorsPerTrack = sectorsPerTrack;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("DiskGeometry [cylinderCount=");
		builder.append(cylinderCount);
		builder.append(", headCount=");
		builder.append(headCount);
		builder.append(", sectorsPerTrack=");
		builder.append(sectorsPerTrack);
		builder.append("]");
		return builder.toString();
	}

	public byte[] toByte() {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[4]).order(
				ByteOrder.BIG_ENDIAN);
		buffer.putInt(cylinderCount << 16 | headCount << 8 | sectorsPerTrack);
		return buffer.array();
	}

}