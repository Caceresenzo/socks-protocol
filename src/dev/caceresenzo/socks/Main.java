package dev.caceresenzo.socks;

import java.io.IOException;
import java.time.Duration;

import dev.caceresenzo.socks.v4.Socks4Server;

public class Main {

	public static void main(String[] args) throws IOException, InterruptedException {
		try (final var server = new Socks4Server(1234)) {
			Thread.sleep(Duration.ofMinutes(3));
//			Thread.sleep(Duration.ofSeconds(10));
		}
	}

}