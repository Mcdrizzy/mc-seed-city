# Cell library

Each cell is two files with the same base name in `src/main/resources/data/seedcity/cells/`:

- `<name>.nbt` - the structure. Generated, do not hand-edit: run `gradlew genCells`.
- `<name>.json` - the sidecar. The only thing the grammar and the verifier read. Also generated.

Both come from the circuit authored in `src/cellgen/java/net/tabor/seedcity/cellgen/Cells.java`.
The design doc assumed cells would be hand-built in-game; they are authored in code instead
(see DECISIONS.md) because that makes them diffable, regenerable, and buildable without a client.
The in-world verifier is the check that a design actually works.

## Sidecar shape (design doc 22.1)

```json
{
  "id": "seedcity:register_block",
  "kind": "logic",
  "size": [7, 5, 7],
  "ports": [
    { "name": "in",  "dir": "in",  "face": "north", "pos": [3, 1, 0], "bits": 4 },
    { "name": "clk", "dir": "in",  "face": "west",  "pos": [0, 1, 3], "bits": 1 },
    { "name": "out", "dir": "out", "face": "south", "pos": [3, 1, 6], "bits": 4 }
  ],
  "truth": "register",
  "weights": { "core": 3, "residential": 4, "forge": 6, "plaza": 1 },
  "cost": { "redstone": 24, "stone": 80, "wood": 0 },
  "setpiece": true
}
```

Rules:

- `kind` is one of `logic | actuator | sensor | storage | decor | core`.
- `pos` is relative to the cell origin (min corner) and must lie on the named `face`.
- Two cells are compatible when an `out` port of one lands exactly on an `in` port of the
  other with equal `bits` when placed adjacent.
- `bits: 4` is carried as signal strength 0-15; `bits: 1` is on/off.
- `truth` names a verifier model: `passthrough, not, and, or, register, counter, decoder,
  actuator, sensor, clock, none`.
- `settle` (optional, default 40) is how many game ticks the verifier waits after driving inputs.
- `setpiece` is `false` only for forge and decor cells.

## Footprint and port conventions

Every shipped cell is 7 wide (x) by 7 deep (z) with a solid floor at y=0. Ports sit at y=1 in the
centre of a face: north `[3,1,0]`, south `[3,1,6]`, west `[0,1,3]`, east `[6,1,3]`. One footprint
keeps the Phase 1 frontier a plain 7x7 grid, and centred ports mean any two cells that agree on
direction and width will mate after rotation.

Port blocks:

| Width | In-port block | Out-port block |
| --- | --- | --- |
| 1-bit | repeater outputting inward | repeater outputting outward |
| 4-bit | comparator outputting inward | comparator outputting outward |

Repeaters output 15 regardless of input, so they are only for 1-bit ports. Comparators pass
strength through unchanged and chain losslessly (comparator reading comparator, or comparator
reading a block another comparator powers). Dust loses one strength per block and must never sit
on a 4-bit path.

## How the verifier drives a cell

The verifier (`net.tabor.seedcity.verify`) uses the real redstone engine, not a simulator:

1. It places a `seedcity:probe` block just outside every in-port. The probe emits a chosen
   strength 0-15 on all faces.
2. It clears the block outside every out-port and reads the strength the port block emits.
3. For each stimulus the truth model asks for, it sets the probes, waits `settle` ticks recording
   the outputs every tick, then samples outputs and a snapshot of every block in the footprint.
4. The model judges the whole sequence and names a port on failure.

Ports whose outside block belongs to a neighbouring placement are left alone; the neighbour drives
them. Results are cached by (cell, neighbours, rotation).

## The Phase 0 cells

| Cell | Truth | Circuit |
| --- | --- | --- |
| bus_segment | passthrough | seven comparators in a row under glass |
| inverter | not | repeater into a block, torch on the far side, dust out; lamp shows the inverted state |
| register_block | register | four-comparator ring holds a strength; clk gates the ring, NOT clk gates the input |
| clock_tower | clock | torch-repeater loop, 68 game tick period; lamp beats under the spire |
| drawbridge | actuator | dust climbs onto blocks that power three sticky pistons under a plank deck |
| storage_cell | none | brick warehouse with barrels |

### Register timing

The register is a gated latch: transparent while clk is high, holding while clk is low. Counted in
redstone ticks after a clk edge, the ring gate reopens at 3 and the input gate closes at 4 (make
before break, so the ring is never left undriven). An input change reaches the ring at 3, so `in`
must not change in the same redstone tick as a falling clk edge. The truth model and card execution
both respect that: write, drop clk with `in` stable, then change `in` freely while holding.

## Adding a cell

1. Add a method to `Cells.java` using the builder (`comparator`, `repeater`, `dust`, `wallTorch`,
   `lamp`, `stickyPiston`, `fill`, `walls`) and declare its ports, truth model, weights and cost.
   Repeaters and comparators are declared by the direction they output to.
2. Add it to `Cells.all()` and run `gradlew genCells`.
3. Add a test method to `CellVerifyTests` and run `gradlew runGameTest`. Iterate on the circuit
   until the verifier passes; the failure message names the port and the step.
4. Place it in a dev client with `/seedcity place <name>` and stand next to it. If it is not
   interesting to stand next to, keep working on it (forge and decor cells are exempt).
