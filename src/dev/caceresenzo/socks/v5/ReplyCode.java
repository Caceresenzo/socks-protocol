package dev.caceresenzo.socks.v5;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public enum ReplyCode {

	REQUEST_GRANTED(0x00),
	GENERAL_FAILURE(0x01),
	CONNECTION_NOT_ALLOWED_BY_RULESET(0x02),
	NETWORK_UNREACHABLE(0x03),
	HOST_UNREACHABLE(0x04),
	CONNECTION_REFUSED_BY_DESTINATION_HOST(0x05),
	TTL_EXPIRED(0x06),
	COMMAND_NOT_SUPPORTED_OR_PROTOCOL_ERROR(0x07),
	ADDRESS_TYPE_NOT_SUPPORTED(0x08);

	private final byte value;

	private ReplyCode(int value) {
		this.value = (byte) value;
	}

	public boolean successful() {
		return this == REQUEST_GRANTED;
	}

}