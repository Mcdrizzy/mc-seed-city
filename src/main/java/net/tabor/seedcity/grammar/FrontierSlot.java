package net.tabor.seedcity.grammar;

import java.util.Set;

/**
 * An unbuilt slot adjacent to the city, described in grid coordinates relative to the core.
 *
 * @param district weight column the grammar reads ("core", "residential", "plaza", "forge")
 * @param rejected cell/rotation pairs that already failed verification here
 */
public record FrontierSlot(int x, int z, String district, Set<String> rejected) {
	public static String rejectKey(String cellId, int rotationOrdinal) {
		return cellId + "/" + rotationOrdinal;
	}
}
