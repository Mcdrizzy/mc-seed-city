package net.tabor.seedcity.grammar;

import net.minecraft.world.level.block.Rotation;
import net.tabor.seedcity.cell.Cell;

/** What the grammar picks for a slot. */
public record Choice(Cell cell, Rotation rotation) {
	@Override
	public String toString() {
		return cell.id() + "/" + rotation.getSerializedName();
	}
}
