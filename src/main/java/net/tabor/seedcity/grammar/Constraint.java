package net.tabor.seedcity.grammar;

import net.minecraft.core.Direction;
import net.tabor.seedcity.cell.Port;

/**
 * What a neighbour presents on one face of a frontier slot: either a port (at the face centre)
 * or a blank wall. Absence of a constraint for a side means no neighbour is there yet.
 *
 * @param side the face of the frontier slot this applies to (pointing toward the neighbour)
 * @param port the neighbour port facing us, or null for a blank wall
 * @param live true when that port is an output that traces back to the clock, so a cell that
 *             listens here will actually do something
 */
public record Constraint(Direction side, Port port, boolean live) {
	public static Constraint wall(Direction side) {
		return new Constraint(side, null, false);
	}

	public boolean hasPort() {
		return port != null;
	}
}
