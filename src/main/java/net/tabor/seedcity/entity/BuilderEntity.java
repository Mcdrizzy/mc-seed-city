package net.tabor.seedcity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.tabor.seedcity.build.BuildTask;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;

import java.util.Optional;

/**
 * The Builder (design doc 6, 24): lives on the frontier, takes the next BuildTask, fetches
 * material, flies to the site, places blocks at a fixed rate with sound, then hands the finished
 * cell to the verifier and moves on. It never decides what to build; the city does.
 */
public final class BuilderEntity extends PathfinderMob {
	private enum Phase { IDLE, TO_STORAGE, WITHDRAW, TO_SITE, BUILD }

	private BlockPos cityPos;
	private BuildTask task;
	private Phase phase = Phase.IDLE;
	private int timer;
	private int travelTicks;
	private Vec3 target;

	public BuilderEntity(EntityType<? extends BuilderEntity> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.FLYING_SPEED, 0.3)
				.add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
		nav.setCanOpenDoors(false);
		nav.setCanFloat(true);
		return nav;
	}

	@Override
	public void travel(Vec3 input) {
		travelFlying(input, getSpeed());
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	public BlockPos cityPos() {
		return cityPos;
	}

	public void setCity(BlockPos pos) {
		this.cityPos = pos;
	}

	public String status() {
		return phase + (task == null ? "" : " " + task.placement() + " " + (int) (task.progress() * 100) + "%");
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (cityPos != null) {
			output.store("City", BlockPos.CODEC, cityPos);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		cityPos = input.read("City", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		try {
			work(level);
		} catch (Exception e) {
			// never crash a city: drop the task and start over
			net.tabor.seedcity.SeedCity.LOGGER.error("Builder {} failed while {}; re-queuing", getUUID(), phase, e);
			dropTask(level);
		}
	}

	private Optional<CityState> city(ServerLevel level) {
		return cityPos == null ? Optional.empty() : CityManager.get(level).city(cityPos);
	}

	private void work(ServerLevel level) {
		SeedCityConfig cfg = SeedCityConfig.get();
		switch (phase) {
			case IDLE -> {
				if (++timer < 20) {
					hover(level, cityPos == null ? position() : Vec3.atCenterOf(cityPos.above(3)));
					return;
				}
				timer = 0;
				Optional<CityState> city = city(level);
				if (city.isEmpty() || city.get().frozen()) {
					return;
				}
				Optional<BuildTask> next = city.get().claimTask(level, getUUID());
				if (next.isEmpty()) {
					return;
				}
				task = next.get();
				CityManager.get(level).touch();
				if (cfg.unlimitedMaterials) {
					begin(Phase.TO_SITE, Vec3.atCenterOf(task.nextPos().above(2)));
				} else {
					begin(Phase.TO_STORAGE, Vec3.atCenterOf(city.get().storageTarget(blockPosition())));
				}
			}
			case TO_STORAGE -> {
				if (travel(cfg)) {
					phase = Phase.WITHDRAW;
					timer = 20;
				}
			}
			case WITHDRAW -> {
				if (--timer <= 0) {
					begin(Phase.TO_SITE, Vec3.atCenterOf(task.nextPos().above(2)));
				}
			}
			case TO_SITE -> {
				if (travel(cfg)) {
					phase = Phase.BUILD;
					timer = 0;
				}
			}
			case BUILD -> build(level, cfg);
		}
	}

	private void begin(Phase next, Vec3 to) {
		phase = next;
		target = to;
		travelTicks = 0;
		timer = 0;
	}

	/** Flies toward the target; true when close enough. Gives up after the configured time. */
	private boolean travel(SeedCityConfig cfg) {
		travelTicks++;
		if (position().distanceTo(target) < 2.5) {
			getNavigation().stop();
			return true;
		}
		if (travelTicks % 10 == 1) {
			getNavigation().moveTo(target.x, target.y, target.z, cfg.builderSpeed);
		}
		if (travelTicks > cfg.abandonSeconds * 20) {
			dropTask((ServerLevel) level());
		}
		return false;
	}

	private void hover(ServerLevel level, Vec3 around) {
		if (tickCount % 40 == 0 && position().distanceTo(around) > 3) {
			getNavigation().moveTo(around.x, around.y, around.z, 1.0);
		}
	}

	private void build(ServerLevel level, SeedCityConfig cfg) {
		BlockPos next = task.nextPos();
		Vec3 stand = Vec3.atCenterOf(next).add(0, 2, 0);
		if (position().distanceTo(stand) > 4.0) {
			if (tickCount % 10 == 0) {
				getNavigation().moveTo(stand.x, stand.y, stand.z, cfg.builderSpeed);
			}
		} else {
			getNavigation().stop();
		}
		getLookControl().setLookAt(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);
		if (++timer < cfg.ticksPerBlock()) {
			return;
		}
		timer = 0;
		if (task.step(level)) {
			finish(level);
		}
	}

	/** Hands the completed cell to the city, which queues it for verification, and goes back to the frontier. */
	private void finish(ServerLevel level) {
		BuildTask done = task;
		task = null;
		city(level).ifPresent(c -> c.onBuildComplete(done));
		CityManager.get(level).touch();
		phase = Phase.IDLE;
		timer = 0;
	}

	private void dropTask(ServerLevel level) {
		if (task != null) {
			BuildTask t = task;
			task = null;
			city(level).ifPresent(c -> c.abandon(t));
		}
		getNavigation().stop();
		phase = Phase.IDLE;
		timer = 0;
	}
}
