package dev.caceresenzo.socks;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import dev.caceresenzo.socks.v5.Authenticator;
import dev.caceresenzo.socks.v5.Socks5Server;

public class Main {

	public static void main(String[] args) throws IOException, InterruptedException {
		final var authenticators = List.<Authenticator>of(
			Authenticator.noAuthentication(),
			Authenticator.usernamePassword((username, password) -> {
				System.out.println("trying to auth %s@%s".formatted(username, new String(password)));
				return true;
			})
		);

		try (final var server = new Socks5Server(1234, authenticators)) {
			Thread.sleep(Duration.ofMinutes(3));
			//			Thread.sleep(Duration.ofSeconds(10));
		}
	}

}