package net.tabor.seedcity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.tabor.seedcity.SeedCityBlocks;
import net.tabor.seedcity.cell.Cell;
import net.tabor.seedcity.cell.CellLibrary;
import net.tabor.seedcity.cell.Placement;
import net.tabor.seedcity.cell.Port;
import net.tabor.seedcity.config.SeedCityConfig;
import net.tabor.seedcity.core.CityManager;
import net.tabor.seedcity.core.CityState;
import net.tabor.seedcity.entity.BuilderEntity;
import net.tabor.seedcity.verify.Verifier;
import net.tabor.seedcity.verify.VerifyResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dev and admin commands:
 * <pre>
 *   /seedcity list
 *   /seedcity place &lt;cell&gt; [rotation 0-3]
 *   /seedcity verify &lt;cell&gt; [rotation 0-3] [keep]
 *   /seedcity verifyall
 *   /seedcity platform &lt;size&gt;        a stone boat under your feet, for testing
 *   /seedcity plant                     a powered Seed where you stand
 *   /seedcity city                      status of the nearest city and its builders
 *   /seedcity slots                     every slot of the nearest city
 * </pre>
 */
public final class SeedCityCommands {
	private static final DynamicCommandExceptionType UNKNOWN_CELL =
			new DynamicCommandExceptionType(id -> Component.literal("Unknown cell " + id));

	private SeedCityCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register(SeedCityCommands::register);
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("seedcity")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(c -> list(c.getSource())))
				.then(Commands.literal("place")
						.then(Commands.argument("cell", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(CellLibrary.ids(), b))
								.executes(c -> place(c, Rotation.NONE))
								.then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
										.executes(c -> place(c, rotation(c))))))
				.then(Commands.literal("verify")
						.then(Commands.argument("cell", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(CellLibrary.ids(), b))
								.executes(c -> verify(c, Rotation.NONE, false))
								.then(Commands.literal("keep").executes(c -> verify(c, Rotation.NONE, true)))
								.then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
										.executes(c -> verify(c, rotation(c), false))
										.then(Commands.literal("keep").executes(c -> verify(c, rotation(c), true))))))
				.then(Commands.literal("verifyall").executes(c -> verifyAll(c.getSource())))
				.then(Commands.literal("platform")
						.then(Commands.argument("size", IntegerArgumentType.integer(7, 200))
								.executes(c -> platform(c.getSource(), IntegerArgumentType.getInteger(c, "size")))))
				.then(Commands.literal("plant").executes(c -> plant(c.getSource())))
				.then(Commands.literal("city").executes(c -> city(c.getSource())))
				.then(Commands.literal("slots").executes(c -> slots(c.getSource()))));
	}

	private static Rotation rotation(CommandContext<CommandSourceStack> c) {
		return Rotation.values()[IntegerArgumentType.getInteger(c, "rotation")];
	}

	private static Cell cell(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Identifier id = IdentifierArgument.getId(c, "cell");
		return CellLibrary.get(id).orElseThrow(() -> UNKNOWN_CELL.create(id));
	}

	/** Cells go two blocks east of the caller so they never land on top of them. */
	private static BlockPos originFor(CommandSourceStack source) {
		return BlockPos.containing(source.getPosition()).offset(2, 0, 0);
	}

	private static int list(CommandSourceStack source) {
		List<Cell> cells = new ArrayList<>(CellLibrary.all());
		if (cells.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No cells loaded."), false);
		}
		for (Cell cell : cells) {
			StringBuilder sb = new StringBuilder(cell.id().toString())
					.append("  ").append(cell.definition().kind().name().toLowerCase())
					.append("  ").append(cell.size().toShortString())
					.append("  truth=").append(cell.definition().truth())
					.append("  ports:");
			for (Port p : cell.definition().ports()) {
				sb.append(' ').append(p);
			}
			source.sendSuccess(() -> Component.literal(sb.toString()), false);
		}
		for (String err : CellLibrary.errors()) {
			source.sendFailure(Component.literal("rejected: " + err));
		}
		return cells.size();
	}

	private static int place(CommandContext<CommandSourceStack> c, Rotation rotation) throws CommandSyntaxException {
		Cell cell = cell(c);
		CommandSourceStack source = c.getSource();
		Placement p = new Placement(cell, originFor(source), rotation);
		if (!p.place(source.getLevel())) {
			source.sendFailure(Component.literal("Could not place " + cell.id()));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Placed " + p + "; ports: " + portSummary(p)), true);
		return 1;
	}

	private static String portSummary(Placement p) {
		StringBuilder sb = new StringBuilder();
		for (Placement.WorldPort wp : p.ports()) {
			sb.append(wp.port().name()).append('@').append(wp.pos().toShortString()).append(' ');
		}
		return sb.toString().trim();
	}

	private static int verify(CommandContext<CommandSourceStack> c, Rotation rotation, boolean keep) throws CommandSyntaxException {
		Cell cell = cell(c);
		CommandSourceStack source = c.getSource();
		ServerLevel level = source.getLevel();
		Placement p = new Placement(cell, originFor(source), rotation);
		if (!p.place(level)) {
			source.sendFailure(Component.literal("Could not place " + cell.id()));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Verifying " + p + " ..."), false);
		Verifier.verify(level, p, List.of(), result -> {
			report(source, result);
			if (!keep) {
				p.clear(level, false);
			}
		});
		return 1;
	}

	private static int verifyAll(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		List<Cell> cells = new ArrayList<>(CellLibrary.all());
		if (cells.isEmpty()) {
			source.sendFailure(Component.literal("No cells loaded."));
			return 0;
		}
		BlockPos base = originFor(source);
		List<VerifyResult> results = new ArrayList<>();
		int i = 0;
		for (Cell cell : cells) {
			Placement p = new Placement(cell, base.offset(i * 12, 0, 0), Rotation.NONE);
			i++;
			if (!p.place(level)) {
				results.add(VerifyResult.fail(cell.id(), "could not place"));
				continue;
			}
			Verifier.verify(level, p, List.of(), result -> {
				report(source, result);
				p.clear(level, false);
				results.add(result);
				if (results.size() == cells.size()) {
					long passed = results.stream().filter(VerifyResult::pass).count();
					source.sendSuccess(() -> Component.literal("verifyall: " + passed + "/" + results.size() + " passed"), true);
				}
			});
		}
		source.sendSuccess(() -> Component.literal("Verifying " + cells.size() + " cell(s) ..."), false);
		return cells.size();
	}

	private static void report(CommandSourceStack source, VerifyResult result) {
		if (result.pass()) {
			source.sendSuccess(() -> Component.literal(result.toString()), false);
		} else {
			source.sendFailure(Component.literal(result.toString()));
		}
	}

	/** Two layers of smooth stone centred under the caller: a city boat for testing. */
	private static int platform(CommandSourceStack source, int size) {
		ServerLevel level = source.getLevel();
		BlockPos feet = BlockPos.containing(source.getPosition());
		int half = size / 2;
		int placed = 0;
		for (int x = -half; x < size - half; x++) {
			for (int z = -half; z < size - half; z++) {
				for (int dy = 1; dy <= 2; dy++) {
					level.setBlock(feet.offset(x, -dy, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					placed++;
				}
			}
		}
		int n = placed;
		source.sendSuccess(() -> Component.literal("Platform " + size + "x" + size + " built (" + n + " blocks). Stand in the middle and /seedcity plant."), true);
		return 1;
	}

	/** Puts a Seed at the caller's feet with a redstone block beneath it, which roots a city. */
	private static int plant(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos feet = BlockPos.containing(source.getPosition());
		level.setBlock(feet.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(feet, SeedCityBlocks.SEED.defaultBlockState(), Block.UPDATE_ALL);
		Optional<CityState> city = CityManager.get(level).city(feet);
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("Seed placed but no city rooted; is it powered?"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Planted. " + city.get().summary()), true);
		return 1;
	}

	private static int city(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		CityState c = city.get();
		source.sendSuccess(() -> Component.literal(c.summary()), false);
		for (BuilderEntity b : c.builders(level, SeedCityConfig.get())) {
			source.sendSuccess(() -> Component.literal("  builder " + b.blockPosition().toShortString() + ": " + b.status()), false);
		}
		source.sendSuccess(() -> Component.literal("  verifier jobs active: " + Verifier.activeJobs()), false);
		return 1;
	}

	private static int slots(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Optional<CityState> city = CityManager.get(level).nearest(BlockPos.containing(source.getPosition()));
		if (city.isEmpty()) {
			source.sendFailure(Component.literal("No city in this dimension."));
			return 0;
		}
		for (String line : city.get().describeSlots()) {
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}
}
