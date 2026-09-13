# The city as a computer

Design addendum, September 2026. Status: **proposed, under discussion**. Nothing here is built
yet and nothing here changes Phase 2. It reframes Phases 3 to 5 and everything after.

## The idea

The Seed grows a city: roads, storage, lights, bridges, districts. As the city matures, the Core
becomes its processor, and the computer physically grows instead of appearing all at once.

- The Core is the CPU, where programs execute.
- A RAM district grows next to it to hold registers and working state.
- A Storage district holds long-term memory: punch cards, saved programs.
- A Compute district (the doc's Forge) grows for arithmetic and, later, parallel work.
- Bus streets are the data buses connecting the districts.
- The Clock Tower stays the heartbeat.

Hardware is never pre-generated. The city builds only what its programs need. A tiny program
produces a tiny computer; a larger one demands more registers, more bus, more arithmetic, and
eventually new districts. Writing software changes the skyline. From above, the mature city reads
like a CPU die: dense compute in the middle, a beautiful, explorable town around it.

Progression falls out of it: discover and repair a city, then own a Seed and grow one, then grow a
computer that never reaches a finished state, because every new capability is new hardware. Two
cities running different programs grow into different shapes. Expansion packs are new districts.

## How it sits against the design doc

Most of this is already in the doc in embryo, which is why it feels right:

- Part II: "what the city builds is whatever the program requires", the `NEED` op, the Forge, and
  "seen from above, a CPU already looks like a city".
- Section 15: registers are physical Register Block cells; "the program is physically present in
  the city".
- Section 19: the five interfaces. Nothing in this addendum needs a sixth. It is Card resolving
  into more kinds of Goal, and Grammar honouring district zoning. Builders and Verify are unchanged.

What it changes:

1. **Districts become functional, not radial.** Today a district is a distance ring
   (core, plaza, residential). The addendum makes a district a typed zone with a purpose,
   a capacity, and its own cell subset and routing style.
2. **Goals become demand.** Today the only goal is "have one clocked actuator". Resolving a card
   yields a hardware requirement, and the planner grows zones to meet it.
3. **Buses become real data paths** with addressing, not just a wire the clock rides on.

Nothing else in the built code moves. The planner, the grammar, the ledger, the builders, the
verifier and the cell library all carry over.

## The one rule that keeps it honest

Pillar one says everything works. A city that *pretends* to compute in Java while the redstone is
scenery would break that pillar quietly. A city whose *entire* CPU is redstone would take years
and run at a speed nobody can watch. The line between them:

> **No value without a register, no operation without hardware.** The Core may sequence and
> control in software, at clock speed. It may not hold state or compute results that the city has
> not built the hardware for.

Concretely:

- Every register a program uses is a Register Block cell in the RAM district. The Core reads and
  writes them through their ports. Kill the cell and the value is gone.
- Every arithmetic or logic op a program uses requires a built, verified cell of that kind in the
  Compute district. The Core routes operands to it over the bus, waits the hardware's latency,
  and reads the result back over the bus. No adder cell, no `ADD`: the card is rejected with
  "needs adder" and the builders go build one.
- Every `OUT`/`IN` is a port on a real actuator or sensor cell, reached over bus or by Courier.
- Control (fetch, decode, branch, `WAIT`) lives in the Core as software, clocked by the tower.
  That is the mainframe fiction the doc already leans on, and it is the piece that could be
  pushed into hardware later as a stretch, one instruction at a time.

This makes the machine's shape a true constraint on the software, which is the whole point of
the idea, without demanding a hand-built redstone CPU before anything runs.

## Words: analog or binary?

This is the biggest technical fork and it should be decided before Phase 3.

The doc uses 4-bit words carried as signal strength on one wire. That is what the cell library
does today: buses, registers and the daylight sensor all speak strength 0-15.

| | Analog (one wire, strength 0-15) | Binary (four wires, on/off) |
| --- | --- | --- |
| Footprint | one lane per word, fits the 7x7 grid trivially | four lanes per word, still fits (lanes 1,3,5 of 7) but corners and crossings get busy |
| Legibility | a lamp per register shows "something is held"; strength is visible with comparator ladders | lamps show the actual bits, which is what a computer looks like |
| Native ops | subtract (saturating at 0), compare, max (wires merge), copy, gate | everything: adders, logic, shifts, all well known in redstone |
| Hard ops | **addition** has no native comparator form; needs conversion or lookup tricks | none hard, just bigger |
| Verification | already working | already working (1-bit ports) |

Recommendation: **analog on the civic bus, binary inside Compute.** The town speaks strength:
registers, sensors, lamps, bridges, doors, the Reader wall. The Compute district speaks binary
internally, with converter cells at its gates (strength to four bits, four bits to strength; both
are known compact circuits). An `ADD` then means: registers put strengths on the bus, the
converter at the Compute gate turns them into bits, the adder adds, the converter turns the sum
back into strength, the bus carries it to the destination register. That is a lot of visible
motion for one instruction, which is exactly the kind of thing this project is for. It also keeps
the doc's 4-bit register model intact and lets the Compute district look like a chip.

If you would rather keep one representation everywhere, binary is the safer long-term bet and the
cell library would need reworking now, before Phase 3 depends on it. Analog everywhere is the
most beautiful and the most limited (subtract-only arithmetic, `ADD` by counting pulses).

## Architecture sketch (v1, 4-bit, one bus)

Small enough to build in Phases 3 and 4, big enough to prove the idea.

- **Data bus:** one analog lane in a loop of bus streets from the Core through RAM and Compute
  and back. New cells: `bus_corner`, `bus_cross` (two lanes crossing, no contact), `bus_tap`
  (a gated branch: value flows when its select line is high; the subtract-mode comparator gate
  the register already uses).
- **Select bus:** a second lane carrying a register address 0-15. A `decoder_plaza` per RAM
  block turns it into one-hot select lines; each register's `bus_tap` opens only when selected.
  Reading register 3 means: Core puts 3 on the select lane, waits, reads the data lane.
- **Write:** Core puts the destination address on select and the value on data, pulses `clk`
  on the RAM block. Registers are transparent latches with a documented hold time; the Core's
  sequencing honours it.
- **Compute:** the Forge district from the doc, renamed. Contains converter gates and whatever
  operation cells the program needs. Its own clock divider. Compact library, no set-piece rule.
- **Storage:** a district of storage cells and card lecterns. Cards the city has run (and, in
  Phase 5, cards it wrote) are physical items you can find here.
- **IO:** actuators and sensors stay spread through the town on wire branches and Couriers,
  because that is what makes the city feel alive. They get bus taps only where a program reads
  or writes them.

Timing at the doc's clock (68 game ticks per beat): a single instruction touching two registers
and an adder is on the order of ten beats. Programs run at the pace of a slow heartbeat, which the
doc wants: everything visible at human speed. Per-district clock dividers keep Compute from
waiting on drawbridges.

## Demand: what a card asks the city to build

Resolving a card produces a hardware requirement. First cut:

| Card uses | City needs |
| --- | --- |
| registers R0..Rn | n+1 register cells in RAM, one decoder per 8, bus and select lanes reaching them |
| `ADD`, `SUB` | one adder (or subtractor) cell in Compute plus converter gates, if analog civic bus |
| `AND`, `OR`, `NOT` | the matching logic cell in Compute |
| `JZ` | a zero-detect (comparator) cell in Compute |
| `WAIT n` | nothing new: the Core counts beats |
| `OUT port`, `IN port` | a bus tap or Courier route to that actuator or sensor |
| `NEED cell` | exactly that cell (already in the doc) |
| a second card in the slot | a Storage district cell to keep the first one |

Each row becomes Goals. The planner grows the matching zone adjacent to the bus, in priority
order from the Core (doc 20.3), and the card goes live only when every need is built and
verified (doc 15.2). Unused hardware is never torn down; the city only grows. A later program that
needs less leaves idle districts behind, which is good: ruins with a story.

## What would be expansion, not core

- GPU/parallel district: many identical cells fed by one broadcast bus, for choreography over
  many lamps or doors at once. Needs no new op if `OUT` can target a bus of actuators.
- Networking between cities (Couriers over land, later "radio" towers).
- Robotics, manufacturing, power grids, other architectures. Each is a district type, a cell
  subset, and a demand row. The framework above does not need to change for them.

## Impact on the phase plan

- Phase 2: unchanged.
- Phase 3: unchanged in scope (Core, cards, L1, Reader) but designed to the honesty rule: the
  interpreter reads and writes physical registers over the bus from day one, and the word
  representation decision is made here.
- Phase 4: "Forge district" becomes "functional districts": RAM, Compute, Storage zoning, the
  demand table, bus taps and select lanes, converters, the first adder. This is the phase that
  turns the city into a computer.
- Phase 5: unchanged, plus GPU district as the first expansion if there is appetite.

## Open questions for Tabor

1. Analog civic bus with binary Compute, binary everywhere, or analog everywhere? (See table.)
   My recommendation is the first.
2. Is the honesty rule right? It means the Core's fetch/decode/branch stays software. The
   alternative is a program counter and instruction memory in redstone, which is a Phase 6+
   Forge project on its own.
3. How visible should data movement be? Buses can glow per strength (comparator ladder into
   lamps along the street) so you can watch a value travel. Costly in blocks, wonderful to look at.
4. District zoning: fixed compass layout (RAM always east of the Core, Compute south, Storage
   north) so every city is readable at a glance, or seed-random so every city surprises you?
   Readable is my vote; the randomness lives inside the districts.
5. Should unused hardware ever be reclaimed? My vote is never; the city only grows.
