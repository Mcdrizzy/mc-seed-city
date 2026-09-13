package net.tabor.seedcity.cell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/**
 * A signal connection point on a cell face (design doc 22.1).
 *
 * @param pos  cell-local position of the port block; it lies on {@code face}
 * @param face outward normal of the face the port sits on
 * @param bits 1 (on/off) or 4 (signal strength 0-15)
 */
public record Port(String name, PortDir dir, Direction face, BlockPos pos, int bits) {
	/** The same port after the cell is rotated about its origin. Matches structure placement. */
	public Port rotated(Rotation rotation) {
		if (rotation == Rotation.NONE) {
			return this;
		}
		return new Port(name, dir, rotation.rotate(face), pos.rotate(rotation), bits);
	}

	/**
	 * Two ports mate when one drives and the other listens. Width is a semantic hint, not a
	 * physical barrier: a 4-bit comparator port reads a 1-bit repeater as 15/0, and a 1-bit
	 * repeater port reads any non-zero strength as on. See DECISIONS.md.
	 */
	public boolean compatibleWith(Port other) {
		return dir != other.dir;
	}

	public boolean sameWidth(Port other) {
		return bits == other.bits;
	}

	@Override
	public String toString() {
		return name + "(" + dir.name().toLowerCase() + "," + bits + "b," + face.getSerializedName() + "@" + pos.toShortString() + ")";
	}
}
