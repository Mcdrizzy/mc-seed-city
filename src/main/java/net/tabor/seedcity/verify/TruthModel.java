package net.tabor.seedcity.verify;

import net.tabor.seedcity.cell.CellDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A named behavioural contract a cell claims to satisfy (design doc 22.3). The verifier drives
 * each stimulus in order, waits for the cell to settle, samples, and finally asks the model to
 * judge the whole sequence. Models are pure and stateless between runs.
 */
public interface TruthModel {
	String name();

	/** Input vectors to drive, in order. Each map is port name to 0-15. Empty map = drive nothing. */
	List<Map<String, Integer>> stimuli(CellDefinition def);

	/** Ticks to wait after each stimulus before sampling. Default comes from the sidecar. */
	default int settleTicks(CellDefinition def) {
		return def.settleTicks();
	}

	/** Empty when the cell passes; otherwise a message that names a port where possible. */
	Optional<String> judge(CellDefinition def, List<Sample> samples);

	/** Levels a 4-bit port is exercised at (design doc 22.3). 1-bit ports use 0 and 15. */
	int[] FOUR_BIT_LEVELS = {0, 1, 7, 15};
	int[] ONE_BIT_LEVELS = {0, 15};

	static int[] levelsFor(int bits) {
		return bits == 4 ? FOUR_BIT_LEVELS : ONE_BIT_LEVELS;
	}

	/** What a 1-bit output should read for a logical value. */
	static int bit(boolean on) {
		return on ? 15 : 0;
	}

	static boolean on(int level) {
		return level > 0;
	}
}
