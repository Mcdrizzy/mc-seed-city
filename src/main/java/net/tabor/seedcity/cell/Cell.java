package net.tabor.seedcity.cell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/**
 * Interface: Cell (design doc 19, 23). A verified redstone module: structure NBT plus sidecar.
 * The only unit anything in the mod knows how to place.
 */
public final class Cell {
	private final CellDefinition definition;
	private final StructureTemplate template;

	public Cell(CellDefinition definition, StructureTemplate template) {
		this.definition = definition;
		this.template = template;
	}

	public Identifier id() {
		return definition.id();
	}

	public CellDefinition definition() {
		return definition;
	}

	public StructureTemplate template() {
		return template;
	}

	public Vec3i size() {
		return definition.size();
	}

	/** World-space box the cell occupies when its local origin is placed at {@code origin}. */
	public BoundingBox footprint(BlockPos origin, Rotation rotation) {
		Vec3i s = definition.size();
		BlockPos far = new BlockPos(s.getX() - 1, s.getY() - 1, s.getZ() - 1).rotate(rotation).offset(origin);
		return BoundingBox.fromCorners(origin, far);
	}

	/** Ports in cell-local coordinates after rotation about the origin. */
	public List<Port> ports(Rotation rotation) {
		return definition.ports().stream().map(p -> p.rotated(rotation)).toList();
	}

	/** Writes the structure into the world. Neighbours get updates so redstone settles on its own. */
	public boolean place(ServerLevel level, BlockPos origin, Rotation rotation) {
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(rotation)
				.setIgnoreEntities(true)
				.setKnownShape(false);
		return template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);
	}

	@Override
	public String toString() {
		return id().toString();
	}
}
