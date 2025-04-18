package dev.caceresenzo.socks;

import java.io.IOException;
import java.time.Duration;

import dev.caceresenzo.socks.v5.Socks5Server;

public class Main {

	public static void main(String[] args) throws IOException, InterruptedException {
		try (final var server = new Socks5Server(1234)) {
			Thread.sleep(Duration.ofMinutes(3));
//			Thread.sleep(Duration.ofSeconds(10));
		}
	}

}