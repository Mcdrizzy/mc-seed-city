package net.tabor.seedcity.cell;

import java.util.Locale;

/** Signal direction of a port relative to its cell. */
public enum PortDir {
	IN,
	OUT;

	public static PortDir parse(String s) throws CellFormatException {
		try {
			return valueOf(s.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new CellFormatException("port dir must be in|out, got " + s);
		}
	}
}
