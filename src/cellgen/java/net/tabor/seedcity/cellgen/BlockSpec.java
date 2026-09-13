package net.tabor.seedcity.cellgen;

import java.util.Map;
import java.util.TreeMap;

/** A block id plus its state properties. Palette entries dedupe on equality. */
public record BlockSpec(String name, Map<String, String> props) {
	public BlockSpec {
		props = Map.copyOf(new TreeMap<>(props));
	}

	public static BlockSpec of(String name, String... kv) {
		if (kv.length % 2 != 0) {
			throw new IllegalArgumentException("odd property list for " + name);
		}
		Map<String, String> m = new TreeMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put(kv[i], kv[i + 1]);
		}
		return new BlockSpec(name, m);
	}

	public BlockSpec with(String k, String v) {
		Map<String, String> m = new TreeMap<>(props);
		m.put(k, v);
		return new BlockSpec(name, m);
	}

	public String prop(String k) {
		return props.get(k);
	}

	public boolean is(String n) {
		return name.equals(n);
	}

	public Nbt.CompoundTag toNbt() {
		Nbt.CompoundTag t = new Nbt.CompoundTag().putString("Name", name);
		if (!props.isEmpty()) {
			Nbt.CompoundTag p = new Nbt.CompoundTag();
			props.forEach(p::putString);
			t.put("Properties", p);
		}
		return t;
	}
}
