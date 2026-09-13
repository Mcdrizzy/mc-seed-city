package net.tabor.seedcity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.tabor.seedcity.verify.ProbeBlock;

/** Block registration. Kept in one place so the registry order is obvious. */
public final class SeedCityBlocks {
	public static Block PROBE;

	private SeedCityBlocks() {
	}

	public static void init() {
		ResourceKey<Block> probeKey = ResourceKey.create(Registries.BLOCK, SeedCity.id("probe"));
		PROBE = Registry.register(BuiltInRegistries.BLOCK, probeKey,
				new ProbeBlock(BlockBehaviour.Properties.of().setId(probeKey).strength(-1.0F, 3600000.0F).noLootTable()));
	}
}
