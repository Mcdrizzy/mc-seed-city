package net.tabor.seedcity.verify;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A constant signal source the verifier parks outside a cell's input ports. Emits its POWER
 * value (0-15) toward FACING only, like a repeater's output, so it can sit in a neighbour's
 * port position without back-feeding the neighbour. Dev-only; never placed by the city.
 */
public final class ProbeBlock extends Block {
	public static final MapCodec<ProbeBlock> CODEC = simpleCodec(ProbeBlock::new);
	public static final IntegerProperty POWER = BlockStateProperties.POWER;
	/** Direction the signal is emitted toward: from the probe to the port it drives. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public ProbeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(POWER, 0).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWER, FACING);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	/** {@code direction} points from the querying block toward this probe. */
	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return direction == state.getValue(FACING).getOpposite() ? state.getValue(POWER) : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return getSignal(state, level, pos, direction);
	}
}
