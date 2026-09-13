package net.tabor.seedcity.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * One observation the verifier hands to a truth model.
 *
 * @param inputs   the port values that were driven for this step (port name to 0-15)
 * @param trace    output port readings on every tick of the settle window, oldest first
 * @param outputs  output port readings at the end of the settle window
 * @param snapshot block states inside the footprint at the end of the settle window,
 *                 keyed by cell-local position; used by actuator models
 */
public record Sample(
		Map<String, Integer> inputs,
		List<Map<String, Integer>> trace,
		Map<String, Integer> outputs,
		Map<BlockPos, BlockState> snapshot
) {
	public int out(String port) {
		Integer v = outputs.get(port);
		return v == null ? -1 : v;
	}

	public int in(String port) {
		Integer v = inputs.get(port);
		return v == null ? 0 : v;
	}
}
