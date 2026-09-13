package net.tabor.seedcity.cell;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.tabor.seedcity.SeedCity;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Loads every {@code data/<ns>/cells/<name>.json} + {@code <name>.nbt} pair on data pack (re)load.
 * A malformed cell is skipped and reported, never thrown: a city must never crash.
 */
public final class CellLibrary extends SimpleReloadListener<CellLibrary.Loaded> {
	public static final Identifier ID = SeedCity.id("cells");
	private static final String DIRECTORY = "cells";

	record Loaded(Map<Identifier, Cell> cells, List<String> errors) {
	}

	private static volatile Map<Identifier, Cell> cells = Map.of();
	private static volatile List<String> errors = List.of();

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new CellLibrary());
	}

	public static Optional<Cell> get(Identifier id) {
		return Optional.ofNullable(cells.get(id));
	}

	public static Collection<Cell> all() {
		return cells.values();
	}

	public static Collection<Identifier> ids() {
		return cells.keySet();
	}

	public static List<String> errors() {
		return errors;
	}

	@Override
	protected Loaded prepare(PreparableReloadListener.SharedState state) {
		ResourceManager rm = state.resourceManager();
		Map<Identifier, Cell> found = new TreeMap<>();
		List<String> problems = new ArrayList<>();
		Map<Identifier, Resource> sidecars = rm.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> e : sidecars.entrySet()) {
			Identifier file = e.getKey();
			try {
				Cell cell = load(rm, file, e.getValue());
				if (found.containsKey(cell.id())) {
					throw new CellFormatException("duplicate cell id " + cell.id());
				}
				found.put(cell.id(), cell);
			} catch (CellFormatException ex) {
				problems.add(file + ": " + ex.getMessage());
			} catch (Exception ex) {
				problems.add(file + ": " + ex);
			}
		}
		return new Loaded(found, problems);
	}

	@Override
	protected void apply(Loaded prepared, PreparableReloadListener.SharedState state) {
		cells = Map.copyOf(prepared.cells());
		errors = List.copyOf(prepared.errors());
		SeedCity.LOGGER.info("Cell library: {} cells loaded, {} rejected", cells.size(), errors.size());
		for (String err : errors) {
			SeedCity.LOGGER.error("Cell rejected: {}", err);
		}
	}

	private static Cell load(ResourceManager rm, Identifier jsonId, Resource json) throws Exception {
		CellDefinition def;
		try (Reader reader = json.openAsReader()) {
			JsonObject obj = GsonHelper.parse(reader);
			def = CellDefinition.parse(obj);
		}
		String base = jsonId.getPath().substring(DIRECTORY.length() + 1, jsonId.getPath().length() - ".json".length());
		String expectedPath = base;
		if (!def.id().getNamespace().equals(jsonId.getNamespace()) || !def.id().getPath().equals(expectedPath)) {
			throw new CellFormatException("sidecar id " + def.id() + " does not match file name " + jsonId.getNamespace() + ":" + expectedPath);
		}
		Identifier nbtId = jsonId.withPath(DIRECTORY + "/" + base + ".nbt");
		Resource nbt = rm.getResource(nbtId).orElseThrow(() -> new CellFormatException("missing structure " + nbtId));
		StructureTemplate template = new StructureTemplate();
		try (InputStream in = nbt.open()) {
			CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			template.load(BuiltInRegistries.BLOCK, tag);
		} catch (Exception ex) {
			throw new CellFormatException("structure " + nbtId + " unreadable: " + ex.getMessage(), ex);
		}
		if (!template.getSize().equals(def.size())) {
			throw new CellFormatException("structure size " + template.getSize() + " differs from sidecar size " + def.size());
		}
		return new Cell(def, template);
	}
}
