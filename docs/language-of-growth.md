# A language of growth

Design note, September 2026. Status: **assessed; adopted in parts** (see the last section).
Source: an idea Tabor started and worked through with another LLM. That text may have drifted
from the design doc; this note keeps what fits, says why, and says what does not.

## The idea in one paragraph

Do not compile programs into machine code. Compile them into **intent**: goals such as "text
output, string in, display out". The colony asks what it already has, what it lacks, and grows
hardware to satisfy the goal. The colony's own language is a few dozen primitive behaviours
(STORE, MOVE, COPY, COMPARE, COUNT, LOOP, WAIT, JOIN, SPLIT, SEE, DO), every building carries a
tiny program of them like DNA, districts are defined by relationships rather than instructions,
the colony searches combinations of cells until one satisfies a goal and remembers the winners
as a growing genome, and the whole thing is graph-first: nodes are cells, edges are relationships,
text is only one rendering. Players hack the city by editing the graph.

## What we already have that this describes

Most of it is the design doc, seen from a different angle. That is a good sign for the original
idea rather than a reason to change course.

| In the pasted text | In Seed City today |
| --- | --- |
| "Compile to intent"; goals not instructions | Doc 15.2: Parse, **Resolve**, Place, Build, Verify, Run. Resolve turns a card into Goals; the planner builds to satisfy them. `city-as-computer.md` widens this into the demand table. |
| Each building carries a tiny program (DNA) | Each cell carries a sidecar: ports, a **truth model** (its behavioural contract), weights, cost. The verifier proves the cell lives up to its DNA before it counts. |
| STORE MOVE COPY COMPARE COUNT LOOP WAIT JOIN SPLIT SEE DO | SET ADD SUB AND OR NOT JMP JZ WAIT OUT IN NEED. STORE/MOVE/COPY are SET; COMPARE is SUB then JZ; LOOP is JMP; SEE is IN; DO is OUT. COUNT is hardware (a counter cell). JOIN and SPLIT are hardware too (junctions, bus taps). The vocabularies agree almost one to one, which validates keeping the op set at twelve. |
| Districts defined by relationships | Ports and the grammar: a district is a weight column plus what its cells' ports let them touch. Phase 4 makes districts functional zones. |
| Graph-first | The city is a graph already: slots are nodes, port matings are edges, and liveness is a fixpoint over it. It was just not visible. `/seedcity graph` now prints it. |
| Players hack the graph | Doc 6.1: hand a Builder a blueprint, or author a new cell on the breadboard. Editing the graph is placing and teaching cells. |
| The colony writes its own programs | Doc L3, Phase 5: mutate a card, run it, keep it if it verifies. |

## What is genuinely new, and what to do with it

### 1. Behaviour as a declared spec, not Java

Today a cell's truth model is the name of a Java class (`register`, `passthrough`). The pasted
text's instinct that every cell should *carry* its behaviour suggests a small declarative spec
instead, evaluated by one generic verifier:

```
"truth": "out = in"                     passthrough
"truth": "out = 15 - in"                inverter
"truth": "out = hold(in, clk)"          register (transparent while clk, holds otherwise)
"truth": "out{k} = (in == k)"           decoder
"truth": "world(in)"                    actuator: the world changes with in
"truth": "toggles(out)"                 clock
```

Why it matters: Phase 4 lets players teach the city new cells. A player cannot write a Java
class; they can write `out = min(a, b)`. The same spec drives stimulus generation, judging, and
the Reader wall's description of what a cell does. **Adopt for Phase 4**, migrating the existing
models onto it as the first test of the spec language. Until then the named models stay.

### 2. The graph as a first-class, visible object

Cheap and immediately useful. `/seedcity graph` dumps nodes, edges, which edges carry the clock,
and which outputs dead-end. Later the same graph feeds the Reader wall, a map item, and any
in-game "view the machine" screen. The plan (slots) remains the model; the graph is the view.
**Adopted now.**

### 3. Intent above the card

A higher-level intent language ("when night falls, raise every bridge") is attractive and is the
doc's single biggest named risk (15.1: "the temptation will be to grow it"). Position: intent
compiles **down into cards**; it never bypasses them and never touches hardware directly, so the
honesty rule holds and the twelve ops stay the machine language. L1 behaviour cards are already
the first, human-facing intent layer. Anything higher is Phase 5 or later, and only if L1 feels
too low-level in play.

### 4. Invention: composing cells until a goal is met

The strongest new idea, and the most expensive. Bounded version that fits the architecture:

- A **compound cell** is a rectangle of slots filled with existing verified cells, wrapped in a
  synthesised sidecar whose ports are the outer faces. Verification proves the compound, exactly
  as for a hand-authored cell.
- The **Foundry** (a later Phase, inside Compute) searches small compositions (two to four cells)
  against a wanted truth spec, using the in-world verifier as the fitness function. Slow is fine:
  the city has time, and "the city is trying something" is good theatre.
- Winners join the library as compound cells: the **genome**. Players carry them between cities
  as blueprint items (doc 6.1), which is how discoveries spread.

Architectural hook to take now: the slot grid stays 7x7, but cells may later occupy an N by M
rectangle of slots. Nothing built so far prevents that; the planner and BuildTask just need to
learn multi-slot placements when the time comes. **Adopt as direction; build after Phase 5.**

### 5. Register-to-register moves

MOVE and COPY point at a gap in the card format: `SET R0 5` exists, `SET R1 R0` does not. Allow
a register as the source of `SET` in Phase 3. No new op.

## What not to take

- **Per-building behaviour scripts** ("a mining camp: FIND DIG STORE"). Behaviour lives in the
  five mobs; cells are passive hardware that declares what it *is*. A mining camp is a storage
  cell plus Collectors. Keeping this line is what keeps the codebase small (doc 19, 26).
- **A new primitive vocabulary.** The twelve ops already cover it. Adding a parallel set of
  behaviour words would give the project two languages to maintain.
- **Unbounded evolution.** Open-ended search over arbitrary block arrangements is not verifiable
  in reasonable time and would produce circuits nobody can read. Search over compositions of
  verified cells keeps every result legible and provably working.

## Summary of decisions

| Item | Status |
| --- | --- |
| `/seedcity graph` city graph dump | done |
| Declarative truth specs replacing named Java models | Phase 4 |
| `SET Rd Rs` register copy | Phase 3 |
| Intent language above cards | Phase 5+, compiles to cards only |
| Compound cells, the Foundry, the genome | direction; after Phase 5 |
| Per-building scripts, new vocabulary, unbounded evolution | not adopted |
