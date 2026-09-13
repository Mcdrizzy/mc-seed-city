package net.tabor.seedcity.grammar;

import net.tabor.seedcity.cell.CellKind;

/**
 * A build request that biases the grammar. Cards resolve NEED/OUT/IN into goals in Phase 3; in
 * Phase 1 the city uses one soft goal to make sure its default program has something to move.
 *
 * @param kind      the cell kind wanted
 * @param weightMul how strongly to prefer it (1 = no preference)
 */
public record Goal(CellKind kind, double weightMul) {
	public static final Goal WANT_ACTUATOR = new Goal(CellKind.ACTUATOR, 6.0);
}
