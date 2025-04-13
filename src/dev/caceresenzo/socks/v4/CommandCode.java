package dev.caceresenzo.socks.v4;

public enum CommandCode {

	CONNECT,
	BIND;

	public static CommandCode valueOf(byte value) {
		return switch (value) {
			case 0x01 -> CONNECT;
			case 0x02 -> BIND;
			default -> null;
		};
	}

}