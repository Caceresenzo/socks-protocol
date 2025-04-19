package dev.caceresenzo.socks.v5;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.BiPredicate;

import dev.caceresenzo.socks.utility.IOStreams;
import lombok.experimental.ExtensionMethod;

public interface Authenticator {

	Result authenticate(Socket socket) throws IOException;

	byte getMethod();

	public record Result(
		boolean success,
		byte version
	) {}

	public static Authenticator noAuthentication() {
		return NoAuthentication.INSTANCE;
	}

	public static Authenticator usernamePassword(BiPredicate<String, byte[]> predicate) {
		return new UsernamePassword(predicate);
	}

	public enum NoAuthentication implements Authenticator {

		INSTANCE;

		private static final Result RESULT = new Result(true, (byte) 0x05);

		@Override
		public Result authenticate(Socket socket) throws IOException {
			return RESULT;
		}

		@Override
		public byte getMethod() {
			return 0x00;
		}

	}

	@ExtensionMethod({ IOStreams.class })
	public record UsernamePassword(
		BiPredicate<String, byte[]> predicate
	) implements Authenticator {

		@Override
		public Result authenticate(Socket socket) throws IOException {
			final var input = new DataInputStream(socket.getInputStream());

			final var version = input.readByte();

			final var id = new String(input.readByteArray(), StandardCharsets.US_ASCII);
			final var password = input.readByteArray();

			try {
				final var success = predicate.test(id, password);

				return new Result(success, version);
			} finally {
				Arrays.fill(password, (byte) 0);
			}
		}

		@Override
		public byte getMethod() {
			return 0x02;
		}

	}

}