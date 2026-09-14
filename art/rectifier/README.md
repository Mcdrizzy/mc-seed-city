# Rectifier

Second character in the NPC fork: an armored district repair guardian, formerly
called Warden. Stone, weathered copper, amber visor/core, lantern, club hammer and tabard.

Open `rectifier-preview.html` in a browser. It includes Rest, Patrol, Repair and
optional Walk poses, independent head movement, night lighting and image export.
Download viewer saves a single HTML file that can be shared and opened offline.

`rectifier.bbmodel` is the editable Blockbench mesh with embedded base texture.
`tools/build_rectifier_assets.py` is authoritative for geometry/UVs; regenerate it
with Python and Pillow. Animations live in RectifierModel.java and preview.js.

See `docs/npc-rectifier-merge-notes.md` for required code changes and compatibility.
The in-game registry ID remains `seedcity:warden`; its display name is Rectifier.

Revision 2: taller legs, wider/deeper chest, a smaller helmet proportion, rebuilt
wrench and an articulated elbow holding the lantern forward. Model height is
about 2.81 blocks; the preview includes a player-height comparison marker.

Revision 3: thicker legs and boots, taller helmet, longer chest and a club hammer
in place of the wrench. Current model: 62 cuboids, nine parts, about 2.94 blocks tall.
