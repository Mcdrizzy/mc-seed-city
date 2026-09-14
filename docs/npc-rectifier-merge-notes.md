# Rectifier: integration notes

The former Warden now displays as **Rectifier** and uses a custom stone-and-copper
repair guardian model inspired by the second character in the supplied lineup.
Builder's approved assets remain unchanged.

## Take together

- `src/client/java/net/tabor/seedcity/client/RectifierMesh.java`, `RectifierModel.java`,
  `RectifierRenderState.java`, `RectifierRenderer.java` and updated `SeedCityClient.java`.
- `src/main/resources/assets/seedcity/textures/entity/rectifier.png` and
  `rectifier_glow.png`, plus `assets/seedcity/lang/en_us.json` for the display name.
- `src/main/java/net/tabor/seedcity/entity/WardenEntity.java`: one synchronized
  presentation flag exposes the existing REPAIR phase to clients. It does not
  replace the existing patrol, damage detection, BuildTask repair or respawn logic.
- `src/gametest/java/net/tabor/seedcity/gametest/RectifierClientTest.java` and the
  client test entrypoint in `src/gametest/resources/fabric.mod.json`.

The registry ID **`seedcity:warden`**, Java entity class, configuration keys, district
data remain unchanged for save and code compatibility. The revised hitbox is
1.4 by 2.95 blocks to accommodate the taller model.
Use `/summon seedcity:warden ~ ~ ~` to spawn the Rectifier. Minecraft's own Warden
is untouched. The unused old placeholder WardenRenderer remains available in source;
the client entrypoint now registers RectifierRenderer instead.

**This fork still contains more than models.** Upstream was missing BuildTask.java;
the separate replacement and `.gitignore` fix are commit `0406d80`. Read
`npc-builder-merge-notes.md` and compare Tabor's original local BuildTask implementation
before merging that prerequisite. Use matching client/server builds for the new flag.

## Model and animation

58 cuboids in nine parts, approximately 2.94 blocks tall. Revision 4 follows the
close-up reference silhouette: long armored legs, compact inset chest/core with
thick flanking plates, exposed copper shoulder blocks, larger gauntlets and a
narrow tabard. Earlier broad slab-like chest and shoulder lids have been removed.
The user's requested club hammer replaces the wrench visible in the reference.
Its striking faces run front-to-back in the repair arm's swing plane; its handle
leans downward/forward from the closed hand, without an independent sideways roll.
The articulated forward-held lantern and player-height marker remain.

The palette uses stone armor, weathered
copper shoulders and greaves, a red-brown tabard, an amber visor and chest core,
a hanging lantern and a club hammer. The base/emission atlases are 1024 square pixels
with a logical 256-square UV layout. Armor offsets avoid coplanar surface fighting.

The head independently follows Minecraft's look state. The existing repair code
already looks toward the damaged block. Repair mode moves the hammer arm; the
lantern counter-rotates with both arm joints and gently swings. Emission remains visible at
night but does not cast light, need shaders, or brighten dynamically during repairs.
Tool motion is continuous during REPAIR, not timed to individual block placements.

Existing flight is preserved. The optional grounded walk cycle is implemented in
the model, but no ground navigation was added. Preview Look around demonstrates the
head joint; it does not add random-looking or nearby-player attention AI to the mob.

## Editing, preview and checks

`tools/build_rectifier_assets.py` is the authoring source. Run it with Python and
Pillow to regenerate Java, PNGs, geometry JSON, Blockbench mesh and offline HTML.
It checks for coplanar overlapping outward faces within each rigid part. The
Blockbench file embeds the texture; animation remains in RectifierModel.java and
is mirrored by `art/rectifier/preview.js`. The preview uses the existing vendored
Three.js bundle, embeds every dependency, and runs offline without Minecraft.

`gradlew.bat build runClientGameTest` runs the existing server tests and both NPC
client checks. The Rectifier test checks head rotation, repair-tool pose and grounded
walking/rest, then captures actual day/night Minecraft screenshots. Screenshots are
stored under `art/rectifier/screenshots/` after visual review.

The packaged JAR is the whole Seed City mod with Builder and Rectifier, replacing
the original JAR. It is not a standalone resource pack. Versions remain Minecraft
26.2, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2 and Java 25.
`build runClientGameTest` passed for revision 4 on rerun. The first run timed out in the existing city-growth test; the unchanged rerun passed all 25 server tests and both NPC client checks. Day/night captures were inspected.
