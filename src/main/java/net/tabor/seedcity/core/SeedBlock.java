package net.tabor.seedcity.core;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

/**
 * The Seed (design doc 4.1, 13): place it, power it, and a city roots here. Builders enclose it
 * into the Core. Breaking it freezes the city (growth and repair stop; what runs keeps running).
 */
public final class SeedBlock extends Block {
	public static final MapCodec<SeedBlock> CODEC = simpleCodec(SeedBlock::new);

	public SeedBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		tryActivate(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
		tryActivate(level, pos);
	}

	private static void tryActivate(Level level, BlockPos pos) {
		if (level instanceof ServerLevel server && server.hasNeighborSignal(pos)) {
			CityManager.get(server).activate(server, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		CityManager.get(level).freeze(pos);
	}
}
