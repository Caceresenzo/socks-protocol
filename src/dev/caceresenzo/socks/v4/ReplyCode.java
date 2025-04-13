package dev.caceresenzo.socks.v4;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public enum ReplyCode {

	REQUEST_GRANTED(0x5A),
	REQUEST_REJECTED_OR_FAILED(0x5B);

	private final byte value;

	private ReplyCode(int value) {
		this.value = (byte) value;
	}

	public boolean successful() {
		return this == REQUEST_GRANTED;
	}

}