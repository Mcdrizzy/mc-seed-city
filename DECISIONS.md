# Decisions

Every judgement call made while building Seed City, one line each, dated. Newest at the bottom.
The design doc (`Seed_City_Design_Doc.pdf`) is the spec; where it is silent, the simplest option
that keeps every phase independently shippable wins, and it gets a line here.

- 2026-09-13: Target Minecraft **26.2**, the latest stable release at project start. Pinned in `gradle.properties`; never floated.
- 2026-09-13: **Java 25**, not the doc's Java 21. Mojang's version metadata for 26.2 requires Java 25 (`javaVersion.majorVersion = 25`), so 21 cannot run it. Language use stays conservative anyway.
- 2026-09-13: Loader **Fabric**, confirmed against the 2026 loader landscape. NeoForge only pays off for compatibility with large content-mod ecosystems, which this project does not need; Forge-loader support is a listed non-goal. Fabric's fast version tracking matters most while pinned to the newest release.
- 2026-09-13: Fabric Loader 0.19.5, Fabric API 0.160.0+26.2, Loom **1.17.20** (a release, not the template's `1.17-SNAPSHOT`, so builds are reproducible). Gradle 9.5.1 via the wrapper.
- 2026-09-13: **Mojang official mappings** (Loom's default; no Yarn). Verified against the remapped 26.2 jar: the doc's names are the mojmap names (`net.minecraft.resources.Identifier`, `net.minecraft.core.BlockPos`, `net.minecraft.world.level.block.Rotation`); cell NBT loads via `StructureTemplate`.
- 2026-09-13: Split environment source sets (`src/main` common+server, `src/client` client-only) as in the Fabric template. Keeps renderers from ever loading on a dedicated server.
- 2026-09-13: Unit tests are plain JUnit 5 for pure-logic packages (`card`, `grammar`, verifier models). In-world acceptance is Gametest; wiring the Gametest source set is the first Phase 0 task.
- 2026-09-13: No mixins yet. Prefer Fabric API events; any mixin added gets a line here with the reason.
- 2026-09-13: No license chosen. Tabor's call before the first Modrinth release; `fabric.mod.json` omits the field until then.
- 2026-09-13: Design doc PDF stays at the repo root as the spec of record. `CLAUDE.md` restates the rules an implementing agent must not break.
- 2026-09-13: Dev toolchain on this machine is a portable Temurin JDK 25 in `%USERPROFILE%\.jdks` with `JAVA_HOME` set as a user environment variable. No system-wide installer, no PATH change.
- 2026-09-13: **Cells are authored in code**, not hand-built in-game. Tabor knows little redstone, so the circuits live in `src/cellgen` (a plain-JVM DSL) and `gradlew genCells` emits the NBT + sidecar the spec requires. The runtime contract (structure NBT + JSON) is unchanged.
- 2026-09-13: **Verification runs in the real world**, not a headless simulator. The verifier parks probe blocks on in-ports, reads out-ports, and steps across server ticks; simulator-fidelity risk (doc 11) disappears because the simulator is Minecraft. Cost: verification is asynchronous and a cell is briefly live while tested. Revisit only if in-world verification proves too slow for Phase 1 growth rates.
- 2026-09-13: **Every cell is 7x7** in footprint with ports at y=1 on face centres. Makes the frontier a grid and port mating a direction+width check. Height is free.
- 2026-09-13: Added a `clock` truth model (free-running output must toggle) beyond the doc's list; the Clock Tower cannot be expressed with the others. Added optional sidecar field `settle`.
- 2026-09-13: `seedcity:probe` block exists only for the verifier: a constant 0-15 signal source. Never placed by the city, no item, no model.
- 2026-09-13: Added a `command` package for `/seedcity ...`; it is glue over the five interfaces, not a sixth.
- 2026-09-13: Acceptance tests are Fabric gametests in `src/gametest`, run by `gradlew runGameTest` and as part of `build`. One test per cell so a failure names the cell; a broken-cell test proves failures name a port.
- 2026-09-13: **Register contract**: transparent while clk high, holds while clk low, and `in` must not change in the same redstone tick as a falling clk edge (1 rt hold time, an inherent property of a gated comparator ring within a 7x7 footprint). The truth model tests write, hold, and input change as separate steps; Phase 3 card execution must respect the same rule.
- 2026-09-13: Docs that used to sit inside the data pack (`cells/README.md`) moved to `docs/`; resource paths may not contain capitals and Minecraft warned about them.
