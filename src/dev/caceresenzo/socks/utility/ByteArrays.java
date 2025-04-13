package dev.caceresenzo.socks.utility;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ByteArrays {

	public static int[] toUnsigned(byte[] bytes) {
		final var ints = new int[bytes.length];

		for (int i = 0; i < bytes.length; i++) {
			ints[i] = Byte.toUnsignedInt(bytes[i]);
		}

		return ints;
	}

}