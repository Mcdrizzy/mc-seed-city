package net.tabor.seedcity.verify;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.cell.CellDefinition;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.cell.PortDir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Interface: Verify (design doc 19, 23). Drives a placed cell through its truth model using the
 * real redstone engine: probe blocks on the input ports, direct signal reads on the outputs,
 * one stimulus per settle window, all across server ticks. Results are cached per
 * (cell, neighbours, rotation) so a verified pairing is never re-run.
 */
public final class Verifier {
	private static final List<Job> JOBS = new ArrayList<>();
	private static final Map<String, Boolean> CACHE = new HashMap<>();

	private Verifier() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(Verifier::tick);
	}

	/**
	 * Starts verifying an already-placed cell. Ports whose outside block lies inside a neighbour
	 * are left alone (the neighbour drives them); all others are probed. The callback fires on
	 * the server thread once the model has judged.
	 */
	public static void verify(ServerLevel level, Placement placement, List<Placement> neighbours, Consumer<VerifyResult> callback) {
		CellDefinition def = placement.cell().definition();
		Optional<TruthModel> model = TruthModels.byName(def.truth());
		if (model.isEmpty()) {
			callback.accept(VerifyResult.fail(def.id(), "unknown truth model " + def.truth()));
			return;
		}
		JOBS.add(new Job(level, placement, neighbours, model.get(), callback));
	}

	public static String cacheKey(Placement p, List<Placement> neighbours) {
		List<String> ids = new ArrayList<>();
		for (Placement n : neighbours) {
			ids.add(n.cell().id().toString());
		}
		ids.sort(String::compareTo);
		return p.cell().id() + "|" + String.join(",", ids) + "|" + p.rotation().getSerializedName();
	}

	public static Optional<Boolean> cached(Placement p, List<Placement> neighbours) {
		return Optional.ofNullable(CACHE.get(cacheKey(p, neighbours)));
	}

	public static void remember(Placement p, List<Placement> neighbours, boolean pass) {
		CACHE.put(cacheKey(p, neighbours), pass);
	}

	public static int activeJobs() {
		return JOBS.size();
	}

	private static void tick(ServerLevel level) {
		if (JOBS.isEmpty()) {
			return;
		}
		Iterator<Job> it = JOBS.iterator();
		while (it.hasNext()) {
			Job job = it.next();
			if (job.level != level) {
				continue;
			}
			try {
				if (job.tick()) {
					it.remove();
				}
			} catch (Exception e) {
				it.remove();
				SeedCity.LOGGER.error("Verifier job crashed for {}", job.placement, e);
				job.restore();
				job.callback.accept(VerifyResult.fail(job.placement.cell().id(), "verifier error: " + e));
			}
		}
	}

	private static final class Job {
		final ServerLevel level;
		final Placement placement;
		final List<Placement> neighbours;
		final TruthModel model;
		final Consumer<VerifyResult> callback;
		final CellDefinition def;
		final List<Map<String, Integer>> stimuli;
		final List<Placement.WorldPort> probed = new ArrayList<>();
		final List<Placement.WorldPort> outputs = new ArrayList<>();
		final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
		final List<Sample> samples = new ArrayList<>();
		final List<Map<String, Integer>> trace = new ArrayList<>();
		int step = -1;
		int wait = 0;
		boolean prepared = false;

		Job(ServerLevel level, Placement placement, List<Placement> neighbours, TruthModel model, Consumer<VerifyResult> callback) {
			this.level = level;
			this.placement = placement;
			this.neighbours = List.copyOf(neighbours);
			this.model = model;
			this.callback = callback;
			this.def = placement.cell().definition();
			this.stimuli = model.stimuli(def);
		}

		/** Returns true when finished. */
		boolean tick() {
			if (!prepared) {
				prepare();
				prepared = true;
				if (stimuli.isEmpty()) {
					return finish();
				}
				advance();
				return false;
			}
			trace.add(readOutputs());
			if (--wait > 0) {
				return false;
			}
			samples.add(new Sample(stimuli.get(step), List.copyOf(trace), readOutputs(), snapshot()));
			trace.clear();
			if (step + 1 >= stimuli.size()) {
				return finish();
			}
			advance();
			return false;
		}

		private void prepare() {
			List<BoundingBox> occupied = new ArrayList<>();
			for (Placement n : neighbours) {
				occupied.add(n.footprint());
			}
			for (Placement.WorldPort wp : placement.ports()) {
				BlockPos outside = wp.outside();
				boolean owned = occupied.stream().anyMatch(b -> b.isInside(outside));
				if (owned) {
					continue;
				}
				saved.putIfAbsent(outside, level.getBlockState(outside));
				if (wp.port().dir() == PortDir.IN) {
					probed.add(wp);
					drive(wp, 0);
				} else {
					outputs.add(wp);
					level.setBlock(outside, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			for (Placement.WorldPort wp : placement.ports()) {
				if (wp.port().dir() == PortDir.OUT && !outputs.contains(wp)) {
					outputs.add(wp);
				}
			}
		}

		private void advance() {
			step++;
			Map<String, Integer> inputs = stimuli.get(step);
			for (Placement.WorldPort wp : probed) {
				drive(wp, inputs.getOrDefault(wp.port().name(), 0));
			}
			wait = Math.max(1, model.settleTicks(def));
		}

		private void drive(Placement.WorldPort wp, int level0) {
			BlockState probe = SeedCityBlocks.PROBE.defaultBlockState().setValue(ProbeBlock.POWER, Math.max(0, Math.min(15, level0)));
			level.setBlock(wp.outside(), probe, Block.UPDATE_ALL);
		}

		private Map<String, Integer> readOutputs() {
			Map<String, Integer> out = new LinkedHashMap<>();
			for (Placement.WorldPort wp : outputs) {
				out.put(wp.port().name(), level.getSignal(wp.pos(), wp.face().getOpposite()));
			}
			return out;
		}

		private Map<BlockPos, BlockState> snapshot() {
			Map<BlockPos, BlockState> snap = new HashMap<>();
			BoundingBox box = placement.footprint();
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				snap.put(p.immutable(), level.getBlockState(p));
			}
			return snap;
		}

		void restore() {
			for (Map.Entry<BlockPos, BlockState> e : saved.entrySet()) {
				level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
			}
			saved.clear();
		}

		private boolean finish() {
			restore();
			Optional<String> problem = model.judge(def, samples);
			VerifyResult result = problem.map(m -> VerifyResult.fail(def.id(), m)).orElseGet(() -> VerifyResult.pass(def.id()));
			remember(placement, neighbours, result.pass());
			callback.accept(result);
			return true;
		}
	}
}
