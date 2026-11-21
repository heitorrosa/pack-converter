# Pack Converter

On-the-fly resource pack converter for Minecraft **26.3** (Fabric, client-side).

Old resource packs (1.7/1.8 era) use the pre-1.13 texture layout and pack
formats. This mod intercepts the resource pipeline and serves old packs
converted in memory: path remapping (`textures/blocks/planks_oak.png` ->
`textures/block/oak_planks.png`, ~650 renames), synthesized `pack.mcmeta`,
and model/blockstate texture rewriting — no files touched on disk.

Compile with:

```
.\gradlew.bat build
.\gradlew.bat test
```

Credits: agentdid127/ResourcePackConverter (MIT)

#

Licensed under GPL-3.0.
