package dev.caceresenzo.socks.v5;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.caceresenzo.socks.utility.IOStreams;
import dev.caceresenzo.socks.v4.CommandCode;
import lombok.experimental.ExtensionMethod;

@ExtensionMethod({ IOStreams.class })
public class Socks5Server implements Runnable, AutoCloseable {

	public static final int VERSION = 0x05;

	private final ServerSocket serverSocket;
	private final Map<Byte, Authenticator> authenticators;

	private boolean running;

	public Socks5Server(int port, List<Authenticator> authenticators) throws IOException {
		this.serverSocket = new ServerSocket(port);
		this.serverSocket.setReuseAddress(true);

		this.authenticators = authenticators.stream()
			.collect(Collectors.toMap(
				Authenticator::getMethod,
				Function.identity()
			));

		this.running = true;

		Thread.startVirtualThread(this);
	}

	@Override
	public void run() {
		while (running) {
			try {
				final var client = serverSocket.accept();

				Thread.startVirtualThread(() -> handle(client));
			} catch (IOException exception) {
				if (running) {
					exception.printStackTrace();
				}
			}
		}
	}

	@Override
	public void close() throws IOException {
		running = false;

		serverSocket.close();
	}

	public void handle(Socket client) {
		try (client) {
			final var input = new DataInputStream(client.getInputStream());
			final var output = new DataOutputStream(client.getOutputStream());

			if (!readVersion(input, client)) {
				return;
			}

			Authenticator authenticator = null;
			Authenticator.Result result = null;

			final var auth = input.readByteArray();
			for (final var possibleAuthentication : auth) {
				authenticator = authenticators.get(possibleAuthentication);
				if (authenticator != null) {
					output.writeByte(VERSION);
					output.writeByte(authenticator.getMethod());

					result = authenticator.authenticate(client);
					break;
				}
			}

			if (authenticator == null) {
				output.writeByte(VERSION);
				output.writeByte(0xff /* choice: no acceptable methods */);
				return;
			} else if (authenticator instanceof Authenticator.NoAuthentication) {
				;
			} else if (result.success()) {
				output.writeByte(result.version());
				output.writeByte(0x00 /* status: success */);
			} else {
				output.writeByte(result.version());
				output.writeByte(0x01 /* status: failure */);
				return;
			}

			if (!readVersion(input, client)) {
				return;
			}

			final var commandValue = input.readByte();
			final var command = CommandCode.valueOf(commandValue);
			if (command == null) {
				reply(client, ReplyCode.COMMAND_NOT_SUPPORTED_OR_PROTOCOL_ERROR, "invalid command: %x".formatted(commandValue));
				return;
			}

			input.readByte(); /* reserved */

			final var addressType = input.readByte();
			final InetAddress destinationAddress = switch (addressType) {
				case 0x01 -> InetAddress.getByAddress(input.readNBytes(4));
				case 0x03 -> {
					final var host = new String(input.readByteArray(), StandardCharsets.US_ASCII);

					try {
						yield InetAddress.getByName(host);
					} catch (UnknownHostException exception) {
						reply(client, ReplyCode.HOST_UNREACHABLE, exception.getMessage());
						yield null;
					}
				}
				case 0x04 -> InetAddress.getByAddress(input.readNBytes(16));
				default -> null;
			};

			if (destinationAddress == null) {
				reply(client, ReplyCode.ADDRESS_TYPE_NOT_SUPPORTED, "invalid address type: %x".formatted(addressType));
				return;
			}

			final var destinationPort = input.readPort();

			final var destination = new InetSocketAddress(destinationAddress, destinationPort);

			System.out.format("[%s] DO %s TO [%s]%n", client.getInetAddress(), command, destination);

			proxy(client, destination);
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}

	private boolean readVersion(DataInputStream input, Socket client) throws IOException {
		var version = input.readByte();

		if (VERSION != version) {
			reply(client, ReplyCode.COMMAND_NOT_SUPPORTED_OR_PROTOCOL_ERROR, "invalid version: %x".formatted(version));
			return false;
		}

		return true;
	}

	private void proxy(Socket client, SocketAddress destinationAddress) throws IOException {
		try (final var destination = new Socket()) {
			destination.connect(destinationAddress);

			reply(client, ReplyCode.REQUEST_GRANTED, null);

			final var thread1 = Thread.startVirtualThread(() -> destination.transfer(client));
			client.transfer(destination);

			thread1.join();
		} catch (ConnectException exception) {
			reply(client, ReplyCode.HOST_UNREACHABLE, exception.getMessage());
		} catch (InterruptedException __) {
			Thread.currentThread().interrupt();
		}
	}

	public void reply(Socket client, ReplyCode code, String errorMessage) throws IOException {
		if (client.isClosed()) {
			return;
		}

		final var output = new DataOutputStream(client.getOutputStream());

		output.writeByte(VERSION);
		output.writeByte(code.value());
		output.writeByte(0); /* reserved */
		output.writeByte(0x01); /* destination ip type */
		output.writeInt(0); /* destination ip */
		output.writeShort(0); /* destination port */

		if (!code.successful()) {
			System.out.format("[%s] REPLY %s BECAUSE [%s]%n", client.getInetAddress(), code, errorMessage);
			client.close();
		}
	}

}