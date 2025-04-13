package socks4;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Main {

	public static void main(String[] args) throws IOException {
		try (final var server = new ServerSocket(1234)) {
			server.setReuseAddress(true);

			while (true) {
				final var client = server.accept();
				Thread.startVirtualThread(() -> handle(client));
			}
		}
	}

	private static void handle(Socket client) {
		try (client) {
			System.out.println("Client Address: " + client.getInetAddress());

			final var dataInputStream = new DataInputStream(client.getInputStream());

			final var version = dataInputStream.readByte();
			final var command = dataInputStream.readByte();
			final var destinationPort = dataInputStream.readUnsignedShort();
			final var destinationIp = dataInputStream.readNBytes(4);

			final var id = new StringBuilder();
			for (char character = (char) dataInputStream.readByte(); character != 0; character = (char) dataInputStream.readByte()) {
				id.append(character);
			}

			System.out.println("Version: " + version);
			System.out.println("Command: " + command);
			System.out.println("Destination Port: " + destinationPort);
			System.out.println("Destination IP: " + Arrays.toString(toUnsigned(destinationIp)));
			System.out.println("ID: " + id);

			final var dataOutputStream = new DataOutputStream(client.getOutputStream());

			try (final var destination = new Socket()) {
				destination.connect(new InetSocketAddress(Inet4Address.getByAddress(destinationIp), destinationPort));

				dataOutputStream.writeByte(0x00); /* reply version */
				dataOutputStream.writeByte(0x5A); /* reply code: request granted */
				dataOutputStream.writeShort(0); /* destination port */
				dataOutputStream.writeInt(0); /* destination ip */

				final var thread1 = Thread.startVirtualThread(() -> transfer(destination, client));
				final var thread2 = Thread.startVirtualThread(() -> transfer(client, destination));

				thread1.join();
				thread2.join();
			} catch (ConnectException exception) {
				dataOutputStream.writeByte(0x00); /* reply version */
				dataOutputStream.writeByte(0x5B); /* reply code: request rejected */
				dataOutputStream.writeShort(0); /* destination port */
				dataOutputStream.writeInt(0); /* destination ip */
			}
		} catch (IOException | InterruptedException exception) {
			exception.printStackTrace();
		}
	}

	private static void transfer(Socket from, Socket to) {
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

	private static int[] toUnsigned(byte[] bytes) {
		final var ints = new int[bytes.length];

		for (int i = 0; i < bytes.length; i++) {
			ints[i] = Byte.toUnsignedInt(bytes[i]);
		}

		return ints;
	}

}