package net.tabor.seedcity.cellgen;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal NBT writer. Only the tag types a structure file needs. No Minecraft dependency so
 * cell generation runs as a plain JVM task.
 */
public final class Nbt {
	private Nbt() {
	}

	public sealed interface Tag permits IntTag, StringTag, ListTag, CompoundTag {
		byte typeId();

		void write(DataOutputStream out) throws IOException;
	}

	public record IntTag(int value) implements Tag {
		public byte typeId() {
			return 3;
		}

		public void write(DataOutputStream out) throws IOException {
			out.writeInt(value);
		}
	}

	public record StringTag(String value) implements Tag {
		public byte typeId() {
			return 8;
		}

		public void write(DataOutputStream out) throws IOException {
			out.writeUTF(value);
		}
	}

	public static final class ListTag implements Tag {
		private final List<Tag> items = new ArrayList<>();

		public ListTag add(Tag t) {
			if (!items.isEmpty() && items.getFirst().typeId() != t.typeId()) {
				throw new IllegalArgumentException("mixed list");
			}
			items.add(t);
			return this;
		}

		public int size() {
			return items.size();
		}

		public byte typeId() {
			return 9;
		}

		public void write(DataOutputStream out) throws IOException {
			out.writeByte(items.isEmpty() ? 0 : items.getFirst().typeId());
			out.writeInt(items.size());
			for (Tag t : items) {
				t.write(out);
			}
		}
	}

	public static final class CompoundTag implements Tag {
		private final Map<String, Tag> entries = new LinkedHashMap<>();

		public CompoundTag put(String key, Tag t) {
			entries.put(key, t);
			return this;
		}

		public CompoundTag putInt(String key, int v) {
			return put(key, new IntTag(v));
		}

		public CompoundTag putString(String key, String v) {
			return put(key, new StringTag(v));
		}

		public byte typeId() {
			return 10;
		}

		public void write(DataOutputStream out) throws IOException {
			for (Map.Entry<String, Tag> e : entries.entrySet()) {
				out.writeByte(e.getValue().typeId());
				out.writeUTF(e.getKey());
				e.getValue().write(out);
			}
			out.writeByte(0);
		}
	}

	public static ListTag ints(int... values) {
		ListTag l = new ListTag();
		for (int v : values) {
			l.add(new IntTag(v));
		}
		return l;
	}

	/** Writes a gzip-compressed root compound with an empty name, as Minecraft expects. */
	public static void writeCompressed(CompoundTag root, OutputStream raw) throws IOException {
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
			out.writeByte(root.typeId());
			out.writeUTF("");
			root.write(out);
		}
	}
}
