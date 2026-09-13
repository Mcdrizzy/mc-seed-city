package net.tabor.seedcity.cell;

/** A sidecar or structure file is malformed. Reported, never propagated into world code. */
public final class CellFormatException extends Exception {
	public CellFormatException(String message) {
		super(message);
	}

	public CellFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
