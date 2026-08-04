package dev.heitorrosa.packconverter.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import dev.heitorrosa.packconverter.convert.PngTestUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.flag.FeatureFlagSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ConverterPackResources}: path remapping, model JSON
 * rewriting, metadata rewriting, listing with legacy-directory probing, and
 * lifecycle delegation. The delegate is an in-memory {@link FakePackResources}
 * keyed by Identifier path.
 */
class ConverterPackResourcesTest {

    private static final PackType CLIENT = PackType.CLIENT_RESOURCES;

    @BeforeAll
    static void setGameVersion() {
        // SharedConstants.getCurrentVersion() throws before the game bootstrap
        // calls setVersion(); mirror it with the built-in version so the
        // current pack format is resolvable in the bare test JVM.
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
    }

    private FakePackResources fake;
    private ConverterPackResources converter;

    @BeforeEach
    void setUp() {
        fake = new FakePackResources();
        converter = new ConverterPackResources(fake);
    }

    @Test
    void getResourceRemapsLegacyTextureToModernPath() throws IOException {
        fake.put("textures/block/oak_planks.png", "modern".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("textures/blocks/planks_oak.png"));

        assertNotNull(supplier);
        assertEquals("modern", read(supplier));
    }

    @Test
    void remappedVariantWinsOverOriginal() throws IOException {
        fake.put("textures/blocks/planks_oak.png", "legacy".getBytes(StandardCharsets.UTF_8));
        fake.put("textures/block/oak_planks.png", "modern".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("textures/blocks/planks_oak.png"));

        assertEquals("modern", read(supplier));
    }

    @Test
    void unmappedNameFallsBackToOriginal() throws IOException {
        // "custom_thing" is not in the rename map; the folder-normalized modern
        // path is absent, so the original legacy path must be returned.
        fake.put("textures/blocks/custom_thing.png", "original".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("textures/blocks/custom_thing.png"));

        assertNotNull(supplier);
        assertEquals("original", read(supplier));
    }

    @Test
    void missingResourceReturnsNull() {
        assertNull(converter.getResource(CLIENT, id("textures/block/does_not_exist.png")));
    }

    @Test
    void modelJsonIsRewritten() throws IOException {
        fake.put("models/block/old_model.json",
                "{\"textures\":{\"all\":\"block/planks_oak\"}}".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("models/block/old_model.json"));

        assertNotNull(supplier);
        assertTrue(read(supplier).contains("block/oak_planks"));
    }

    @Test
    void blockStateJsonModelKeysAreRewritten() throws IOException {
        String blockState = "{\"variants\":{\"normal\":{\"model\":\"block/planks_oak\"}}}";
        fake.put("blockstates/planks_oak.json", blockState.getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("blockstates/planks_oak.json"));

        assertNotNull(supplier);
        String out = read(supplier);
        assertTrue(out.contains("block/oak_planks"));
        assertFalse(out.contains("block/planks_oak"));
    }

    @Test
    void getMetadataSectionReportsCurrentFormatRange() throws IOException {
        // The delegate's own range (0..1) excludes the current version; the
        // assertion below only passes when the converter overrides it.
        PackFormat current = SharedConstants.getCurrentVersion().packVersion(CLIENT);
        assertFalse(fake.getMetadataSection(PackMetadataSection.forPackType(CLIENT))
                .supportedFormats().isValueInRange(current));

        PackMetadataSection section = converter
                .getMetadataSection(PackMetadataSection.forPackType(CLIENT));

        assertNotNull(section);
        assertEquals("Fake pack", section.description().getString());
        assertTrue(section.supportedFormats().isValueInRange(current));
    }

    @Test
    void nonPackMetadataSectionsDelegate() throws IOException {
        MetadataSectionType<String> other = new MetadataSectionType<>("other", Codec.STRING);
        assertNull(converter.getMetadataSection(other));
    }

    @Test
    void listResourcesRemapsEntries() throws IOException {
        fake.put("textures/blocks/planks_oak.png", "x".getBytes(StandardCharsets.UTF_8));

        List<Identifier> collected = collect("textures/blocks");

        assertTrue(collected.contains(id("textures/block/oak_planks.png")));
        assertFalse(collected.contains(id("textures/blocks/planks_oak.png")));
    }

    @Test
    void listResourcesProbesLegacyDirectories() throws IOException {
        // Delegate only holds legacy "textures/blocks" entries; requesting the
        // modern "textures/block" directory must surface them, remapped.
        fake.put("textures/blocks/planks_oak.png", "x".getBytes(StandardCharsets.UTF_8));

        List<Identifier> collected = collect("textures/block");

        assertTrue(collected.contains(id("textures/block/oak_planks.png")));
    }

    @Test
    void listResourcesRewritesModelJsonContent() throws IOException {
        fake.put("models/block/planks_oak.json",
                "{\"textures\":{\"all\":\"block/planks_oak\"}}".getBytes(StandardCharsets.UTF_8));

        Map<Identifier, String> collected = collectWithContent("models/block");

        Identifier remapped = id("models/block/oak_planks.json");
        assertTrue(collected.containsKey(remapped));
        assertTrue(collected.get(remapped).contains("block/oak_planks"));
        assertFalse(collected.get(remapped).contains("block/planks_oak"));
    }

    @Test
    void listResourcesRewritesBlockStateJsonContent() throws IOException {
        fake.put("blockstates/planks_oak.json",
                "{\"variants\":{\"normal\":{\"model\":\"block/planks_oak\"}}}".getBytes(StandardCharsets.UTF_8));

        Map<Identifier, String> collected = collectWithContent("blockstates");

        Identifier remapped = id("blockstates/oak_planks.json");
        assertTrue(collected.containsKey(remapped));
        assertTrue(collected.get(remapped).contains("block/oak_planks"));
        assertFalse(collected.get(remapped).contains("block/planks_oak"));
    }

    @Test
    void getResourceFindsMcmetaCompanionViaReverseProbe() throws IOException {
        // 26.2 asks for the modern companion path; the legacy pack only has the
        // old-named file. Reverse lookup must surface it.
        fake.put("textures/blocks/planks_oak.png.mcmeta", "anim".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("textures/block/oak_planks.png.mcmeta"));

        assertNotNull(supplier);
        assertEquals("anim", read(supplier));
    }

    @Test
    void getResourceFindsModernModelViaLegacyName() throws IOException {
        // Delegate has the model under its legacy file name; the game requests
        // the modern id. The reverse probe finds it and the content is rewritten.
        fake.put("models/block/planks_oak.json",
                "{\"textures\":{\"all\":\"block/planks_oak\"}}".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("models/block/oak_planks.json"));

        assertNotNull(supplier);
        assertTrue(read(supplier).contains("block/oak_planks"));
    }

    @Test
    void supplierWrapsOpenedResourcesAndCloseIsIdempotent() {
        FakeResourcesSupplier delegate = new FakeResourcesSupplier();
        ConverterResourcesSupplier supplier = new ConverterResourcesSupplier(delegate);

        PackResources primary = supplier.openPrimary(FakePackResources.FAKE_LOCATION);
        PackResources full = supplier.openFull(FakePackResources.FAKE_LOCATION,
                new Pack.Metadata(Component.literal("x"), PackCompatibility.COMPATIBLE,
                        FeatureFlagSet.of(), List.of()));

        assertTrue(primary instanceof ConverterPackResources);
        assertTrue(full instanceof ConverterPackResources);

        primary.close();
        primary.close();
        assertEquals(1, delegate.resources.closeCount);
        full.close();
        full.close();
        assertEquals(2, delegate.resources.closeCount);
    }

    @Test
    void getRootResourcePassesThrough() throws IOException {
        fake.put("pack.mcmeta", "{\"pack\":{}}".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getRootResource("pack.mcmeta");

        assertNotNull(supplier);
        assertEquals("{\"pack\":{}}", read(supplier));
    }

    @Test
    void getNamespacesDelegates() {
        fake.namespaces.add("minecraft");
        assertTrue(converter.getNamespaces(CLIENT).contains("minecraft"));
    }

    @Test
    void getResourceSlicesHotbarSpriteFromWidgetsAtlas() throws IOException {
        byte[] widgets = PngTestUtil.png(256, 256);
        fake.put("textures/gui/widgets.png", widgets);

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT,
                id("textures/gui/sprites/hud/hotbar.png"));

        assertNotNull(supplier);
        byte[] hotbar = readBytes(supplier);
        assertEquals(182, PngTestUtil.width(hotbar));
        assertEquals(22, PngTestUtil.height(hotbar));
        // Box starts at (0,0): slice pixel (7,5) == source pixel (7,5).
        assertEquals(PngTestUtil.rgb(widgets, 7, 5), PngTestUtil.rgb(hotbar, 7, 5));
    }

    @Test
    void getResourceReturnsNullWhenSliceSourceMissing() {
        // No widgets.png in the pack: the request must fall through to null,
        // so the game renders the vanilla default instead.
        assertNull(converter.getResource(CLIENT, id("textures/gui/sprites/hud/hotbar.png")));
    }

    @Test
    void getResourceServesArmorLayerFromLegacySource() throws IOException {
        byte[] layer = PngTestUtil.png(64, 32);
        fake.put("textures/models/armor/iron_layer_1.png", layer);

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT,
                id("textures/entity/equipment/humanoid/iron.png"));

        assertNotNull(supplier);
        assertArrayEquals(layer, readBytes(supplier)); // straight pixel copy, no re-encode
    }

    @Test
    void getResourceServesArmorLayerFromLegacyArmorFolderSpelling() throws IOException {
        byte[] layer = PngTestUtil.png(64, 32);
        fake.put("textures/armor/iron_layer_1.png", layer);

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT,
                id("textures/entity/equipment/humanoid/iron.png"));

        assertNotNull(supplier);
        assertArrayEquals(layer, readBytes(supplier));
    }

    @Test
    void getResourceServesMojangstudiosCopy() throws IOException {
        byte[] logo = PngTestUtil.png(256, 128);
        fake.put("textures/gui/title/mojang.png", logo);

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT,
                id("textures/gui/title/mojangstudios.png"));

        assertNotNull(supplier);
        assertArrayEquals(logo, readBytes(supplier));
    }

    @Test
    void getResourceServesMenuBackgroundCopies() throws IOException {
        byte[] background = PngTestUtil.png(256, 256);
        fake.put("textures/gui/options_background.png", background);

        IoSupplier<InputStream> menu = converter.getResource(CLIENT,
                id("textures/gui/menu_background.png"));
        IoSupplier<InputStream> inworld = converter.getResource(CLIENT,
                id("textures/gui/inworld_menu_background.png"));

        assertNotNull(menu);
        assertNotNull(inworld);
        assertArrayEquals(background, readBytes(menu));
        assertArrayEquals(background, readBytes(inworld));
    }

    @Test
    void getResourceServesCelestialSunCopy() throws IOException {
        byte[] sun = PngTestUtil.png(16, 16);
        fake.put("textures/environment/sun.png", sun);

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT,
                id("textures/environment/celestial/sun.png"));

        assertNotNull(supplier);
        assertArrayEquals(sun, readBytes(supplier));
    }

    @Test
    void getResourceSplicesExplosionCells() throws IOException {
        byte[] explosion = PngTestUtil.png(128, 128);
        fake.put("textures/entity/explosion.png", explosion);

        byte[] cell0 = readBytes(converter.getResource(CLIENT, id("textures/particle/explosion_0.png")));
        byte[] cell5 = readBytes(converter.getResource(CLIENT, id("textures/particle/explosion_5.png")));

        assertEquals(32, PngTestUtil.width(cell0));
        assertEquals(32, PngTestUtil.height(cell0));
        // explosion_0 = row0col0, explosion_5 = row1col1 in the 4x4 grid.
        assertEquals(PngTestUtil.rgb(explosion, 0, 0), PngTestUtil.rgb(cell0, 0, 0));
        assertEquals(PngTestUtil.rgb(explosion, 32, 32), PngTestUtil.rgb(cell5, 0, 0));
    }

    @Test
    void listResourcesSurfacesSlicedHotbarWhenOnlyAtlasExists() throws IOException {
        fake.put("textures/gui/widgets.png", PngTestUtil.png(256, 256));

        List<Identifier> collected = collect("textures/gui/sprites/hud");

        assertTrue(collected.contains(id("textures/gui/sprites/hud/hotbar.png")));
        assertTrue(collected.contains(id("textures/gui/sprites/hud/hotbar_selection.png")));
    }

    @Test
    void listResourcesSlicesHotbarContent() throws IOException {
        byte[] widgets = PngTestUtil.png(256, 256);
        fake.put("textures/gui/widgets.png", widgets);

        Map<Identifier, byte[]> collected = new HashMap<>();
        converter.listResources(CLIENT, "minecraft", "textures/gui/sprites/hud",
                (listed, supplier) -> {
                    try {
                        collected.put(listed, readBytes(supplier));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        byte[] hotbar = collected.get(id("textures/gui/sprites/hud/hotbar.png"));
        assertNotNull(hotbar);
        assertEquals(182, PngTestUtil.width(hotbar));
        assertEquals(PngTestUtil.rgb(widgets, 0, 0), PngTestUtil.rgb(hotbar, 0, 0));
    }

    @Test
    void listResourcesOmitsSliceTargetsWhenSourceMissing() {
        // Pack without widgets.png/icons.png: nothing under sprites/hud.
        assertTrue(collect("textures/gui/sprites/hud").isEmpty());
    }

    @Test
    void listResourcesSurfacesArmorTargetsWhenLayerExists() throws IOException {
        fake.put("textures/models/armor/iron_layer_1.png", PngTestUtil.png(64, 32));

        List<Identifier> collected = collect("textures/entity/equipment/humanoid");

        assertTrue(collected.contains(id("textures/entity/equipment/humanoid/iron.png")));
    }

    @Test
    void listResourcesSurfacesArmorTargetFromLegacyFolderSpelling() throws IOException {
        fake.put("textures/armor/iron_layer_1.png", PngTestUtil.png(64, 32));

        List<Identifier> collected = collect("textures/entity/equipment/humanoid");

        assertTrue(collected.contains(id("textures/entity/equipment/humanoid/iron.png")));
    }

    @Test
    void listResourcesSurfacesExpansionCopies() throws IOException {
        fake.put("textures/gui/title/mojang.png", PngTestUtil.png(256, 128));
        fake.put("textures/gui/options_background.png", PngTestUtil.png(256, 256));
        fake.put("textures/environment/sun.png", PngTestUtil.png(16, 16));

        assertTrue(collect("textures/gui/title").contains(id("textures/gui/title/mojangstudios.png")));
        List<Identifier> gui = collect("textures/gui");
        assertTrue(gui.contains(id("textures/gui/menu_background.png")));
        assertTrue(gui.contains(id("textures/gui/inworld_menu_background.png")));
        assertTrue(collect("textures/environment").contains(id("textures/environment/celestial/sun.png")));
    }

    @Test
    void listResourcesSurfacesExplosionCells() throws IOException {
        fake.put("textures/entity/explosion.png", PngTestUtil.png(128, 128));

        List<Identifier> collected = collect("textures/particle");

        assertTrue(collected.contains(id("textures/particle/explosion_0.png")));
        assertTrue(collected.contains(id("textures/particle/explosion_5.png")));
        assertTrue(collected.contains(id("textures/particle/explosion_15.png")));
    }

    @Test
    void listResourcesSurfacesMoonPhasesWhenMoonAtlasExists() throws IOException {
        fake.put("textures/environment/moon_phases.png", PngTestUtil.png(128, 64));

        List<Identifier> collected = collect("textures/environment/celestial/moon");

        assertTrue(collected.contains(id("textures/environment/celestial/moon/full_moon.png")));
        assertTrue(collected.contains(id("textures/environment/celestial/moon/waxing_gibbous.png")));
    }

    @Test
    void getResourceSlicesCompassFrameFromLegacyAtlas() throws IOException {
        byte[] compass = PngTestUtil.png(16, 512);
        fake.put("textures/items/compass.png", compass);

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT,
                id("textures/item/compass_07.png"));

        assertNotNull(supplier);
        byte[] frame = readBytes(supplier);
        assertEquals(16, PngTestUtil.width(frame));
        assertEquals(16, PngTestUtil.height(frame));
        // Frame 7 = row band 7 (y = 7*16) of the 16x512 atlas.
        assertEquals(PngTestUtil.rgb(compass, 0, 7 * 16), PngTestUtil.rgb(frame, 0, 0));
    }

    @Test
    void getResourceStillRemapsOrdinaryItemTextures() throws IOException {
        // compass_*/clock_* slicing must not shadow the item rename path:
        // golden_apple still resolves from the legacy apple_golden file.
        fake.put("textures/items/apple_golden.png", PngTestUtil.png(16, 16));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT,
                id("textures/item/golden_apple.png"));

        assertNotNull(supplier);
        byte[] apple = readBytes(supplier);
        assertEquals(16, PngTestUtil.width(apple));
        assertEquals(16, PngTestUtil.height(apple));
        assertEquals(PngTestUtil.rgb(PngTestUtil.png(16, 16), 0, 0), PngTestUtil.rgb(apple, 0, 0));
    }

    @Test
    void listResourcesSurfacesCompassFramesWhenAtlasExists() throws IOException {
        fake.put("textures/items/compass.png", PngTestUtil.png(16, 512));

        List<Identifier> collected = collect("textures/item");

        assertTrue(collected.contains(id("textures/item/compass_00.png")));
        assertTrue(collected.contains(id("textures/item/compass_31.png")));
    }

    @Test
    void listResourcesRootSurfacesSlicesAndExpansions() throws IOException {
        fake.put("textures/gui/widgets.png", PngTestUtil.png(256, 256));
        fake.put("textures/models/armor/iron_layer_1.png", PngTestUtil.png(64, 32));

        List<Identifier> collected = collect("textures");

        assertTrue(collected.contains(id("textures/gui/sprites/hud/hotbar.png")));
        assertTrue(collected.contains(id("textures/entity/equipment/humanoid/iron.png")));
    }

    @Test
    void closeDelegatesAndIsIdempotent() {
        converter.close();
        converter.close();
        assertEquals(1, fake.closeCount);
    }

    private List<Identifier> collect(String directory) {
        List<Identifier> collected = new ArrayList<>();
        converter.listResources(CLIENT, "minecraft", directory, (id, supplier) -> collected.add(id));
        return collected;
    }

    private Map<Identifier, String> collectWithContent(String directory) throws IOException {
        Map<Identifier, String> collected = new HashMap<>();
        converter.listResources(CLIENT, "minecraft", directory,
                (id, supplier) -> {
                    try {
                        collected.put(id, read(supplier));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        return collected;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    private static String read(IoSupplier<InputStream> supplier) throws IOException {
        try (InputStream in = supplier.get()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readBytes(IoSupplier<InputStream> supplier) throws IOException {
        try (InputStream in = supplier.get()) {
            return in.readAllBytes();
        }
    }

    /**
     * In-memory {@link PackResources} keyed by Identifier path (no namespace
     * prefix). {@link #location()} returns a minimal fake {@code
     * PackLocationInfo}; {@code getMetadataSection} serves a fixed pack
     * section so callers can read a description.
     */
    private static final class FakePackResources implements PackResources {

        static final PackLocationInfo FAKE_LOCATION = new PackLocationInfo(
                "fake", Component.literal("Fake pack"), PackSource.BUILT_IN, Optional.empty());
        private final Map<String, byte[]> resources = new HashMap<>();
        private final Set<String> namespaces = new HashSet<>();
        private int closeCount;

        void put(String path, byte[] bytes) {
            resources.put(path, bytes);
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            String key = String.join("/", path);
            byte[] bytes = resources.get(key);
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            byte[] bytes = resources.get(id.getPath());
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
            String prefix = directory + "/";
            for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    Identifier id = Identifier.fromNamespaceAndPath(namespace, entry.getKey());
                    byte[] bytes = entry.getValue();
                    output.accept(id, () -> new ByteArrayInputStream(bytes));
                }
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return namespaces;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMetadataSection(MetadataSectionType<T> type) {
            if (type == PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES)) {
                return (T) new PackMetadataSection(Component.literal("Fake pack"),
                        new InclusiveRange<>(PackFormat.of(0), PackFormat.of(1)));
            }
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return FAKE_LOCATION;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    /**
     * {@link Pack.ResourcesSupplier} whose opened resources share one fake
     * pack, recording how many times each open method was invoked.
     */
    private static final class FakeResourcesSupplier implements Pack.ResourcesSupplier {

        private final FakePackResources resources = new FakePackResources();

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return resources;
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return resources;
        }
    }
}
