package net.tabor.seedcity.cell;

import java.util.Locale;

/** What role a cell plays in the city. Mirrors the sidecar "kind" field. */
public enum CellKind {
	LOGIC,
	ACTUATOR,
	SENSOR,
	STORAGE,
	DECOR,
	CORE;

	public static CellKind parse(String s) throws CellFormatException {
		try {
			return valueOf(s.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new CellFormatException("kind must be logic|actuator|sensor|storage|decor|core, got " + s);
		}
	}
}
