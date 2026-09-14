# Builder

First character in the NPC fork: compact stone-and-weathered-copper construction
worker with cyan eyes, a leather belt and pouches, a belt hammer, material-load
block and a blue schematic panel. Based on the user's approved five-character
reference, specifically its leftmost character.

Open `builder-preview.html` in a browser to rotate and inspect the actual model.
It runs offline. `builder.bbmodel` is the editable Blockbench mesh and embeds its
texture. The preview approximates lighting; the Fabric client-test captures are
actual Minecraft screenshots.

Use Walk to preview the optional walking animation. The mod still uses its existing
flight navigation. The blueprint now meets the left hand with a visible grip.
Send the HTML file directly to a friend, or use Download viewer to save a copy.
It includes its model, textures and renderer; no server or Minecraft is required.
Save image downloads the current camera view as a PNG.

Revision 3 separates overlapping armor faces to fix depth flicker, and holds the
blueprint in front with the schematic facing the Builder. The geometry generator
checks for coplanar overlapping outward faces in each rigid part before exporting.

See `docs/npc-builder-merge-notes.md` at the repository root for required Java and
asset files, the restored upstream BuildTask source, tests, and limitations.

Regenerate from the repository root with `python tools/build_builder_assets.py`
(Pillow required). The Python source is authoritative; the model JSON, PNGs, Java
mesh, HTML and Blockbench file are generated outputs. Animations are in
`src/client/java/net/tabor/seedcity/client/BuilderModel.java` and mirrored in the
preview's `preview.js`.
