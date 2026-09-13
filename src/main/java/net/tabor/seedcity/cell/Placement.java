package net.tabor.seedcity.cell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** A cell at a world position with a rotation. What the grammar emits and what builders build. */
public record Placement(Cell cell, BlockPos origin, Rotation rotation) {
	/** A port resolved to world space. {@code outside} is the block just beyond the face. */
	public record WorldPort(Port port, BlockPos pos, Direction face) {
		public BlockPos outside() {
			return pos.relative(face);
		}
	}

	public BoundingBox footprint() {
		return cell.footprint(origin, rotation);
	}

	public List<WorldPort> ports() {
		List<WorldPort> out = new ArrayList<>();
		for (Port p : cell.ports(rotation)) {
			out.add(new WorldPort(p, origin.offset(p.pos()), p.face()));
		}
		return out;
	}

	public boolean place(ServerLevel level) {
		return cell.place(level, origin, rotation);
	}

	/** Replaces the footprint with air, top-down, without dropping items. */
	public void clear(ServerLevel level) {
		BoundingBox box = footprint();
		int flags = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
		for (int y = box.maxY(); y >= box.minY(); y--) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), flags);
				}
			}
		}
	}

	@Override
	public String toString() {
		return cell.id() + "@" + origin.toShortString() + "/" + rotation.getSerializedName();
	}
}
