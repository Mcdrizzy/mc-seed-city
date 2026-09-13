package net.tabor.seedcity.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.tabor.seedcity.SeedCity;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.build.BuildTask;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellDefinition;
import net.tabor.seedcity.cell.CellKind;
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.cell.Port;
import net.tabor.seedcity.cell.PortDir;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.entity.BuilderEntity;
import net.tabor.seedcity.entity.SeedCityEntities;
import net.tabor.seedcity.grammar.Choice;
import net.tabor.seedcity.grammar.Constraint;
import net.tabor.seedcity.grammar.FrontierSlot;
import net.tabor.seedcity.grammar.Goal;
import net.tabor.seedcity.grammar.Grammar;
import net.tabor.seedcity.verify.Verifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One city (design doc 13, 20.3, 23): the seed origin, the slot grid and what is planned or built
 * in it, the material stock, and the frontier. Entities only ever consume tasks from here.
 *
 * <p>The layout is decided by a planner that walks the frontier in a fixed order (distance from
 * the core, then x, then z) and seeds the grammar per slot from the city seed, so the city that
 * grows depends only on (world seed, seed block position), never on builder timing.
 */
public final class CityState {
	public static final int SLOT = 7;
	public static final Identifier CORE_CELL = SeedCity.id("core");
	public static final Identifier CLOCK_CELL = SeedCity.id("clock_tower");
	public static final Identifier JUNCTION_CELL = SeedCity.id("junction");
	private static final int LOOKAHEAD = 2;

	public record SlotKey(int x, int z) {
		public SlotKey offset(Direction d) {
			return new SlotKey(x + d.getStepX(), z + d.getStepZ());
		}

		public int chebyshev() {
			return Math.max(Math.abs(x), Math.abs(z));
		}

		@Override
		public String toString() {
			return "(" + x + "," + z + ")";
		}
	}

	public enum SlotStatus {
		/** In the map (keeps its rejection list) but needs a cell chosen. */
		PENDING,
		PLANNED,
		BUILDING,
		/** All blocks placed; waiting for, or undergoing, verification. */
		VERIFY,
		BUILT,
		BLOCKED;

		static SlotStatus parse(String s) {
			try {
				return valueOf(s.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return PENDING;
			}
		}
	}

	public static final class Slot {
		public final SlotKey key;
		public SlotStatus status;
		public Identifier cell;
		public Rotation rotation = Rotation.NONE;
		public final Set<String> rejected = new LinkedHashSet<>();
		public long since;
		public String note = "";
		/** Builder currently working here. Not persisted; a reload re-queues the slot. */
		public UUID builder;
		/** Verification attempts spent on the current cell. Not persisted. */
		public int retries;

		Slot(SlotKey key, SlotStatus status) {
			this.key = key;
			this.status = status;
		}

		boolean occupies() {
			return status == SlotStatus.PLANNED || status == SlotStatus.BUILDING || status == SlotStatus.VERIFY || status == SlotStatus.BUILT;
		}

		@Override
		public String toString() {
			return key + " " + status + (cell == null ? "" : " " + cell.getPath() + "/" + rotation.getSerializedName());
		}
	}

	private record SlotData(int x, int z, String status, Optional<Identifier> cell, Rotation rotation, List<String> rejected, long since, String note) {
		static final Codec<SlotData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("x").forGetter(SlotData::x),
				Codec.INT.fieldOf("z").forGetter(SlotData::z),
				Codec.STRING.fieldOf("status").forGetter(SlotData::status),
				Identifier.CODEC.optionalFieldOf("cell").forGetter(SlotData::cell),
				Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(SlotData::rotation),
				Codec.STRING.listOf().optionalFieldOf("rejected", List.of()).forGetter(SlotData::rejected),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(SlotData::since),
				Codec.STRING.optionalFieldOf("note", "").forGetter(SlotData::note)
		).apply(i, SlotData::new));

		static SlotData of(Slot s) {
			return new SlotData(s.key.x(), s.key.z(), s.status.name(), Optional.ofNullable(s.cell), s.rotation, List.copyOf(s.rejected), s.since, s.note);
		}

		Slot toSlot() {
			Slot s = new Slot(new SlotKey(x, z), SlotStatus.parse(status));
			s.cell = cell.orElse(null);
			s.rotation = rotation;
			s.rejected.addAll(rejected);
			s.since = since;
			s.note = note;
			if (s.status == SlotStatus.BUILDING || s.status == SlotStatus.VERIFY) {
				s.status = SlotStatus.PLANNED;   // re-run the task after a reload; correct blocks are skipped
			}
			return s;
		}
	}

	public static final Codec<CityState> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("seed").forGetter(c -> c.seedPos),
			Codec.LONG.fieldOf("city_seed").forGetter(c -> c.citySeed),
			Codec.BOOL.fieldOf("frozen").forGetter(c -> c.frozen),
			Codec.INT.fieldOf("built").forGetter(c -> c.builtCount),
			Codec.INT.fieldOf("redstone").forGetter(c -> c.redstone),
			Codec.INT.fieldOf("stone").forGetter(c -> c.stone),
			Codec.INT.fieldOf("wood").forGetter(c -> c.wood),
			SlotData.CODEC.listOf().fieldOf("slots").forGetter(c -> c.slots.values().stream().map(SlotData::of).toList())
	).apply(i, CityState::new));

	private final BlockPos seedPos;
	private final long citySeed;
	private boolean frozen;
	private int builtCount;
	private int redstone;
	private int stone;
	private int wood;
	private final Map<SlotKey, Slot> slots = new LinkedHashMap<>();
	/** Finished cells waiting for their turn under the verifier. Not persisted. */
	private final List<BuildTask> pendingVerify = new ArrayList<>();
	private SlotKey verifyingSlot;

	private CityState(BlockPos seedPos, long citySeed, boolean frozen, int builtCount, int redstone, int stone, int wood, List<SlotData> slotData) {
		this.seedPos = seedPos;
		this.citySeed = citySeed;
		this.frozen = frozen;
		this.builtCount = builtCount;
		this.redstone = redstone;
		this.stone = stone;
		this.wood = wood;
		for (SlotData d : slotData) {
			Slot s = d.toSlot();
			slots.put(s.key, s);
		}
	}

	/**
	 * A fresh city rooted at a Seed. The core, the clock tower south of it, and a junction on the
	 * clock's output are planned by force: the clock has one output, and a dead end there would
	 * cap the whole city's signal at one cell.
	 */
	public static CityState create(long worldSeed, BlockPos seedPos, SeedCityConfig cfg) {
		long citySeed = cfg.citySeedOverride != 0 ? cfg.citySeedOverride : mix(worldSeed ^ seedPos.asLong());
		CityState c = new CityState(seedPos, citySeed, false, 0, cfg.initialRedstone, cfg.initialStone, cfg.initialWood, List.of());
		c.forceRoot();
		return c;
	}

	private void forceRoot() {
		force(new SlotKey(0, 0), CORE_CELL, Rotation.NONE);
		force(new SlotKey(0, 1), CLOCK_CELL, Rotation.NONE);
		force(new SlotKey(0, 2), JUNCTION_CELL, Rotation.NONE);
	}

	/** A throwaway city used to prove the planner is deterministic. */
	public static List<String> previewPlan(long citySeed, int count, SeedCityConfig cfg) {
		CityState c = new CityState(BlockPos.ZERO, citySeed, false, 0, cfg.initialRedstone, cfg.initialStone, cfg.initialWood, List.of());
		c.forceRoot();
		c.planAhead(k -> true, count, cfg);
		List<String> out = new ArrayList<>();
		for (Slot s : c.slots.values()) {
			out.add(s.toString());
		}
		return out;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private void force(SlotKey key, Identifier cell, Rotation rotation) {
		Slot s = new Slot(key, SlotStatus.PLANNED);
		s.cell = cell;
		s.rotation = rotation;
		slots.put(key, s);
	}

	// ---- geometry ---------------------------------------------------------------------------

	public BlockPos seedPos() {
		return seedPos;
	}

	public long citySeed() {
		return citySeed;
	}

	public boolean frozen() {
		return frozen;
	}

	public void freeze() {
		frozen = true;
	}

	public int builtCount() {
		return builtCount;
	}

	public BlockPos coreOrigin() {
		return seedPos.offset(-3, -1, -3);
	}

	public BlockPos slotOrigin(SlotKey k) {
		return coreOrigin().offset(k.x() * SLOT, 0, k.z() * SLOT);
	}

	public Optional<Slot> slot(SlotKey k) {
		return Optional.ofNullable(slots.get(k));
	}

	public Iterable<Slot> slots() {
		return slots.values();
	}

	public Optional<Placement> placement(Slot s) {
		if (s.cell == null) {
			return Optional.empty();
		}
		return CellLibrary.get(s.cell).map(cell -> {
			BlockPos origin = slotOrigin(s.key).offset(Cell.rotationShift(s.rotation, cell.size()));
			return new Placement(cell, origin, s.rotation);
		});
	}

	public String district(SlotKey k) {
		int d = k.chebyshev();
		if (d <= 1) {
			return "core";
		}
		return d == 2 ? "plaza" : "residential";
	}

	// ---- planning ---------------------------------------------------------------------------

	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	private Port portFacing(Slot n, Direction sideFromUs) {
		Optional<Cell> cell = CellLibrary.get(n.cell);
		if (cell.isEmpty()) {
			return null;
		}
		return Grammar.portOn(cell.get().ports(n.rotation), sideFromUs.getOpposite());
	}

	/** What the neighbours of a slot present on each shared face. */
	public List<Constraint> constraints(SlotKey k) {
		return constraints(k, liveSlots(false, false));
	}

	private List<Constraint> constraints(SlotKey k, Set<SlotKey> clocked) {
		List<Constraint> out = new ArrayList<>();
		for (Direction side : SIDES) {
			Slot n = slots.get(k.offset(side));
			if (n == null || !n.occupies() || n.cell == null) {
				continue;
			}
			Port facingUs = portFacing(n, side);
			if (facingUs == null) {
				out.add(Constraint.wall(side));
			} else {
				boolean live = facingUs.dir() == PortDir.OUT && clocked.contains(n.key);
				out.add(new Constraint(side, facingUs, live));
			}
		}
		return out;
	}

	/** Slots whose signal traces back to the clock tower. */
	public Set<SlotKey> clockedSlots(boolean builtOnly) {
		return liveSlots(builtOnly, true);
	}

	/**
	 * Slots that carry a signal: sources (the clock; sensors too unless {@code clockOnly}) and any
	 * cell with an input mated to the output of a live cell. A fixpoint over the planned city, so
	 * the planner can prefer placements that will actually do something.
	 */
	public Set<SlotKey> liveSlots(boolean builtOnly, boolean clockOnly) {
		Set<SlotKey> clocked = new HashSet<>();
		List<Slot> eligible = new ArrayList<>();
		for (Slot s : slots.values()) {
			boolean ok = builtOnly ? s.status == SlotStatus.BUILT : s.occupies();
			if (ok && s.cell != null && CellLibrary.get(s.cell).isPresent()) {
				eligible.add(s);
				CellDefinition def = CellLibrary.get(s.cell).get().definition();
				if ("clock".equals(def.truth()) || (!clockOnly && def.kind() == CellKind.SENSOR)) {
					clocked.add(s.key);
				}
			}
		}
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Slot s : eligible) {
				if (clocked.contains(s.key)) {
					continue;
				}
				Cell cell = CellLibrary.get(s.cell).get();
				for (Direction side : SIDES) {
					Port ours = Grammar.portOn(cell.ports(s.rotation), side);
					if (ours == null || ours.dir() != PortDir.IN) {
						continue;
					}
					Slot n = slots.get(s.key.offset(side));
					if (n == null || !clocked.contains(n.key)) {
						continue;
					}
					Port theirs = portFacing(n, side);
					if (theirs != null && theirs.dir() == PortDir.OUT && ours.compatibleWith(theirs)) {
						clocked.add(s.key);
						changed = true;
						break;
					}
				}
			}
		}
		return clocked;
	}

	private boolean hasClockedActuator(boolean builtOnly) {
		Set<SlotKey> clocked = clockedSlots(builtOnly);
		for (Slot s : slots.values()) {
			if (s.cell == null || !clocked.contains(s.key)) {
				continue;
			}
			Optional<Cell> cell = CellLibrary.get(s.cell);
			if (cell.isPresent() && cell.get().definition().kind() == CellKind.ACTUATOR) {
				return true;
			}
		}
		return false;
	}

	/** An actuator is built and its input traces back to the built clock tower: it cycles. */
	public boolean hasClockedActuatorBuilt() {
		return hasClockedActuator(true);
	}

	/**
	 * The next slot to plan. Slots that a live output already points at come first (the clock
	 * network grows like a root system before the quiet districts fill in), then pending re-plans,
	 * then the rest by distance from the core, x, z. Deterministic.
	 */
	private Optional<SlotKey> nextFrontier(int maxRadius, Set<SlotKey> clocked) {
		SlotKey bestLive = null;
		SlotKey bestPending = null;
		SlotKey bestAny = null;
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.PENDING) {
				bestPending = better(bestPending, s.key);
				if (fedByLive(s.key, clocked)) {
					bestLive = better(bestLive, s.key);
				}
			}
		}
		for (Slot s : slots.values()) {
			if (!s.occupies()) {
				continue;
			}
			for (Direction d : SIDES) {
				SlotKey k = s.key.offset(d);
				if (slots.containsKey(k) || k.chebyshev() > maxRadius) {
					continue;
				}
				bestAny = better(bestAny, k);
				if (fedByLive(k, clocked)) {
					bestLive = better(bestLive, k);
				}
			}
		}
		if (bestLive != null) {
			return Optional.of(bestLive);
		}
		if (bestPending != null) {
			return Optional.of(bestPending);
		}
		return Optional.ofNullable(bestAny);
	}

	/** True when a clocked neighbour has an output port on the face shared with this slot. */
	private boolean fedByLive(SlotKey k, Set<SlotKey> clocked) {
		for (Direction side : SIDES) {
			Slot n = slots.get(k.offset(side));
			if (n == null || !clocked.contains(n.key)) {
				continue;
			}
			Port p = portFacing(n, side);
			if (p != null && p.dir() == PortDir.OUT) {
				return true;
			}
		}
		return false;
	}

	private static SlotKey better(SlotKey a, SlotKey b) {
		if (a == null) {
			return b;
		}
		int c = Integer.compare(a.chebyshev(), b.chebyshev());
		if (c == 0) {
			c = Integer.compare(a.x(), b.x());
		}
		if (c == 0) {
			c = Integer.compare(a.z(), b.z());
		}
		return c <= 0 ? a : b;
	}

	private int unassignedPlanned() {
		int n = 0;
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.PLANNED && s.builder == null) {
				n++;
			}
		}
		return n;
	}

	/** Plans slots ahead of the builders until {@code lookahead} are waiting or the frontier is spent. */
	void planAhead(Predicate<SlotKey> buildable, int lookahead, SeedCityConfig cfg) {
		int guard = 0;
		while (unassignedPlanned() < lookahead && guard++ < 512) {
			Set<SlotKey> clocked = clockedSlots(false);
			Set<SlotKey> live = liveSlots(false, false);
			Optional<SlotKey> next = nextFrontier(cfg.maxRadiusSlots, live);
			if (next.isEmpty()) {
				return;
			}
			SlotKey k = next.get();
			Slot s = slots.computeIfAbsent(k, key -> new Slot(key, SlotStatus.PENDING));
			if (!buildable.test(k)) {
				s.status = SlotStatus.BLOCKED;
				s.note = "not buildable";
				continue;
			}
			Random rng = new Random(mix(citySeed ^ (k.x() * 0x9E3779B97F4A7C15L) ^ (k.z() * 0xC2B2AE3D27D4EB4FL)));
			boolean haveActuator = false;
			for (Slot o : slots.values()) {
				if (o.cell != null && clocked.contains(o.key) && CellLibrary.get(o.cell).map(c -> c.definition().kind() == CellKind.ACTUATOR).orElse(false)) {
					haveActuator = true;
					break;
				}
			}
			Optional<Goal> goal = haveActuator ? Optional.empty() : Optional.of(Goal.WANT_ACTUATOR);
			FrontierSlot fs = new FrontierSlot(k.x(), k.z(), district(k), Set.copyOf(s.rejected));
			Optional<Choice> choice = Grammar.choose(fs, constraints(k, live), goal, rng, CellLibrary.all());
			if (choice.isEmpty()) {
				s.status = SlotStatus.BLOCKED;
				s.note = "no legal cell";
				continue;
			}
			s.status = SlotStatus.PLANNED;
			s.cell = choice.get().cell().id();
			s.rotation = choice.get().rotation();
			s.note = "";
		}
	}

	/**
	 * A slot is buildable when its floor layer is solid and everything above it up to the tallest
	 * cell is air (or the Seed itself). Anything else, including player builds, blocks the slot.
	 */
	public boolean buildable(ServerLevel level, SlotKey k) {
		BlockPos origin = slotOrigin(k);
		int maxHeight = 1;
		for (Cell c : CellLibrary.all()) {
			maxHeight = Math.max(maxHeight, c.size().getY());
		}
		for (int x = 0; x < SLOT; x++) {
			for (int z = 0; z < SLOT; z++) {
				BlockPos floor = origin.offset(x, 0, z);
				BlockState f = level.getBlockState(floor);
				if (f.isAir() || !f.isCollisionShapeFullBlock(level, floor)) {
					return false;
				}
				for (int y = 1; y < maxHeight; y++) {
					BlockPos p = floor.above(y);
					BlockState st = level.getBlockState(p);
					if (st.isAir() || st.canBeReplaced() || st.is(SeedCityBlocks.PROBE)) {
						continue;   // probes are ours and transient
					}
					if (p.equals(seedPos) && st.is(SeedCityBlocks.SEED)) {
						continue;
					}
					return false;
				}
			}
		}
		return true;
	}

	// ---- tasks ------------------------------------------------------------------------------

	private boolean affordable(BuildTask.Cost c, SeedCityConfig cfg) {
		return cfg.unlimitedMaterials || (redstone >= c.redstone() && stone >= c.stone() && wood >= c.wood());
	}

	private void spend(BuildTask.Cost c, int sign) {
		redstone += sign * -c.redstone();
		stone += sign * -c.stone();
		wood += sign * -c.wood();
	}

	/**
	 * Hands the next task to a builder in priority order, or nothing if the city is frozen, the
	 * frontier is spent, or the stock cannot cover the next cell (growth stalls).
	 */
	public Optional<BuildTask> claimTask(ServerLevel level, UUID builder) {
		if (frozen) {
			return Optional.empty();
		}
		SeedCityConfig cfg = SeedCityConfig.get();
		if (chunkSpan() >= cfg.maxChunks) {
			return Optional.empty();
		}
		for (int attempt = 0; attempt < 8; attempt++) {
			planAhead(k -> buildable(level, k), LOOKAHEAD, cfg);
			Slot pick = null;
			for (Slot s : slots.values()) {
				if (s.status == SlotStatus.PLANNED && s.builder == null && !adjacentToVerification(s.key)) {
					pick = pick == null || better(pick.key, s.key) == s.key ? s : pick;
				}
			}
			if (pick == null) {
				return Optional.empty();
			}
			if (!buildable(level, pick.key)) {
				pick.status = SlotStatus.BLOCKED;
				pick.note = "not buildable";
				continue;
			}
			Optional<Placement> placement = placement(pick);
			if (placement.isEmpty()) {
				pick.status = SlotStatus.BLOCKED;
				pick.note = "cell " + pick.cell + " not in library";
				continue;
			}
			BuildTask task = new BuildTask(placement.get(), pick.key);
			if (!affordable(task.cost(), cfg)) {
				return Optional.empty();
			}
			spend(task.cost(), 1);
			pick.status = SlotStatus.BUILDING;
			pick.builder = builder;
			pick.since = level.getGameTime();
			return Optional.of(task);
		}
		return Optional.empty();
	}

	public void abandon(BuildTask task) {
		Slot s = slots.get(task.slot());
		if (s != null && s.status == SlotStatus.BUILDING) {
			s.status = SlotStatus.PLANNED;
			s.builder = null;
			spend(task.cost(), -1);
		}
	}

	/** A builder has placed every block. The cell now queues for verification; the builder moves on. */
	public void onBuildComplete(BuildTask task) {
		Slot s = slots.get(task.slot());
		if (s != null && s.status == SlotStatus.BUILDING) {
			s.status = SlotStatus.VERIFY;
			s.builder = null;
			pendingVerify.add(task);
		}
	}

	private boolean adjacentToVerification(SlotKey k) {
		if (verifyingSlot == null) {
			return false;
		}
		return Math.abs(verifyingSlot.x() - k.x()) + Math.abs(verifyingSlot.z() - k.z()) == 1;
	}

	private boolean neighbourUnderConstruction(SlotKey k) {
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
			Slot n = slots.get(k.offset(d));
			if (n != null && n.status == SlotStatus.BUILDING) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Starts the next queued verification when none is running and no builder is working next to
	 * the candidate, so probes and builders never touch the same blocks. One at a time per city.
	 */
	private void processVerification(ServerLevel level) {
		if (verifyingSlot != null || pendingVerify.isEmpty()) {
			return;
		}
		for (BuildTask task : pendingVerify) {
			if (neighbourUnderConstruction(task.slot())) {
				continue;
			}
			Slot s = slots.get(task.slot());
			if (s == null || s.status != SlotStatus.VERIFY) {
				pendingVerify.remove(task);
				return;
			}
			pendingVerify.remove(task);
			verifyingSlot = task.slot();
			Verifier.verify(level, task.placement(), builtNeighbours(task.slot()), result -> {
				verifyingSlot = null;
				if (result.pass()) {
					onBuilt(task);
				} else if (s.retries++ < 1) {
					// one more try: a neighbour may have been mid-change when we sampled
					SeedCity.LOGGER.info("City {}: {} at {} failed once ({}); retrying", seedPos.toShortString(), s.cell, s.key, result.message());
					pendingVerify.add(task);
				} else {
					task.placement().clear(level, true);
					onVerifyFailed(task, result.message());
				}
			});
			return;
		}
	}

	private void onBuilt(BuildTask task) {
		Slot s = slots.get(task.slot());
		if (s != null) {
			s.status = SlotStatus.BUILT;
			s.builder = null;
			builtCount++;
		}
	}

	/** The cell failed in place: remember the rejection, re-plan this slot and any unbuilt dependants. */
	private void onVerifyFailed(BuildTask task, String reason) {
		Slot s = slots.get(task.slot());
		if (s == null) {
			return;
		}
		SeedCity.LOGGER.warn("City {}: {} at {} failed verification ({}); re-planning", seedPos.toShortString(), s.cell, s.key, reason);
		s.rejected.add(FrontierSlot.rejectKey(s.cell.toString(), s.rotation.ordinal()));
		s.status = SlotStatus.PENDING;
		s.cell = null;
		s.builder = null;
		s.retries = 0;
		spend(task.cost(), -1);
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
			Slot n = slots.get(s.key.offset(d));
			if (n != null && n.status == SlotStatus.PLANNED && n.builder == null) {
				n.status = SlotStatus.PENDING;
				n.cell = null;
			}
		}
	}

	public List<Placement> builtNeighbours(SlotKey k) {
		List<Placement> out = new ArrayList<>();
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
			Slot n = slots.get(k.offset(d));
			if (n != null && n.status == SlotStatus.BUILT) {
				placement(n).ifPresent(out::add);
			}
		}
		return out;
	}

	/** Where a builder fetches material: the nearest built storage cell, else the core. */
	public BlockPos storageTarget(BlockPos from) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (Slot s : slots.values()) {
			if (s.status != SlotStatus.BUILT || s.cell == null) {
				continue;
			}
			Optional<Cell> cell = CellLibrary.get(s.cell);
			if (cell.isEmpty() || cell.get().definition().kind() != CellKind.STORAGE) {
				continue;
			}
			BlockPos top = slotOrigin(s.key).offset(3, cell.get().size().getY() + 1, 3);
			double d = top.distSqr(from);
			if (d < bestDist) {
				bestDist = d;
				best = top;
			}
		}
		return best != null ? best : seedPos.above(5);
	}

	public int pendingVerifications() {
		return pendingVerify.size() + (verifyingSlot == null ? 0 : 1);
	}

	private int chunkSpan() {
		Set<Long> chunks = new HashSet<>();
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILT) {
				BlockPos o = slotOrigin(s.key);
				chunks.add(((long) (o.getX() >> 4) << 32) ^ ((o.getZ() >> 4) & 0xFFFFFFFFL));
			}
		}
		return chunks.size();
	}

	// ---- per-tick upkeep --------------------------------------------------------------------

	/** Called once a second by the manager. Re-queues orphaned slots and keeps builders spawned. */
	public void upkeep(ServerLevel level) {
		if (!level.getBlockState(seedPos).is(SeedCityBlocks.SEED)) {
			if (!frozen) {
				SeedCity.LOGGER.info("City {}: seed destroyed, freezing", seedPos.toShortString());
			}
			frozen = true;
		}
		if (frozen) {
			return;
		}
		SeedCityConfig cfg = SeedCityConfig.get();
		List<BuilderEntity> builders = builders(level, cfg);
		Set<UUID> alive = new HashSet<>();
		for (BuilderEntity b : builders) {
			alive.add(b.getUUID());
		}
		for (Slot s : slots.values()) {
			if (s.status == SlotStatus.BUILDING && (s.builder == null || !alive.contains(s.builder))) {
				s.status = SlotStatus.PLANNED;
				s.builder = null;
			}
		}
		processVerification(level);
		int desired = Math.min(cfg.maxBuilders, 1 + builtCount / cfg.cellsPerBuilder);
		if (builders.size() < desired) {
			spawnBuilder(level);
		}
	}

	public List<BuilderEntity> builders(ServerLevel level, SeedCityConfig cfg) {
		AABB box = new AABB(seedPos).inflate(cfg.maxRadiusSlots * SLOT + 24.0);
		return level.getEntities(SeedCityEntities.BUILDER, box, b -> seedPos.equals(b.cityPos()));
	}

	public void spawnBuilder(ServerLevel level) {
		BuilderEntity b = SeedCityEntities.BUILDER.spawn(level, seedPos.above(2), EntitySpawnReason.MOB_SUMMONED);
		if (b != null) {
			b.setCity(seedPos);
		}
	}

	public String summary() {
		int planned = 0, building = 0, verify = 0, built = 0, blocked = 0, pending = 0;
		for (Slot s : slots.values()) {
			switch (s.status) {
				case PLANNED -> planned++;
				case BUILDING -> building++;
				case VERIFY -> verify++;
				case BUILT -> built++;
				case BLOCKED -> blocked++;
				case PENDING -> pending++;
			}
		}
		return "city@" + seedPos.toShortString() + (frozen ? " FROZEN" : "") + " seed=" + Long.toHexString(citySeed)
				+ " built=" + built + " verifying=" + verify + " building=" + building + " planned=" + planned
				+ " pending=" + pending + " blocked=" + blocked
				+ " stock=" + redstone + "r/" + stone + "s/" + wood + "w";
	}

	public List<String> describeSlots() {
		List<Slot> list = new ArrayList<>(slots.values());
		list.sort(Comparator.comparingInt((Slot s) -> s.key.chebyshev()).thenComparingInt(s -> s.key.x()).thenComparingInt(s -> s.key.z()));
		List<String> out = new ArrayList<>();
		for (Slot s : list) {
			out.add(s + (s.note.isEmpty() ? "" : " [" + s.note + "]"));
		}
		return out;
	}

	public Vec3i size() {
		return new Vec3i(SLOT, 0, SLOT);
	}
}
