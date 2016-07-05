/**
 * credits to https://github.com/njovy/vhd-converter 
 */
package org.fogbowcloud.manager.core.plugins.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Formatter;

public class VhdUtils {

	private static FileChannel srcChannel;
	private static RandomAccessFile stream;
	public static final int HEADER_SIZE = 512;

	public static String toUUIDFormat(byte[] bytes) {
		byte[] switched = new byte[] { bytes[3], bytes[2], bytes[1], bytes[0],
				bytes[5], bytes[4], bytes[7], bytes[6], bytes[8], bytes[9],
				bytes[10], bytes[11], bytes[12], bytes[13], bytes[14],
				bytes[15] };

		StringBuilder sb = new StringBuilder(bytes.length * 2);
		Formatter formatter = new Formatter(sb);
		for (byte b : switched) {
			formatter.format("%02x", b);
		}
		sb.insert(8, "-");
		sb.insert(13, "-");
		sb.insert(18, "-");
		sb.insert(23, "-");

		String temp = sb.toString().toUpperCase();
		formatter.close();
		return temp;
	}

	public static float toFloat(int majorVersion, int minorVersion) {
		return Float.parseFloat(majorVersion + "." + minorVersion);
	}

	public static void openImage(String imageLocation, boolean create)
			throws IOException {

		File srcFile = new File(imageLocation);
		if (create) {
			srcFile.mkdirs();
			srcFile.createNewFile();

			// TODO create the VHD here
		}

		stream = new RandomAccessFile(srcFile, "rw");

		srcChannel = stream.getChannel();
	}

	public static FileChannel getChannel() {
		return srcChannel;
	}

	public static long getChannelSize() throws IOException {
		return srcChannel.size();
	}

	public static void closeImage() throws IOException {
		stream.close();
	}

	public static long getValueL(byte[] by) {
		long value = 0;
		for (int i = 0; i < by.length; i++) {
			value += ((long) by[i] & 0xffL) << (8 * i);
		}
		return value;
	}

	public static long getValueM(byte[] by) {
		long value = 0;
		for (int i = 0; i < by.length; i++) {
			value = (value << 8) + (by[i] & 0xff);
		}
		return value;
	}

	public static byte[] toBytes(long num) {
		BigInteger a = BigInteger.valueOf(num);
		byte[] temp = a.toByteArray();
		byte[] tr = new byte[temp.length];

		for (int i = 0; i < tr.length; i++) {
			tr[i] = temp[temp.length - 1 - i];
		}
		return tr;
	}

	public static boolean convertVhdToRaw(String input, String output) {
		System.out.print("vhd -> raw - ");
		try {
			File file = new File(input);
			if (!file.exists()) {
				System.out.println("input: " + input + " doesn't exist");
				return false;
			}
			RandomAccessFile stream = new RandomAccessFile(file, "rw");
			FileChannel channel = stream.getChannel();
			if (isVhdFooterAvailable(stream.length(), channel)) {
				stream.setLength(stream.length() - HEADER_SIZE);
				channel.close();
				stream.close();
				System.out.println(input + "->" + output);
				file.renameTo(new File(output));
			} else {
				channel.close();
				stream.close();
				System.out.println("This is not a vhd.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	public static boolean convertRawToVhd(String input, String output) {
		System.out.print("raw -> vhd - ");
		try {
			File file = new File(input);
			if (!file.exists()) {
				System.out.println("input: " + input + " doesn't exist");
				return false;
			}
			RandomAccessFile stream = new RandomAccessFile(file, "rw");
			FileChannel channel = stream.getChannel();

			if (!isVhdFooterAvailable(file.length(), channel)) {
				channel.write(VhdFooter.create(stream.length()), file.length());
			} else {
				System.out.println("input " + input + " is vhd");
			}
			channel.close();
			stream.close();
			file.renameTo(new File(output));
			System.out.println(input + " -> " + output);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Check if the file has a vhd cookie at the beginning of a vhd footer.
	 * 
	 * @param fileSize
	 *            the size of the given file
	 * @param channel
	 *            the channel of the file
	 * @return true if the file has a vhd footer.
	 * @throws IOException
	 */
	public static final boolean isVhdFooterAvailable(long fileSize,
			FileChannel channel) throws IOException {

		if (fileSize < HEADER_SIZE) {
			return true;
		}
		ByteBuffer cookie = ByteBuffer.allocate(VhdFooter.COOKIE.length);
		channel.read(cookie, fileSize - HEADER_SIZE);
		return Arrays.equals(cookie.array(), VhdFooter.COOKIE);
	}

}