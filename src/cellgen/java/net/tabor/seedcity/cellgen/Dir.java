package net.tabor.seedcity.cellgen;

/** Minecraft axis conventions: +x east, +y up, +z south. */
public enum Dir {
	NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);

	public final int dx, dy, dz;

	Dir(int dx, int dy, int dz) {
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
	}

	public Dir opposite() {
		return switch (this) {
			case NORTH -> SOUTH;
			case SOUTH -> NORTH;
			case WEST -> EAST;
			case EAST -> WEST;
			case UP -> DOWN;
			case DOWN -> UP;
		};
	}

	public boolean horizontal() {
		return dy == 0;
	}

	public String key() {
		return name().toLowerCase();
	}
}
