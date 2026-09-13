package net.tabor.seedcity.cellgen;

import java.io.IOException;
import java.nio.file.Path;

/** Entry point of the genCells Gradle task: writes every cell in {@link Cells#all()} to a directory. */
public final class Main {
	private Main() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("usage: Main <output-dir>");
			System.exit(2);
		}
		Path out = Path.of(args[0]);
		for (CellBuilder cell : Cells.all()) {
			cell.writeTo(out);
			System.out.println("wrote " + out.resolve(cell.id() + ".nbt") + " and sidecar");
		}
	}
}
