package dev.caceresenzo.socks.utility;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Socket;

import lombok.experimental.UtilityClass;

@UtilityClass
public class IOStreams {

	public static byte[] readByteArray(DataInputStream input) throws IOException {
		final var size = input.readUnsignedByte();

		return input.readNBytes(size);
	}

	public static int readPort(DataInputStream input) throws IOException {
		return input.readUnsignedShort();
	}

	public static Inet4Address readInet4Address(DataInputStream input) throws IOException {
		final var bytes = input.readNBytes(4);

		return (Inet4Address) Inet4Address.getByAddress(bytes);
	}

	public static String readNullTerminatedString(DataInputStream input) throws IOException {
		final var builder = new StringBuilder();

		for (var character = (char) input.readByte(); character != 0; character = (char) input.readByte()) {
			builder.append(character);
		}

		return builder.toString();
	}

	public static void transfer(Socket from, Socket to) {
		try (
			final var fromInputStream = from.getInputStream();
			final var toOutputStream = to.getOutputStream();
		) {
			fromInputStream.transferTo(toOutputStream);
		} catch (IOException exception) {
			final var message = exception.getMessage();
			if (message != null && message.contains("closed")) {
				return;
			}

			exception.printStackTrace();
		}
	}

}