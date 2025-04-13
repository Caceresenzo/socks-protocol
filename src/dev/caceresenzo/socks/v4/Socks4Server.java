package dev.caceresenzo.socks.v4;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import dev.caceresenzo.socks.utility.IOStreams;
import lombok.experimental.ExtensionMethod;

@ExtensionMethod({ IOStreams.class })
public class Socks4Server implements Runnable, AutoCloseable {

	public static final int VERSION = 0x04;

	private final ServerSocket serverSocket;

	private boolean running;

	public Socks4Server(int port) throws IOException {
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
			final var dataInputStream = new DataInputStream(client.getInputStream());

			final var version = dataInputStream.readByte();
			if (VERSION != version) {
				reply(client, ReplyCode.REQUEST_REJECTED_OR_FAILED, "invalid version: %x".formatted(version));
				return;
			}

			final var commandValue = dataInputStream.readByte();
			final var command = CommandCode.valueOf(commandValue);
			if (command == null) {
				reply(client, ReplyCode.REQUEST_REJECTED_OR_FAILED, "invalid command: %x".formatted(commandValue));
				return;
			}

			final var destinationPort = dataInputStream.readPort();
			final var destinationIp = dataInputStream.readNBytes(4);
			final var id = dataInputStream.readNullTerminatedString();

			InetAddress destinationAddress = null;

			final var is4a = destinationIp[0] == 0 && destinationIp[1] == 0 && destinationIp[2] == 0;
			if (is4a) {
				final var destinationHost = dataInputStream.readNullTerminatedString();

				destinationAddress = InetAddress.getByName(destinationHost);
			} else {
				destinationAddress = InetAddress.getByAddress(destinationIp);
			}

			final var destination = new InetSocketAddress(destinationAddress, destinationPort);

			System.out.format("[%s] DO %s TO [%s] AUTH [%s]%n", client.getInetAddress(), command, destination, id);

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
			reply(client, ReplyCode.REQUEST_REJECTED_OR_FAILED, exception.getMessage());
		} catch (InterruptedException __) {
			Thread.currentThread().interrupt();
		}
	}

	public void reply(Socket client, ReplyCode code, String errorMessage) throws IOException {
		final var dataOutputStream = new DataOutputStream(client.getOutputStream());

		dataOutputStream.writeByte(0x00); /* reply version */
		dataOutputStream.writeByte(code.value());
		dataOutputStream.writeShort(0); /* destination port */
		dataOutputStream.writeInt(0); /* destination ip */

		if (!code.successful()) {
			System.out.format("[%s] REPLY %s BECAUSE [%s]%n", client.getInetAddress(), code, errorMessage);
			client.close();
		}
	}

}