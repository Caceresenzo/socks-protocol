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
import java.nio.charset.StandardCharsets;

import dev.caceresenzo.socks.utility.IOStreams;
import dev.caceresenzo.socks.v4.CommandCode;
import lombok.experimental.ExtensionMethod;

@ExtensionMethod({ IOStreams.class })
public class Socks5Server implements Runnable, AutoCloseable {

	public static final int VERSION = 0x05;

	private final ServerSocket serverSocket;

	private boolean running;

	public Socks5Server(int port) throws IOException {
		serverSocket = new ServerSocket(port);
		serverSocket.setReuseAddress(true);

		running = true;

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

			var version = input.readByte();
			if (VERSION != version) {
				reply(client, ReplyCode.COMMAND_NOT_SUPPORTED_OR_PROTOCOL_ERROR, "invalid version: %x".formatted(version));
				return;
			}

			final var auth = input.readByteArray();
			// TODO Auth

			output.write(VERSION);
			output.write(0x00 /* status: success */);

			version = input.readByte();
			final var command = CommandCode.valueOf(input.readByte());
			input.readByte();

			final var addressType = input.readByte();
			final InetAddress destinationAddress = switch (addressType) {
				case 0x01 -> InetAddress.getByAddress(input.readNBytes(4));
				case 0x03 -> InetAddress.getByName(new String(input.readByteArray(), StandardCharsets.US_ASCII));
				case 0x04 -> InetAddress.getByAddress(input.readNBytes(16));
				default -> null;
			};

			final var destinationPort = input.readPort();

			final var destination = new InetSocketAddress(destinationAddress, destinationPort);

			System.out.format("[%s] DO %s TO [%s]%n", client.getInetAddress(), command, destination);

			proxy(client, destination);
		} catch (IOException exception) {
			exception.printStackTrace();
		}
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