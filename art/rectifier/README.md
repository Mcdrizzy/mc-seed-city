# Rectifier

Second character in the NPC fork: an armored district repair guardian, formerly
called Warden. Stone, weathered copper, amber visor/core, lantern, wrench and tabard.

Open `rectifier-preview.html` in a browser. It includes Rest, Patrol, Repair and
optional Walk poses, independent head movement, night lighting and image export.
Download viewer saves a single HTML file that can be shared and opened offline.

`rectifier.bbmodel` is the editable Blockbench mesh with embedded base texture.
`tools/build_rectifier_assets.py` is authoritative for geometry/UVs; regenerate it
with Python and Pillow. Animations live in RectifierModel.java and preview.js.

See `docs/npc-rectifier-merge-notes.md` for required code changes and compatibility.
The in-game registry ID remains `seedcity:warden`; its display name is Rectifier.
